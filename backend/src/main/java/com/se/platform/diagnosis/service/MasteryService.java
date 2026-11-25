package com.se.platform.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.service.Grader;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.diagnosis.dto.DiagnosisDtos.MasteryChange;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 知识点掌握度（docs/模块说明/练习与掌握度-设计.md 第五节）。每道题只算第一次作答，之后重做只是练习。
 * <pre>
 * 每道题的得分 s：答对 1，答错 0；多选少选 0.5；简答 = 答到的要点数 / 要点总数
 * 原始掌握度 first  = 第一次是在练习（内容项）里做的题，得分求平均；之后不再改
 * 补练       remedy = 第一次是在小助手推荐、干预布置里做的题（都是没做过的新题），得分求平均
 * 当前掌握度 m      = 补练够 2 道用 remedy，否则用 first（画像颜色、推荐、干预都看它）
 * 补练状态（第一次 < 0.8 才有）：补练够 2 道且 remedy ≥ 0.8 → recovered 已补回来；做过补练但还没到 → practicing 补练中
 * </pre>
 * 不加时间权重、不扣蒙对的部分、不打次数折扣。第一次是等老师确认的简答（pending），确认之前这道题先不算。
 * 一道题可以关联几个知识点，它的作答都算进这几个知识点。
 */
@Service
public class MasteryService {

    /** 补练至少几道新题才算数 */
    public static final int MIN_REMEDY = 2;
    /** 补练平均分达到多少算“已补回来” */
    public static final double RECOVERED = 0.8;
    public static final String STATUS_RECOVERED = "recovered";
    public static final String STATUS_PRACTICING = "practicing";

    private final MasteryMapper masteryMapper;
    private final AnswerRecordMapper answerMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final KnowledgePointMapper kpMapper;

    public MasteryService(MasteryMapper masteryMapper, AnswerRecordMapper answerMapper,
                          ExerciseKnowledgePointMapper linkMapper, KnowledgePointMapper kpMapper) {
        this.masteryMapper = masteryMapper;
        this.answerMapper = answerMapper;
        this.linkMapper = linkMapper;
        this.kpMapper = kpMapper;
    }

    /** 每道题一个得分（0～1）→ 平均分，保留 4 位小数；没有作答返回 null */
    public static Double compute(List<Double> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        double sum = scores.stream().mapToDouble(Double::doubleValue).sum();
        return Math.round(sum / scores.size() * 10000) / 10000.0;
    }

    /** 一条作答的得分。旧记录没存得分：按对错算 */
    static double scoreOf(AnswerRecord a) {
        return a.getScore() != null ? a.getScore() : Boolean.TRUE.equals(a.getIsCorrect()) ? 1 : 0;
    }

    /** 每道题只留第一次的作答（records 从早到晚）；第一次还在等老师确认的，这道题先不算 */
    static List<AnswerRecord> firstPerExercise(List<AnswerRecord> records) {
        Map<Long, AnswerRecord> first = new java.util.LinkedHashMap<>();
        for (AnswerRecord a : records) {
            first.putIfAbsent(a.getExerciseId(), a);
        }
        return first.values().stream().filter(a -> !Grader.PENDING.equals(a.getStatus())).toList();
    }

    public static final String GREEN = "green";
    public static final String ORANGE = "orange";
    public static final String RED = "red";

    /**
     * 画像颜色（每个学生、每个知识点；docs/模块说明/练习与掌握度-设计.md 5.1）：
     * 第一次练习就掌握（≥ 0.8）→ green；第一次没掌握、补练后补回来了 → orange（曾经错过，要留意，不变成绿色）；
     * 还没补回来 → red；没练过 → none。
     */
    public static String color(Mastery m) {
        if (m == null || (m.getFirstCount() == null || m.getFirstCount() == 0)
                && (m.getRemedyCount() == null || m.getRemedyCount() == 0)) {
            return "none";
        }
        if (m.getFirstMastery() != null && m.getFirstMastery() >= RECOVERED) {
            return GREEN;
        }
        return STATUS_RECOVERED.equals(m.getRemedyStatus()) ? ORANGE : RED;
    }

    /** good 掌握 / medium 一般 / weak 薄弱 / none 还没练过（阈值和 docs/api.md 一致） */
    public static String level(Double mastery) {
        if (mastery == null) {
            return "none";
        }
        return mastery >= 0.8 ? "good" : mastery >= 0.6 ? "medium" : "weak";
    }

