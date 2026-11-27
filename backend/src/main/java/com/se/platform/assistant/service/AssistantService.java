package com.se.platform.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.assistant.dto.AssistantDtos.ChapterProgress;
import com.se.platform.assistant.dto.AssistantDtos.ChapterReport;
import com.se.platform.assistant.dto.AssistantDtos.PointReport;
import com.se.platform.assistant.dto.AssistantDtos.Summary;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.assistant.dto.AssistantDtos.PlanStep;
import com.se.platform.assistant.entity.RemedyRound;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.LearningEventMapper;
import java.time.LocalDateTime;
import com.se.platform.feynman.service.FeynmanDiagnosis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 个人学习助手（docs/计划-个人学习助手.md 第 4、5 节）。第一版：全部用规则和模板，不调大模型。
 *
 * <ul>
 *   <li>学完一章 = 这一章所有学习项都完成过一次（learning_event 里的 finish_item）</li>
 *   <li>做过这一章的练习后的个人总结：每个知识点只看练习（当前掌握度的颜色、第一次的掌握度、补练状态），
 *       加上错在哪条要点、最常见的误解、复习方案（看资料 → 小助手里的补练题）</li>
 * </ul>
 * 只读这个学生自己的数据；题目答案、话题要点、其他学生的数据都不碰。
 */
@Service
public class AssistantService {

    private final FeynmanDiagnosis feynmanDiagnosis;

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final KnowledgePointMapper kpMapper;
    private final LearningEventMapper eventMapper;
    private final MasteryMapper masteryMapper;
    private final RemedyService remedyService;
    private final PracticeAnalysis practiceAnalysis;

    public AssistantService(CourseMapper courseMapper, ChapterMapper chapterMapper, ChapterItemMapper itemMapper,
                            KnowledgePointMapper kpMapper, LearningEventMapper eventMapper, MasteryMapper masteryMapper,
                            RemedyService remedyService, PracticeAnalysis practiceAnalysis,
                            FeynmanDiagnosis feynmanDiagnosis) {
        this.feynmanDiagnosis = feynmanDiagnosis;
        this.practiceAnalysis = practiceAnalysis;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.kpMapper = kpMapper;
        this.eventMapper = eventMapper;
        this.masteryMapper = masteryMapper;
        this.remedyService = remedyService;
    }

