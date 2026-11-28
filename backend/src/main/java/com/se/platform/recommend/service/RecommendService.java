package com.se.platform.recommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.assistant.entity.RemedyRound;
import com.se.platform.assistant.mapper.RemedyRoundMapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.course.service.Grader;
import com.se.platform.intervention.entity.ClassPush;
import com.se.platform.intervention.mapper.ClassPushMapper;
import com.se.platform.intervention.service.ClassAnalysisService;
import com.se.platform.recommend.dto.RecommendDtos.RecommendationList;
import com.se.platform.recommend.dto.RecommendDtos.SavedSet;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 学习推荐 = 小助手的延伸。设计：补弱点、拓展都由小助手负责，学生要先经过小助手——
 * 在小助手里点“做补练题”“来点更难的”，大模型出完题，学生点“去查看”到学习推荐页做；
 * 老师的推送在小助手里点“接收”后存到这里。页面分三类：
 * <ul>
 *   <li>系统补充（remedy_round.kind = remedy）：小助手按这个学生的错题出的补练，一律保存——知识点变色（补回来）要有记录；</li>
 *   <li>拓展（kind = extend）：还没做的显示出来；做完后学生自己选保存不保存，不保存的不再显示；</li>
 *   <li>老师推送（class_push）：学生接收了的。</li>
 * </ul>
 * 做题仍然调小助手原来的接口（补练、拓展、老师推送各自的提交接口），判分和“补回来”的规则只有一套。
 * 做过的题带上我的答案、对错、正确答案、解析和“你可能是这样想的”（用存下来的作答重新判一遍）。
 * 练习里的题不会出现在这里（不绕过解锁顺序，也不占用“第一次作答”）。
 */
@Service
public class RecommendService {