    /** 交卷后：重算这几道题关联的知识点，返回每个知识点前后的变化 */
    public List<MasteryChange> update(long studentId, Collection<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return List.of();
        }
        List<Long> kpIds = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .in(ExerciseKnowledgePoint::getExerciseId, exerciseIds))
                .stream().map(ExerciseKnowledgePoint::getKnowledgePointId).distinct().toList();
        if (kpIds.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgePoint> kps = kpMapper.selectByIds(kpIds).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, k -> k));
        List<MasteryChange> changes = new ArrayList<>();
        for (Long kpId : kpIds) {
            KnowledgePoint kp = kps.get(kpId);
            if (kp == null) {
                continue; // 知识点已删除
            }
            Mastery row = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                    .eq(Mastery::getStudentId, studentId)
                    .eq(Mastery::getKnowledgePointId, kpId));
            Double before = row == null ? null : row.getMastery();
            if (row == null) {
                row = new Mastery();
                row.setStudentId(studentId);
                row.setKnowledgePointId(kpId);
            }
            fill(row, answersOf(studentId, kpId));
            if (row.getId() == null) {
                masteryMapper.insert(row);
            } else {
                masteryMapper.updateById(row);
            }
            changes.add(new MasteryChange(kpId, kp.getName(), before, row.getMastery(), level(row.getMastery()),
                    row.getRemedyStatus(), color(row)));
        }
        return changes;
    }

    /** 按作答（从早到晚）填好一行：原始掌握度、补练、当前掌握度、补练状态；返回有没有变 */
    static boolean fill(Mastery row, List<AnswerRecord> records) {
        List<Double> first = new ArrayList<>();
        List<Double> remedy = new ArrayList<>();
        for (AnswerRecord a : firstPerExercise(records)) {
            (a.getItemId() != null ? first : remedy).add(scoreOf(a)); // 练习里做的 / 推荐、干预里做的
        }
        Double f = compute(first);
        Double r = compute(remedy);
        Double m = remedy.size() >= MIN_REMEDY || f == null ? r : f;
        // 第一次就掌握了（≥ 0.8）的不需要“补回来”，不标状态
        String status = remedy.isEmpty() || f != null && f >= RECOVERED ? null
                : remedy.size() >= MIN_REMEDY && r >= RECOVERED ? STATUS_RECOVERED : STATUS_PRACTICING;
        int count = first.size() + remedy.size();
        int correct = (int) java.util.stream.Stream.concat(first.stream(), remedy.stream()).filter(x -> x >= 1).count();
        boolean changed = !java.util.Objects.equals(m, row.getMastery()) || !java.util.Objects.equals(f, row.getFirstMastery())
                || !java.util.Objects.equals(r, row.getRemedy()) || !java.util.Objects.equals(status, row.getRemedyStatus())
                || !java.util.Objects.equals(count, row.getAttemptCount())
                || !java.util.Objects.equals(correct, row.getCorrectCount())
                || !java.util.Objects.equals(first.size(), row.getFirstCount())
                || !java.util.Objects.equals(remedy.size(), row.getRemedyCount());
        row.setMastery(m == null ? 0.0 : m);
        row.setFirstMastery(f);
        row.setFirstCount(first.size());
        row.setRemedy(r);
        row.setRemedyCount(remedy.size());
        row.setRemedyStatus(status);
        row.setAttemptCount(count);
        row.setCorrectCount(correct);
        return changed;
    }

    /** 启动时按作答重算所有已有的掌握度（计分方法改过后，旧的值跟着更新）；返回改了几条 */
    public int recomputeAll() {
        int changed = 0;
        for (Mastery row : masteryMapper.selectList(null)) {
            if (fill(row, answersOf(row.getStudentId(), row.getKnowledgePointId()))) {
                masteryMapper.updateById(row);
                changed++;
            }
        }
        return changed;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recomputeOnStartup() {
        int n = recomputeAll();
        if (n > 0) {
            org.slf4j.LoggerFactory.getLogger(MasteryService.class).info("掌握度按新计分方法重算了 {} 条", n);
        }
    }

    /** 关联了这个知识点的题目 id */
    public List<Long> exerciseIdsOf(Long kpId) {
        return linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getKnowledgePointId, kpId))
                .stream().map(ExerciseKnowledgePoint::getExerciseId).toList();
    }

    /** 学生在一个知识点上的全部作答（通过关联的题目），从早到晚 */
    public List<AnswerRecord> answersOf(long studentId, Long kpId) {
        List<Long> exerciseIds = exerciseIdsOf(kpId);
        if (exerciseIds.isEmpty()) {
            return List.of();
        }
        return answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getStudentId, studentId)
                .in(AnswerRecord::getExerciseId, exerciseIds)
                .orderByAsc(AnswerRecord::getSubmittedAt)
                .orderByAsc(AnswerRecord::getId));
    }
}
