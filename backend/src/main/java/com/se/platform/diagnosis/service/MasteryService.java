package com.se.platform.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.mapper.ExerciseMapper;
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
 * 知识点掌握度（docs/模块说明/练习与掌握度-设计.md 第五节）：加权近期正确率，再扣掉蒙对的部分。
 * <pre>
 * 每次作答 i 的权重 Wᵢ = tᵢ × rᵢ
 *   tᵢ = 0.8^(n-i)：时间权重，最近一次 1，往前每次乘 0.8，只取最近 10 次
 *   rᵢ：可信度权重，程序判的 1，简答两次判分一致 0.5，老师确认过 1
 * 正确率   p = Σ Wᵢ × sᵢ / Σ Wᵢ        sᵢ = 这题得分 0～1
 * 蒙对概率 g = Σ Wᵢ × gᵢ / Σ Wᵢ        gᵢ = 这题蒙对的概率（单选 1/选项数，判断 0.5……）
 * 掌握度   m = max(0, (p − g) / (1 − g))
 * 作答少于 3 次：m 再乘 次数/3，避免做对 1 道就算掌握
 * </pre>
 * 等老师确认的简答（pending）不算。一道题可以关联几个知识点，它的每次作答都算进这几个知识点。
 */
@Service
public class MasteryService {

    static final int WINDOW = 10;
    static final double DECAY = 0.8;
    static final int MIN_ATTEMPTS = 3;

    private final MasteryMapper masteryMapper;
    private final AnswerRecordMapper answerMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final KnowledgePointMapper kpMapper;
    private final ExerciseMapper exerciseMapper;

    public MasteryService(MasteryMapper masteryMapper, AnswerRecordMapper answerMapper,
                          ExerciseKnowledgePointMapper linkMapper, KnowledgePointMapper kpMapper,
                          ExerciseMapper exerciseMapper) {
        this.exerciseMapper = exerciseMapper;
        this.masteryMapper = masteryMapper;
        this.answerMapper = answerMapper;
        this.linkMapper = linkMapper;
        this.kpMapper = kpMapper;
    }

    /** 一次作答：得分 s、蒙对概率 g、可信度权重 r */
    public record Attempt(double score, double guess, double reliability) {
    }

    /** 只有对错的作答（旧接口，g = 0、r = 1） */
    public static Double compute(List<Boolean> results) {
        return computeAttempts(results.stream()
                .map(b -> new Attempt(Boolean.TRUE.equals(b) ? 1 : 0, 0, 1)).toList());
    }

    /** 按作答（从早到晚）算掌握度，0～1，保留 4 位小数；没作答返回 null */
    public static Double computeAttempts(List<Attempt> all) {
        if (all.isEmpty()) {
            return null;
        }
        List<Attempt> recent = all.subList(Math.max(0, all.size() - WINDOW), all.size());
        int n = recent.size();
        double sum = 0;
        double guess = 0;
        double weights = 0;
        for (int i = 0; i < n; i++) {
            Attempt a = recent.get(i);
            double w = Math.pow(DECAY, n - 1 - i) * a.reliability(); // 最近一次时间权重 1，往前每次乘 0.8
            weights += w;
            sum += w * a.score();
            guess += w * a.guess();
        }
        if (weights == 0) {
            return null;
        }
        double p = sum / weights;
        double g = guess / weights;
        double m = g >= 1 ? 0 : Math.max(0, (p - g) / (1 - g));
        if (all.size() < MIN_ATTEMPTS) {
            m = m * all.size() / MIN_ATTEMPTS;
        }
        return Math.round(m * 10000) / 10000.0;
    }

    /** 一条作答记录 → Attempt。旧记录没存得分、g、r：得分按对错，g 按题型算，r = 1 */
    Attempt attemptOf(AnswerRecord a, Map<Long, Exercise> exercises) {
        double score = a.getScore() != null ? a.getScore() : Boolean.TRUE.equals(a.getIsCorrect()) ? 1 : 0;
        Double g = a.getGuess();
        if (g == null) {
            Exercise e = exercises.get(a.getExerciseId());
            g = e == null ? 0 : Grader.guess(e.getType(), count(e.getOptions()), count(e.getReasons()));
        }
        return new Attempt(score, g, a.getReliability() == null ? 1 : a.getReliability());
    }

    private static int count(String jsonArray) {
        try {
            return jsonArray == null || jsonArray.isBlank() ? 0 : JSON.readTree(jsonArray).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

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
            List<AnswerRecord> records = answersOf(studentId, kpId).stream()
                    .filter(a -> !Grader.PENDING.equals(a.getStatus())).toList();
            Map<Long, Exercise> exercises = records.isEmpty() ? Map.of()
                    : exerciseMapper.selectByIds(records.stream().map(AnswerRecord::getExerciseId).distinct().toList())
                    .stream().collect(Collectors.toMap(Exercise::getId, e -> e));
            List<Attempt> attempts = records.stream().map(a -> attemptOf(a, exercises)).toList();
            Mastery row = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                    .eq(Mastery::getStudentId, studentId)
                    .eq(Mastery::getKnowledgePointId, kpId));
            Double before = row == null ? null : row.getMastery();
            if (row == null) {
                row = new Mastery();
                row.setStudentId(studentId);
                row.setKnowledgePointId(kpId);
            }
            row.setMastery(computeAttempts(attempts));
            row.setAttemptCount(attempts.size());
            row.setCorrectCount((int) attempts.stream().filter(x -> x.score() >= 1).count());
            if (row.getId() == null) {
                masteryMapper.insert(row);
            } else {
                masteryMapper.updateById(row);
            }
            changes.add(new MasteryChange(kpId, kp.getName(), before, row.getMastery(), level(row.getMastery())));
        }
        return changes;
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
