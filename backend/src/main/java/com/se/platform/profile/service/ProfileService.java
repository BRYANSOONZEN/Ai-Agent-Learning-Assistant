package com.se.platform.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.diagnosis.dto.DiagnosisDtos.PointDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.StudentDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.TopicOutcome;
import com.se.platform.diagnosis.service.DiagnosisService;
import com.se.platform.profile.dto.ProfileDtos.DayActivity;
import com.se.platform.profile.dto.ProfileDtos.Dimension;
import com.se.platform.profile.dto.ProfileDtos.QuizPoint;
import com.se.platform.profile.dto.ProfileDtos.Stats;
import com.se.platform.profile.dto.ProfileDtos.StudentProfile;
import com.se.platform.profile.dto.ProfileDtos.Tag;
import com.se.platform.record.dto.RecordDtos.CourseProgress;
import com.se.platform.record.dto.RecordDtos.QuizAttempt;
import com.se.platform.record.dto.RecordDtos.RecordOverview;
import com.se.platform.record.dto.RecordDtos.Summary;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.mapper.LearningEventMapper;
import com.se.platform.record.service.RecordService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 学生画像：不存新数据，用「记」（学习记录）和「诊」（掌握度、对话结果）现算出
 * 六个维度、几条带依据的标签、近 14 天的学习量和每次交卷的正确率。
 * 每个数都能说清楚是怎么来的；数据不够的维度留空，不硬凑。
 */
@Service
public class ProfileService {

    static final int DAYS = 14;
    /** 近 14 天学够 7 天（隔天学一次）投入度算满 */
    static final int FULL_ACTIVE_DAYS = 7;

    private final RecordService recordService;
    private final DiagnosisService diagnosisService;
    private final LearningEventMapper eventMapper;
    private final AnswerRecordMapper answerMapper;

    public ProfileService(RecordService recordService, DiagnosisService diagnosisService,
                          LearningEventMapper eventMapper, AnswerRecordMapper answerMapper) {
        this.recordService = recordService;
        this.diagnosisService = diagnosisService;
        this.eventMapper = eventMapper;
        this.answerMapper = answerMapper;
    }

    public StudentProfile student(long studentId) {
        RecordOverview records = recordService.overview(studentId);
        StudentDiagnosis diagnosis = diagnosisService.student(studentId);
        List<PointDiagnosis> points = diagnosis.courses().stream()
                .flatMap(c -> c.chapters().stream()).flatMap(ch -> ch.points().stream()).toList();
        List<DayActivity> days = days(studentId);
        int activeDays = (int) days.stream().filter(d -> d.actions() > 0).count();

        Summary s = records.summary();
        Double accuracy = s.questionsAnswered() == 0 ? null : round((double) s.correctAnswers() / s.questionsAnswered());
        Stats stats = new Stats(s.minutes(), activeDays, s.itemsFinished(), s.questionsAnswered(), accuracy,
                diagnosis.summary().weak());

        List<QuizAttempt> attempts = new ArrayList<>(records.quizzes());
        Collections.reverse(attempts); // 从早到晚
        List<QuizPoint> trend = attempts.stream()
                .map(q -> new QuizPoint(q.submittedAt(), q.title(), q.correct(), q.total())).toList();

        Retry retry = retry(attempts);
        return new StudentProfile(studentId, diagnosis.studentName(), stats,
                tags(activeDays, s.minutes(), points, diagnosis.dialogueTopics(), retry),
                dimensions(activeDays, s.minutes(), records.courses(), accuracy, s, points,
                        diagnosis.dialogueTopics(), retry),
                days, trend);
    }

    // ------------------------------------------------------------------ 维度

    private List<Dimension> dimensions(int activeDays, int minutes, List<CourseProgress> courses, Double accuracy,
                                       Summary s, List<PointDiagnosis> points,
                                       List<TopicOutcome> topics, Retry retry) {
        List<Dimension> list = new ArrayList<>();

        list.add(new Dimension("engagement", "投入", activeDays == 0 ? null
                : round(Math.min(1.0, (double) activeDays / FULL_ACTIVE_DAYS)),
                activeDays == 0 ? "近 14 天还没有学习记录"
                        : "近 14 天学了 " + activeDays + " 天，共 " + minutes + " 分钟；隔天学一次算满分"));

        int done = courses.stream().mapToInt(CourseProgress::done).sum();
        int total = courses.stream().mapToInt(CourseProgress::total).sum();
        list.add(new Dimension("progress", "完成度", total == 0 ? null : round((double) done / total),
                total == 0 ? "还没开始学课程" : "学过的 " + courses.size() + " 门课里，学完 " + done + " / " + total + " 项内容"));

        list.add(new Dimension("accuracy", "正确率", accuracy,
                accuracy == null ? "还没做过练习" : "答对 " + s.correctAnswers() + " / " + s.questionsAnswered() + " 题"));

        List<PointDiagnosis> practiced = points.stream().filter(p -> p.mastery() != null).toList();
        Double mastery = practiced.isEmpty() ? null
                : round(practiced.stream().mapToDouble(PointDiagnosis::mastery).average().orElse(0));
        list.add(new Dimension("mastery", "掌握度", mastery, practiced.isEmpty() ? "还没有练过的知识点"
                : "练过的 " + practiced.size() + " 个知识点的平均掌握度（见学情诊断）"));

        list.add(new Dimension("correction", "订正", retry.redone == 0 ? null : round((double) retry.improved / retry.redone),
                retry.redone == 0 ? "还没有重做过做错的练习"
                        : "做错后重做的 " + retry.redone + " 组练习里，" + retry.improved + " 组成绩提高了"));

        // 自己答出算 1，提示后答出 0.6，看了讲解 0.2，跳过 0
        Double independence = topics.isEmpty() ? null : round(topics.stream().mapToDouble(t -> switch (t.result()) {
            case "SELF_CORRECT" -> 1.0;
            case "HINT_CORRECT" -> 0.6;
            case "REVEALED" -> 0.2;
            default -> 0.0;
        }).average().orElse(0));
        long self = topics.stream().filter(t -> "SELF_CORRECT".equals(t.result())).count();
        list.add(new Dimension("independence", "独立思考", independence, topics.isEmpty()
                ? "还没有完成的 AI 对话话题"
                : "AI 对话的 " + topics.size() + " 个话题里，" + self + " 个是自己想出来的（提示后答出、看讲解的分数低一些）"));
        return list;
    }