    public Summary summary(long studentId) {
        // 每个学习项第一次完成的时间
        Map<Long, LocalDateTime> finished = new HashMap<>();
        eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getStudentId, studentId)
                        .eq(LearningEvent::getEventType, LearningEvent.FINISH_ITEM))
                .forEach(e -> finished.merge(e.getTargetId(), e.getCreatedAt(),
                        (a, b) -> a == null ? b : b == null ? a : a.isBefore(b) ? a : b));

        List<ChapterProgress> chapters = new ArrayList<>();
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, "published")
                .orderByAsc(Course::getId));
        for (Course course : courses) {
            List<Chapter> published = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                    .eq(Chapter::getCourseId, course.getId())
                    .eq(Chapter::getStatus, "published")
                    .orderByAsc(Chapter::getSortOrder)
                    .orderByAsc(Chapter::getId));
            for (Chapter ch : published) {
                ChapterProgress p = progress(studentId, course, ch, finished);
                if (p != null) {
                    chapters.add(p);
                }
            }
        }
        chapters.sort(Comparator.comparing(ChapterProgress::lastAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Long focus = chapters.stream().filter(ChapterProgress::completed).findFirst()
                .or(() -> chapters.stream().findFirst())
                .map(ChapterProgress::chapterId).orElse(null);
        return new Summary(chapters, focus);
    }

    /** 一章的进度；一项都没开始的章节返回 null（助手只说学过的） */
    private ChapterProgress progress(long studentId, Course course, Chapter ch, Map<Long, LocalDateTime> finished) {
        List<ChapterItem> items = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                .eq(ChapterItem::getChapterId, ch.getId())
                .orderByAsc(ChapterItem::getSortOrder)
                .orderByAsc(ChapterItem::getId));
        if (items.isEmpty()) {
            return null;
        }
        List<ChapterItem> doneItems = items.stream().filter(i -> finished.containsKey(i.getId())).toList();
        if (doneItems.isEmpty()) {
            return null;
        }
        boolean completed = doneItems.size() == items.size();
        LocalDateTime lastAt = doneItems.stream().map(i -> finished.get(i.getId())).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        ChapterItem next = items.stream().filter(i -> !finished.containsKey(i.getId())).findFirst().orElse(null);
        Long dialogueItemId = items.stream().filter(i -> "dialogue".equals(i.getType())).map(ChapterItem::getId)
                .findFirst().orElse(null);
        return new ChapterProgress(course.getId(), course.getName(), ch.getId(), ch.getTitle(), doneItems.size(),
                items.size(), completed, lastAt, next == null ? null : next.getId(),
                next == null ? null : next.getTitle(), dialogueItemId,
                completed || practiceAnalysis.practiced(studentId, items) ? report(studentId, ch, completed) : null);
    }

    /** 学完一章后（或者做过这一章的练习后）的个人总结 */
    ChapterReport report(long studentId, Chapter ch) {
        return report(studentId, ch, true);
    }

    ChapterReport report(long studentId, Chapter ch, boolean completed) {
        PracticeAnalysis.Context ctx = practiceAnalysis.context(ch.getId());
        List<KnowledgePoint> kps = kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getChapterId, ch.getId())
                .orderByAsc(KnowledgePoint::getSortOrder)
                .orderByAsc(KnowledgePoint::getId));
        Map<Long, String> dialogue = feynmanDiagnosis.byKnowledgePoint(studentId, ch.getId());
        List<PointReport> points = kps.stream().map(kp -> point(studentId, kp, ctx, dialogue.get(kp.getId()))).toList();
        int good = (int) points.stream().filter(p -> MasteryService.GREEN.equals(p.verdict())).count();
        int orange = (int) points.stream().filter(p -> MasteryService.ORANGE.equals(p.verdict())).count();
        int weak = (int) points.stream().filter(p -> isWeak(p.verdict())).count();
        int none = (int) points.stream().filter(p -> "none".equals(p.verdict())).count();
        String headline;
        if (points.isEmpty()) {
            headline = "「" + ch.getTitle() + "」你学完了。这一章还没有设置知识点，暂时没有更细的分析。";
        } else if (none == points.size()) {
            headline = "「" + ch.getTitle() + "」" + (completed ? "你学完了，" : "") + "知识点都还没有足够的数据判断，先把练习做完。";
        } else {
            // 只说不为 0 的部分，例如“7 个知识点里 4 个第一次就掌握，1 个补回来了，2 个还没掌握”
            List<String> parts = new ArrayList<>();
            if (good > 0) {
                parts.add(good + " 个第一次就掌握");
            }
            if (orange > 0) {
                parts.add(orange + " 个补回来了");
            }
            if (weak > 0) {
                parts.add(weak + " 个还没掌握，需要补");
            }
            if (none > 0) {
                parts.add(none + " 个还没练到");
            }
            headline = "「" + ch.getTitle() + "」" + (completed ? "你学完了。" : "根据你目前的练习：") + points.size()
                    + " 个知识点里 " + String.join("，", parts) + "。";
        }
        return new ChapterReport(headline, good, weak, points);
    }

    static boolean isWeak(String verdict) {
        return MasteryService.RED.equals(verdict);
    }

    /**
     * 一个知识点：只看练习（2026-11-25 起 AI 对话不打分，不再用“概念层”）。
     * 颜色 = 当前掌握度的等级；红、橙的给复习方案和“做补练题”（小助手的自动干预，见 RemedyService）。
     */
    private PointReport point(long studentId, KnowledgePoint kp, PracticeAnalysis.Context ctx, String dialogue) {
        Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId).eq(Mastery::getKnowledgePointId, kp.getId()));
        boolean practiced = m != null && m.getFirstCount() != null && m.getFirstCount() > 0
                || m != null && m.getRemedyCount() != null && m.getRemedyCount() > 0;
        Double mastery = practiced ? m.getMastery() : null;
        String level = MasteryService.level(mastery);
        String color = MasteryService.color(m);
        String status = m == null ? null : m.getRemedyStatus();
        List<RemedyRound> rounds = remedyService.rounds(studentId, kp.getId());
        boolean escalated = rounds.stream().anyMatch(r -> Boolean.TRUE.equals(r.getEscalated()));
        boolean weak = MasteryService.RED.equals(color);
        PracticeAnalysis.Result pr = practiceAnalysis.analyse(studentId, kp, ctx, weak, false);
        String misconception = weak ? pr.misconception() : null;
        // 方案里只留“看资料”；做题改成小助手里的补练题
        List<PlanStep> plan = new ArrayList<>(pr.plan().stream().filter(st -> "review".equals(st.kind())).toList());

        String first = m == null || m.getFirstMastery() == null ? null
                : String.format("第一次练习 %d%%", Math.round(m.getFirstMastery() * 100));
        String text;
        String action = null;
        if (!practiced) {
            text = "还没做过这个知识点的练习。";
        } else if (MasteryService.GREEN.equals(color)) {
            text = "第一次练习就掌握了。想挑战的话，可以来点更难的题。";
            action = "extend";
        } else if (MasteryService.ORANGE.equals(color)) {
            text = (first == null ? "" : first + "，") + "补练后补回来了。这里曾经错过，复习时多留意。";
            action = "extend";
        } else if (escalated) {
            text = "两轮补练还没补回来。先回去看看资料，再去 AI 对话把它讲一遍。";
            action = "dialogue";
        } else {
            text = (first == null ? "" : first + "，") + "还没掌握"
                    + (misconception == null ? "" : "。你可能是这样想的：" + misconception) + "。先看资料，再做几道补练题。";
            action = "remedy";
            boolean open = !rounds.isEmpty() && RemedyRound.OPEN.equals(rounds.get(rounds.size() - 1).getStatus());
            plan.add(new PlanStep("remedy", rounds.isEmpty() ? "做 3 道根据你的错题出的新题（在小助手里做）"
                    : "继续补练（第 " + Math.min(rounds.size() + (open ? 0 : 1), RemedyService.MAX_ROUNDS) + " 轮）",
                    null, null));
        }
        return new PointReport(kp.getId(), kp.getName(), level, mastery,
                m == null ? null : m.getFirstMastery(), status, rounds.size(), escalated,
                m == null || m.getAttemptCount() == null ? 0 : m.getAttemptCount(), misconception,
                color, text, action, weak ? pr.details() : List.of(), plan, dialogue);
    }
}