    private final RemedyRoundMapper roundMapper;
    private final ClassPushMapper pushMapper;
    private final AnswerRecordMapper answerMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseService exerciseService;
    private final KnowledgePointMapper kpMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public RecommendService(RemedyRoundMapper roundMapper, ClassPushMapper pushMapper, AnswerRecordMapper answerMapper,
                            ExerciseMapper exerciseMapper, ExerciseService exerciseService,
                            KnowledgePointMapper kpMapper, ChapterMapper chapterMapper, CourseMapper courseMapper,
                            UserMapper userMapper) {
        this.roundMapper = roundMapper;
        this.pushMapper = pushMapper;
        this.answerMapper = answerMapper;
        this.exerciseMapper = exerciseMapper;
        this.exerciseService = exerciseService;
        this.kpMapper = kpMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    /** 我的学习推荐：小助手出过的补练、拓展，和做过的老师推送，按时间从新到旧 */
    public RecommendationList student(long studentId) {
        List<SavedSet> sets = new ArrayList<>();
        for (RemedyRound r : roundMapper.selectList(new LambdaQueryWrapper<RemedyRound>()
                .eq(RemedyRound::getStudentId, studentId))) {
            List<Long> ids = ClassAnalysisService.split(r.getExerciseIds());
            boolean done = !RemedyRound.OPEN.equals(r.getStatus());
            String kind = "extend".equals(r.getKind()) ? "extend" : "remedy";
            boolean saved = "remedy".equals(kind) || Boolean.TRUE.equals(r.getSaved());
            if ("extend".equals(kind) && done && !saved) {
                continue; // 做完了、学生没选保存的拓展题不显示
            }
            sets.add(set(r.getId(), kind, r.getKnowledgePointId(), "remedy".equals(kind) ? nz(r.getRoundNo()) : 0,
                    r.getAssessType(), done, saved, done ? r.getScore() : null, null, r.getCreatedAt(), null, studentId, ids));
        }
        for (ClassPush p : pushMapper.selectList(new LambdaQueryWrapper<ClassPush>()
                .eq(ClassPush::getStatus, ClassPush.PUSHED))) {
            if (!ClassAnalysisService.split(p.getStudentIds()).contains(studentId)) {
                continue;
            }
            if (!ClassAnalysisService.split(p.getAcceptedIds()).contains(studentId)) {
                continue; // 还没在小助手里接收：只在小助手里显示
            }
            List<Long> ids = ClassAnalysisService.split(p.getExerciseIds());
            Map<Long, AnswerRecord> mine = firstAnswers(studentId, ids);
            boolean done = !ids.isEmpty() && !mine.isEmpty();
            Double score = done ? Math.round(mine.values().stream().mapToDouble(a -> a.getScore() == null ? 0 : a.getScore())
                    .average().orElse(0) * 100) / 100.0 : null;
            sets.add(set(p.getId(), "teacher", p.getKnowledgePointId(), 0, p.getAssessType() == null ? "concept" : p.getAssessType(),
                    done, true, score, p.getExplanation(), p.getPushedAt(), p.getDueAt(), studentId, ids));
        }
        sets.sort(Comparator.comparing(SavedSet::at, Comparator.nullsLast(Comparator.reverseOrder())));
        User u = userMapper.selectById(studentId);
        return new RecommendationList(u == null ? "" : u.getName(),
                (int) sets.stream().filter(s -> "remedy".equals(s.kind())).count(),
                (int) sets.stream().filter(s -> "extend".equals(s.kind())).count(),
                (int) sets.stream().filter(s -> "teacher".equals(s.kind())).count(), sets);
    }

    private SavedSet set(Long id, String kind, Long kpId, int round, String assessType, boolean done, boolean saved,
                         Double score, String explanation, LocalDateTime at, LocalDateTime dueAt, long studentId,
                         List<Long> ids) {
        String assess = assessType == null ? null : ClassAnalysisService.ASSESS.getOrDefault(assessType, assessType);
        KnowledgePoint kp = kpId == null ? null : kpMapper.selectById(kpId);
        Chapter ch = kp == null || kp.getChapterId() == null ? null : chapterMapper.selectById(kp.getChapterId());
        Course c = kp == null || kp.getCourseId() == null ? null : courseMapper.selectById(kp.getCourseId());
        Map<Long, AnswerRecord> mine = firstAnswers(studentId, ids);
        List<QuestionResult> qs = new ArrayList<>();
        if (!ids.isEmpty()) {
            Map<Long, Exercise> byId = new LinkedHashMap<>();
            exerciseMapper.selectByIds(ids).forEach(e -> byId.put(e.getId(), e));
            for (Long eid : ids) {
                Exercise e = byId.get(eid);
                if (e != null) {
                    qs.add(result(exerciseService.toView(e), done ? mine.get(eid) : null));
                }
            }
        }
        return new SavedSet(id, kind, kpId, kp == null ? "" : kp.getName(), c == null ? null : c.getId(),
                c == null ? "" : c.getName(), ch == null ? null : ch.getId(), ch == null ? "" : ch.getTitle(), round,
                assessType, assess, done ? "done" : "open", saved, score, explanation, at, dueAt, qs);
    }

    /** 拓展做完后学生选保存不保存；只能改自己的、已经做完的拓展 */
    public void saveExtend(long studentId, Long roundId, boolean saved) {
        RemedyRound r = roundMapper.selectById(roundId);
        if (r == null || r.getStudentId() != studentId || !"extend".equals(r.getKind())) {
            throw BusinessException.notFound("拓展题");
        }
        r.setSaved(saved);
        roundMapper.updateById(r);
    }

    /**
     * 一道题的回看：做过的用存下来的答案重新判（客观题规则和做题时一样；简答用存下来的分数），
     * 没做的只给题目，不给答案。
     */
    static QuestionResult result(ExerciseView v, AnswerRecord a) {
        if (a == null) {
            return new QuestionResult(v.id(), v.type(), v.stem(), v.options(), null, null, false, null, null, 0, 0, 0,
                    "open", v.reasons(), null, null, List.of(), null, List.of());
        }
        if (Grader.SHORT.equals(v.type())) {
            return new QuestionResult(v.id(), v.type(), v.stem(), v.options(), a.getAnswer(), null,
                    Boolean.TRUE.equals(a.getIsCorrect()), v.analysis(), null, a.getScore() == null ? 0 : a.getScore(),
                    0, 1, a.getStatus(), v.reasons(), null, null, List.of(), v.reference(), List.of());
        }
        return Grader.grade(v, a.getAnswer());
    }

    /** 每道题这个学生的第一次作答 */
    private Map<Long, AnswerRecord> firstAnswers(long studentId, List<Long> ids) {
        Map<Long, AnswerRecord> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId).in(AnswerRecord::getExerciseId, ids)
                        .orderByAsc(AnswerRecord::getSubmittedAt).orderByAsc(AnswerRecord::getId))
                .forEach(a -> out.putIfAbsent(a.getExerciseId(), a));
        return out;
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
