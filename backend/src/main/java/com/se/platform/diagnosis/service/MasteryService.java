package com.se.platform.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
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
 * 知识点掌握度（docs/design.md 5.1）：加权近期正确率。
 * <pre>
 * mastery = Σ(wᵢ × correctᵢ) / Σ wᵢ ，wᵢ = 0.8^(n-i)，i = 1（最早）…n（最近），只取最近 10 次
 * 作答少于 3 次：结果再乘 次数/3，避免做对 1 道就算掌握
 * </pre>
 * 一道题可以关联几个知识点，它的每次作答都算进这几个知识点。
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

    public MasteryService(MasteryMapper masteryMapper, AnswerRecordMapper answerMapper,
                          ExerciseKnowledgePointMapper linkMapper, KnowledgePointMapper kpMapper) {
        this.masteryMapper = masteryMapper;
        this.answerMapper = answerMapper;
        this.linkMapper = linkMapper;
        this.kpMapper = kpMapper;
    }

    /** 按作答对错（从早到晚）算掌握度，0～1，保留 4 位小数；没作答返回 null */
    public static Double compute(List<Boolean> results) {
        if (results.isEmpty()) {
            return null;
        }
        List<Boolean> recent = results.subList(Math.max(0, results.size() - WINDOW), results.size());
        int n = recent.size();
        double sum = 0;
        double weights = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.pow(DECAY, n - 1 - i); // 最近一次权重 1，往前每次乘 0.8
            weights += w;
            if (Boolean.TRUE.equals(recent.get(i))) {
                sum += w;
            }
        }
        double m = sum / weights;
        if (results.size() < MIN_ATTEMPTS) {
            m = m * results.size() / MIN_ATTEMPTS;
        }
        return Math.round(m * 10000) / 10000.0;
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
            List<Boolean> results = answersOf(studentId, kpId).stream().map(AnswerRecord::getIsCorrect).toList();
            Mastery row = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                    .eq(Mastery::getStudentId, studentId)
                    .eq(Mastery::getKnowledgePointId, kpId));
            Double before = row == null ? null : row.getMastery();
            if (row == null) {
                row = new Mastery();
                row.setStudentId(studentId);
                row.setKnowledgePointId(kpId);
            }
            row.setMastery(compute(results));
            row.setAttemptCount(results.size());
            row.setCorrectCount((int) results.stream().filter(Boolean.TRUE::equals).count());
            if (row.getId() == null) {
                masteryMapper.insert(row);
            } else {
                masteryMapper.updateById(row);
            }
            changes.add(new MasteryChange(kpId, kp.getName(), before, row.getMastery(), level(row.getMastery())));
        }
        return changes;
    }

    /** 学生在一个知识点上的全部作答（通过关联的题目），从早到晚 */
    public List<AnswerRecord> answersOf(long studentId, Long kpId) {
        List<Long> exerciseIds = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getKnowledgePointId, kpId))
                .stream().map(ExerciseKnowledgePoint::getExerciseId).toList();
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