    // ------------------------------------------------------------------ 标签

    private List<Tag> tags(int activeDays, int minutes, List<PointDiagnosis> points, List<TopicOutcome> topics,
                           Retry retry) {
        List<Tag> tags = new ArrayList<>();
        if (activeDays >= 4) {
            tags.add(new Tag("behavior", "学习规律", "近 14 天学了 " + activeDays + " 天"));
        } else if (activeDays > 0) {
            tags.add(new Tag("behavior", "学习不太连续", "近 14 天只学了 " + activeDays + " 天，共 " + minutes + " 分钟"));
        }
        if (retry.improved > 0) {
            tags.add(new Tag("behavior", "错了会回头订正", retry.improved + " 组做错的练习重做后成绩提高了"));
        }
        if (!topics.isEmpty()) {
            long self = topics.stream().filter(t -> "SELF_CORRECT".equals(t.result())).count();
            long revealed = topics.stream().filter(t -> "REVEALED".equals(t.result())).count();
            if (self * 2 >= topics.size()) {
                tags.add(new Tag("behavior", "能独立想明白", "AI 对话 " + topics.size() + " 个话题里 " + self + " 个自己答出"));
            } else if (revealed * 2 >= topics.size()) {
                tags.add(new Tag("behavior", "依赖讲解", "AI 对话 " + topics.size() + " 个话题里 " + revealed + " 个看了讲解才懂"));
            }
        }
        for (PointDiagnosis p : points) {
            if ("good".equals(p.level())) {
                tags.add(new Tag("strength", "擅长：" + p.name(), "掌握度 " + percent(p.mastery())));
            } else if ("weak".equals(p.level())) {
                tags.add(new Tag("weakness", "薄弱：" + p.name(), "掌握度 " + percent(p.mastery())
                        + "，答对 " + p.correctCount() + " / " + p.attemptCount() + " 次"));
            }
        }
        return tags;
    }

    // ------------------------------------------------------------------ 近 14 天

    private List<DayActivity> days(long studentId) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(DAYS - 1).atStartOfDay();
        Map<LocalDate, int[]> byDay = new LinkedHashMap<>(); // [秒, 次数]
        for (int i = DAYS - 1; i >= 0; i--) {
            byDay.put(today.minusDays(i), new int[2]);
        }
        eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getStudentId, studentId)
                        .ge(LearningEvent::getCreatedAt, from))
                .forEach(e -> {
                    int[] d = byDay.get(e.getCreatedAt().toLocalDate());
                    if (d != null) {
                        d[0] += e.getDurationSec() == null ? 0 : e.getDurationSec();
                        d[1]++;
                    }
                });
        // 一次交卷算一件事（同一次交卷的几题时间相同）
        Set<String> submits = new HashSet<>();
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId)
                        .ge(AnswerRecord::getSubmittedAt, from))
                .forEach(a -> {
                    if (submits.add(a.getItemId() + "@" + a.getSubmittedAt())) {
                        int[] d = byDay.get(a.getSubmittedAt().toLocalDate());
                        if (d != null) {
                            d[1]++;
                        }
                    }
                });
        return byDay.entrySet().stream()
                .map(e -> new DayActivity(e.getKey(), (int) Math.round(e.getValue()[0] / 60.0), e.getValue()[1]))
                .toList();
    }

    // ------------------------------------------------------------------ 小工具

    /** 订正：同一组练习，答错过之后又交了卷，后面最好的一次比那次错的高就算提高了 */
    private record Retry(int redone, int improved) {
    }

    private static Retry retry(List<QuizAttempt> attemptsOldFirst) {
        Map<Long, List<QuizAttempt>> byItem = attemptsOldFirst.stream()
                // 推荐练习里做的题没有 itemId，算作一组
                .collect(Collectors.groupingBy(q -> q.itemId() == null ? -1L : q.itemId(), LinkedHashMap::new,
                        Collectors.toList()));
        int redone = 0;
        int improved = 0;
        for (List<QuizAttempt> list : byItem.values()) {
            for (int i = 0; i < list.size() - 1; i++) {
                QuizAttempt q = list.get(i);
                if (q.correct() < q.total()) {
                    redone++;
                    double before = (double) q.correct() / q.total();
                    boolean better = list.subList(i + 1, list.size()).stream()
                            .anyMatch(later -> (double) later.correct() / later.total() > before);
                    if (better) {
                        improved++;
                    }
                    break; // 每组练习只看第一次做错之后
                }
            }
        }
        return new Retry(redone, improved);
    }

    private static Double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    private static String percent(Double v) {
        return v == null ? "-" : Math.round(v * 100) + "%";
    }
}
