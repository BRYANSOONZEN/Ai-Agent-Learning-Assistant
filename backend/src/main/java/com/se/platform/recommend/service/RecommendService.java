package com.se.platform.recommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.dto.LearnDtos.QuizResult;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.course.service.LearnService;
import com.se.platform.diagnosis.dto.DiagnosisDtos.CourseDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.MasteryChange;
import com.se.platform.diagnosis.dto.DiagnosisDtos.PointDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.StudentDiagnosis;
import com.se.platform.diagnosis.service.DiagnosisService;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.recommend.dto.RecommendDtos.PointRecommendation;
import com.se.platform.recommend.dto.RecommendDtos.RecExercise;
import com.se.platform.recommend.dto.RecommendDtos.RecommendationList;
import com.se.platform.recommend.dto.RecommendDtos.ReviewLink;
import com.se.platform.recommend.entity.Recommendation;
import com.se.platform.recommend.mapper.RecommendationMapper;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.service.RecordService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习推荐（docs/design.md 5.2）：取掌握度 < 0.6 的知识点，从低到高；
 * 每个知识点挑题，优先 上次做错的 > 没做过的 > 以前错过、后来做对的，同一档里难度接近知识点的在前；
 * 每个知识点最多 3 道，一共最多 5 道，每道写明理由。
 * 推荐过的题记在 recommendation 表：做对了是 done，点"换一道"是 dismissed，这两种以后都不再推。
 * 在推荐页做题和交卷一样记进学习记录、重算掌握度，所以做完刷新，推荐会跟着变。
 */
@Service
public class RecommendService {

    static final int MAX_TOTAL = 5;
    static final int MAX_PER_POINT = 3;

    private final DiagnosisService diagnosisService;
    private final MasteryService masteryService;
    private final RecordService recordService;
    private final ExerciseService exerciseService;
    private final RecommendationMapper recommendationMapper;
    private final AnswerRecordMapper answerMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final ExerciseMapper exerciseMapper;
    private final KnowledgePointMapper kpMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;

    public RecommendService(DiagnosisService diagnosisService, MasteryService masteryService,
                            RecordService recordService, ExerciseService exerciseService,
                            RecommendationMapper recommendationMapper, AnswerRecordMapper answerMapper,
                            ExerciseKnowledgePointMapper linkMapper, ExerciseMapper exerciseMapper,
                            KnowledgePointMapper kpMapper, ChapterMapper chapterMapper, ChapterItemMapper itemMapper) {
        this.diagnosisService = diagnosisService;
        this.masteryService = masteryService;
        this.recordService = recordService;
        this.exerciseService = exerciseService;
        this.recommendationMapper = recommendationMapper;
        this.answerMapper = answerMapper;
        this.linkMapper = linkMapper;
        this.exerciseMapper = exerciseMapper;
        this.kpMapper = kpMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
    }

    // ------------------------------------------------------------------ 推荐列表

    /** 打开推荐页时按最新的掌握度重新挑题，挑出来的写进 recommendation 表 */
    @Transactional
    public RecommendationList student(long studentId) {
        StudentDiagnosis diagnosis = diagnosisService.student(studentId);
        Map<Long, String> courseNames = diagnosis.courses().stream()
                .collect(Collectors.toMap(CourseDiagnosis::courseId, CourseDiagnosis::courseName));
        Map<Long, Recommendation> mine = recommendationMapper.selectList(new LambdaQueryWrapper<Recommendation>()
                        .eq(Recommendation::getStudentId, studentId)
                        .eq(Recommendation::getTargetType, Recommendation.EXERCISE))
                .stream().collect(Collectors.toMap(Recommendation::getTargetId, r -> r));
        Map<Long, List<Boolean>> history = history(studentId);

        List<PointRecommendation> points = new ArrayList<>();
        int left = MAX_TOTAL;
        for (PointDiagnosis p : diagnosis.weakPoints()) { // 已经按掌握度从低到高
            KnowledgePoint kp = kpMapper.selectById(p.knowledgePointId());
            List<RecExercise> picked = new ArrayList<>();
            for (Candidate c : candidates(kp, mine, history)) {
                if (picked.size() == MAX_PER_POINT || left == 0) {
                    break;
                }
                Recommendation r = save(studentId, kp.getId(), c, mine.get(c.exercise.getId()));
                ExerciseView v = exerciseService.toView(c.exercise);
                picked.add(new RecExercise(r.getId(), v.id(), v.stem(), v.options(), v.difficulty(), c.reason));
                left--;
            }
            points.add(new PointRecommendation(p.knowledgePointId(), p.name(), p.courseId(),
                    courseNames.get(p.courseId()), p.chapterId(), p.chapterTitle(), p.mastery(), p.level(),
                    p.attemptCount(), p.correctCount(), p.misconception(), kp.getContent(),
                    review(p.chapterId()), picked));
        }
        int done = (int) mine.values().stream().filter(r -> Recommendation.DONE.equals(r.getStatus())).count();
        return new RecommendationList(diagnosis.studentName(), diagnosis.weakPoints().size(), done, points);
    }

    /** 一道候选题；rank 小的先推 */
    private record Candidate(Exercise exercise, int rank, String reason) {
    }

