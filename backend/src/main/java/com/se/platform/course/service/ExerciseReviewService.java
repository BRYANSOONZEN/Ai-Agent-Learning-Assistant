package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ExerciseToolDtos.CoverageRow;
import com.se.platform.course.dto.ExerciseToolDtos.ShortAnswerView;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 老师端：覆盖检查（每个知识点在每种考查类型、题型下有几道题）；简答作答的查看和改判。
 * 改判后可信度 r = 1，重新算这个学生的掌握度。
 */
@Service
public class ExerciseReviewService {

    /** 一个知识点少于几道题时提醒：掌握度不到 3 次作答会打折 */
    static final int MIN_PER_KP = 3;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final ExerciseService exerciseService;
    private final ExerciseMapper exerciseMapper;
    private final KnowledgePointMapper kpMapper;
    private final ChapterService chapterService;
    private final AnswerRecordMapper answerMapper;
    private final UserMapper userMapper;
    private final MasteryService masteryService;
    private final ObjectMapper json;

    public ExerciseReviewService(ExerciseService exerciseService, ExerciseMapper exerciseMapper,
                                 KnowledgePointMapper kpMapper, ChapterService chapterService,
                                 AnswerRecordMapper answerMapper, UserMapper userMapper,
                                 MasteryService masteryService, ObjectMapper json) {
        this.exerciseService = exerciseService;
        this.exerciseMapper = exerciseMapper;
        this.kpMapper = kpMapper;
        this.chapterService = chapterService;
        this.answerMapper = answerMapper;
        this.userMapper = userMapper;
        this.masteryService = masteryService;
        this.json = json;
    }

    /** 覆盖检查：本章每个知识点有几道题（已确认的），按考查类型、题型分开数 */
    public List<CoverageRow> coverage(Long chapterId) {
        chapterService.requireOwned(chapterId);
        // 小助手生成的个人补练题不算（没经过老师审核，只给一个学生）
        List<ExerciseView> all = exerciseService.list(chapterId, null).stream()
                .filter(e -> !ExerciseService.SOURCE_ASSISTANT.equals(e.source())).toList();
        List<CoverageRow> rows = new ArrayList<>();
        for (KnowledgePoint kp : kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getChapterId, chapterId).orderByAsc(KnowledgePoint::getId))) {
            List<ExerciseView> mine = all.stream()
                    .filter(e -> e.knowledgePoints().stream().anyMatch(k -> k.id().equals(kp.getId()))).toList();
            List<ExerciseView> live = mine.stream().filter(e -> !e.draft()).toList();
            Map<String, Integer> byAssess = new LinkedHashMap<>();
            for (String a : List.of("concept", "scenario", "data", "process", Grader.CUSTOM)) {
                byAssess.put(a, (int) live.stream().filter(e -> a.equals(e.assessType())).count());
            }
            Map<String, Integer> byType = new LinkedHashMap<>();
            for (String t : List.of(Grader.SINGLE, Grader.TWO_TIER, Grader.MULTIPLE, Grader.JUDGE, Grader.FILL,
                    Grader.SHORT)) {
                byType.put(t, (int) live.stream().filter(e -> t.equals(e.type())).count());
            }
            int drafts = mine.size() - live.size();
            String warning = live.isEmpty() ? (drafts > 0 ? "有草稿，确认后才算" : "还没有题")
                    : live.size() < MIN_PER_KP ? "少于 " + MIN_PER_KP + " 道，可能有要点没考到" : null;
            rows.add(new CoverageRow(kp.getId(), kp.getName(), byAssess, byType, live.size(), drafts, warning));
        }
        return rows;
    }

    /** 这次练习里简答题的全部作答（待确认的排前面） */
    public List<ShortAnswerView> shortAnswers(Long itemId) {
        List<Exercise> shorts = exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>()
                .eq(Exercise::getItemId, itemId).eq(Exercise::getType, Grader.SHORT));
        if (shorts.isEmpty()) {
            return List.of();
        }
        chapterService.requireOwned(shorts.get(0).getChapterId());
        Map<Long, Exercise> byId = shorts.stream().collect(Collectors.toMap(Exercise::getId, e -> e));
        List<AnswerRecord> records = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .in(AnswerRecord::getExerciseId, byId.keySet()).orderByDesc(AnswerRecord::getSubmittedAt));
        Map<Long, User> users = records.isEmpty() ? Map.of()
                : userMapper.selectByIds(records.stream().map(AnswerRecord::getStudentId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        return records.stream()
                .sorted((a, b) -> Boolean.compare(Grader.PENDING.equals(b.getStatus()), Grader.PENDING.equals(a.getStatus())))
                .map(a -> {
                    User u = users.get(a.getStudentId());
                    return new ShortAnswerView(a.getId(), a.getExerciseId(), byId.get(a.getExerciseId()).getStem(),
                            u == null ? "" : u.getName(), u == null ? "" : u.getUsername(), a.getAnswer(),
                            a.getStatus(), a.getScore(), a.getReliability(), a.getReviewedBy() != null,
                            a.getSubmittedAt() == null ? "" : a.getSubmittedAt().format(TIME), detail(a));
                }).toList();
    }

    /** 老师确认或改判一份简答：每条要点答到没有 → 得分；r = 1；重算这个学生的掌握度 */
    @Transactional
    public ShortAnswerView review(Long recordId, List<Boolean> hits) {
        AnswerRecord a = answerMapper.selectById(recordId);
        Exercise e = a == null ? null : exerciseMapper.selectById(a.getExerciseId());
        if (e == null || !Grader.SHORT.equals(e.getType())) {
            throw BusinessException.notFound("简答作答");
        }
        chapterService.requireOwned(e.getChapterId());
        ExerciseView v = exerciseService.toView(e);
        if (hits == null || hits.size() != v.keyPoints().size() || hits.stream().anyMatch(h -> h == null)) {
            throw BusinessException.badRequest("每条要点都要选“答到”或“没答到”");
        }
        List<Map<String, Object>> points = detail(a);
        for (int i = 0; i < hits.size(); i++) {
            Map<String, Object> p = i < points.size() ? points.get(i) : new LinkedHashMap<>();
            p.put("no", i + 1);
            p.put("text", v.keyPoints().get(i));
            p.put("hit", hits.get(i));
            p.put("agreed", true);
            p.put("teacher", true);
            if (i >= points.size()) {
                points.add(p);
            }
        }
        double score = ShortAnswerJudge.scoreOf(hits);
        a.setScore(score);
        a.setIsCorrect(score >= 1);
        a.setReliability(1.0);
        a.setStatus(Grader.GRADED);
        a.setReviewedBy(chapterService.currentTeacherId());
        a.setDetail(json.valueToTree(points).toString());
        answerMapper.updateById(a);
        masteryService.update(a.getStudentId(), List.of(e.getId()));
        return shortAnswers(e.getItemId()).stream().filter(x -> x.recordId().equals(recordId)).findFirst()
                .orElseThrow();
    }

    private List<Map<String, Object>> detail(AnswerRecord a) {
        try {
            return a.getDetail() == null ? new ArrayList<>()
                    : json.readValue(a.getDetail(), new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