    /** 这个知识点下学生能看到的题（章节已发布），去掉做对过的推荐和换掉的，按优先级排好 */
    private List<Candidate> candidates(KnowledgePoint kp, Map<Long, Recommendation> mine,
                                       Map<Long, List<Boolean>> history) {
        List<Long> ids = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getKnowledgePointId, kp.getId()))
                .stream().map(ExerciseKnowledgePoint::getExerciseId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Exercise> exercises = exerciseMapper.selectByIds(ids);
        Set<Long> published = chapterMapper.selectByIds(exercises.stream().map(Exercise::getChapterId).toList())
                .stream().filter(ch -> "published".equals(ch.getStatus())).map(Chapter::getId)
                .collect(Collectors.toSet());
        int kpDifficulty = kp.getDifficulty() == null ? 2 : kp.getDifficulty();

        List<Candidate> list = new ArrayList<>();
        for (Exercise e : exercises) {
            Recommendation r = mine.get(e.getId());
            if (!published.contains(e.getChapterId())
                    || r != null && !Recommendation.NEW.equals(r.getStatus())) {
                continue;
            }
            List<Boolean> h = history.getOrDefault(e.getId(), List.of());
            if (h.isEmpty()) {
                list.add(new Candidate(e, 1, "还没做过"));
            } else if (!h.get(h.size() - 1)) {
                list.add(new Candidate(e, 0, "上次做错了"));
            } else if (h.contains(false)) {
                list.add(new Candidate(e, 2, "以前做错过，再巩固一次"));
            }
            // 一直都做对的题不推
        }
        list.sort(Comparator.comparingInt(Candidate::rank)
                .thenComparingInt(c -> Math.abs((c.exercise.getDifficulty() == null ? 2 : c.exercise.getDifficulty())
                        - kpDifficulty))
                .thenComparingInt(c -> c.exercise.getSortOrder() == null ? 0 : c.exercise.getSortOrder())
                .thenComparingLong(c -> c.exercise.getId()));
        return list;
    }

    private Recommendation save(long studentId, Long kpId, Candidate c, Recommendation existing) {
        if (existing != null) {
            if (!c.reason.equals(existing.getReason())) {
                existing.setReason(c.reason);
                recommendationMapper.updateById(existing);
            }
            return existing;
        }
        Recommendation r = new Recommendation();
        r.setStudentId(studentId);
        r.setTargetType(Recommendation.EXERCISE);
        r.setTargetId(c.exercise.getId());
        r.setKnowledgePointId(kpId);
        r.setReason(c.reason);
        r.setStatus(Recommendation.NEW);
        recommendationMapper.insert(r);
        return r;
    }

    /** 每道题的作答对错，从早到晚 */
    private Map<Long, List<Boolean>> history(long studentId) {
        Map<Long, List<Boolean>> map = new HashMap<>();
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId)
                        .orderByAsc(AnswerRecord::getSubmittedAt)
                        .orderByAsc(AnswerRecord::getId))
                .forEach(a -> map.computeIfAbsent(a.getExerciseId(), k -> new ArrayList<>())
                        .add(Boolean.TRUE.equals(a.getIsCorrect())));
        return map;
    }

    /** 本章可以回看的视频和文档 */
    private List<ReviewLink> review(Long chapterId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                        .eq(ChapterItem::getChapterId, chapterId)
                        .in(ChapterItem::getType, "video", "document")
                        .orderByAsc(ChapterItem::getSortOrder)
                        .orderByAsc(ChapterItem::getId))
                .stream().map(i -> new ReviewLink(i.getId(), i.getType(), i.getTitle())).toList();
    }

    // ------------------------------------------------------------------ 做题、反馈

    /** 在推荐页做题：只能做推荐给自己、还没做对的题；判分、记进学习记录、重算掌握度 */
    @Transactional
    public QuizResult practice(long studentId, Map<Long, String> answers) {
        if (answers == null || answers.isEmpty()) {
            throw BusinessException.badRequest("没有作答");
        }
        Map<Long, Recommendation> recs = recommendationMapper.selectList(new LambdaQueryWrapper<Recommendation>()
                        .eq(Recommendation::getStudentId, studentId)
                        .eq(Recommendation::getTargetType, Recommendation.EXERCISE)
                        .in(Recommendation::getTargetId, answers.keySet()))
                .stream().collect(Collectors.toMap(Recommendation::getTargetId, r -> r));
        // 按课程分开存，同一次提交时间相同，学习记录里算一次「推荐练习」
        Map<Long, List<QuestionResult>> byCourse = new LinkedHashMap<>();
        List<QuestionResult> results = new ArrayList<>();
        for (Map.Entry<Long, String> a : answers.entrySet()) {
            Recommendation r = recs.get(a.getKey());
            Exercise e = exerciseMapper.selectById(a.getKey());
            if (r == null || e == null || !Recommendation.NEW.equals(r.getStatus())) {
                throw BusinessException.badRequest("这道题不在你的推荐里");
            }
            QuestionResult result = LearnService.grade(exerciseService.toView(e), a.getValue());
            results.add(result);
            byCourse.computeIfAbsent(e.getCourseId(), k -> new ArrayList<>()).add(result);
            if (result.correct()) {
                r.setStatus(Recommendation.DONE);
            } else {
                r.setReason("上次做错了");
            }
            recommendationMapper.updateById(r);
        }
        byCourse.forEach((courseId, list) -> recordService.saveAnswers(courseId, null, list));
        List<MasteryChange> changes = masteryService.update(studentId, answers.keySet());
        int correct = (int) results.stream().filter(QuestionResult::correct).count();
        return new QuizResult(results.size(), correct, results, changes);
    }

    /** 学生反馈：done 已完成 / dismissed 换一道 */
    public void feedback(long studentId, Long id, String status) {
        if (!Recommendation.DONE.equals(status) && !Recommendation.DISMISSED.equals(status)) {
            throw BusinessException.badRequest("status 只能是 done 或 dismissed");
        }
        Recommendation r = recommendationMapper.selectById(id);
        if (r == null || r.getStudentId() != studentId) {
            throw BusinessException.notFound("推荐");
        }
        r.setStatus(status);
        recommendationMapper.updateById(r);
    }
}
