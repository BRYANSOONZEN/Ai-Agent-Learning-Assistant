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
import com.se.platform.intervention.service.InterventionService;
import com.se.platform.intervention.service.LayerService;
import com.se.platform.intervention.service.LayerService.Application;
import com.se.platform.intervention.service.LayerService.Concept;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.LearningEventMapper;
import java.time.LocalDateTime;
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
 *   <li>学完后的个人总结：每个知识点用和干预相同的两层判断（LayerService），再加上反复出现的误解</li>
 * </ul>
 * 只读这个学生自己的数据；题目答案、话题要点、其他学生的数据都不碰。
 */
@Service
public class AssistantService {

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final KnowledgePointMapper kpMapper;
    private final LearningEventMapper eventMapper;
    private final LayerService layerService;
    private final InterventionService interventionService;
    private final PracticeAnalysis practiceAnalysis;

    public AssistantService(CourseMapper courseMapper, ChapterMapper chapterMapper, ChapterItemMapper itemMapper,
                            KnowledgePointMapper kpMapper, LearningEventMapper eventMapper,
                            LayerService layerService, InterventionService interventionService,
                            PracticeAnalysis practiceAnalysis) {
        this.practiceAnalysis = practiceAnalysis;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.kpMapper = kpMapper;
        this.eventMapper = eventMapper;
        this.layerService = layerService;
        this.interventionService = interventionService;
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
        List<PointReport> points = kps.stream().map(kp -> point(studentId, kp, ctx)).toList();
        int good = (int) points.stream().filter(p -> "good".equals(p.verdict())).count();
        int weak = (int) points.stream().filter(p -> isWeak(p.verdict())).count();
        String headline;
        if (points.isEmpty()) {
            headline = "「" + ch.getTitle() + "」你学完了。这一章还没有设置知识点，暂时没有更细的分析。";
        } else if (weak == 0) {
            int unsure = points.size() - good;   // 练得太少 + 数据不够
            headline = "「" + ch.getTitle() + "」" + (completed ? "你学完了，" : "根据你目前的练习，") + points.size() + " 个知识点"
                    + (good == points.size() ? "全部稳了，可以学下一章。"
                    : good == 0 ? "都还没有足够的数据判断，先把练习多做几道。"
                    : "里 " + good + " 个已经稳了，" + unsure + " 个还要多练几道才能判断。");
        } else {
            // 只说不为 0 的部分，例如"2 个知识点里 1 个还需要补，1 个再练几道确认一下"
            int few = (int) points.stream().filter(p -> "few".equals(p.verdict())).count();
            List<String> parts = new ArrayList<>();
            if (good > 0) {
                parts.add(good + " 个已经稳了");
            }
            parts.add(weak + " 个还需要补");
            if (few > 0) {
                parts.add(few + " 个再练几道确认一下");
            }
            headline = "「" + ch.getTitle() + "」" + (completed ? "你学完了。" : "根据你目前的练习：") + points.size()
                    + " 个知识点里 "
                    + String.join("，", parts) + "。";
        }
        return new ChapterReport(headline, good, weak, points);
    }

    static boolean isWeak(String verdict) {
        return "apply".equals(verdict) || "concept".equals(verdict) || "both".equals(verdict);
    }

    /** 一个知识点：两层判断 → 结论、一句话、建议做什么 */
    private PointReport point(long studentId, KnowledgePoint kp, PracticeAnalysis.Context ctx) {
        Concept concept = layerService.concept(studentId, kp.getId(), null);
        Application app = layerService.application(studentId, kp.getId());
        Mastery m = app.mastery();
        Double mastery = m == null ? null : m.getMastery();
        int attempts = m == null ? 0 : m.getAttemptCount();
        String misconception = interventionService.mainMisconception(studentId, kp.getId());

        String c = concept.state();
        String a = app.state();
        boolean conceptOk = LayerService.GOOD.equals(c) || LayerService.FAIR.equals(c);
        boolean skipped = concept.topics().stream().anyMatch(t -> "SKIPPED_IRRELEVANT".equals(t.result()));

        String saidInDialogue = LayerService.GOOD.equals(c) ? "对话里你自己讲出来了"
                : LayerService.FAIR.equals(c) ? "对话里你在提示后讲出来了"
                : LayerService.BAD.equals(c) ? (skipped ? "对话里这个话题你跳过了" : "对话里你还没能自己讲出来")
                : "这一章的对话没有考到它";
        String practice = m == null ? "还没做过练习"
                : LayerService.NONE.equals(a) ? "练习只做了 " + attempts + " 次"
                : String.format("练习掌握度 %.2f", mastery);
        String because = misconception == null ? "" : "，几次都是这个问题：" + misconception;

        String verdict;
        String text;
        String action;
        if (LayerService.BAD.equals(c) && LayerService.BAD.equals(a)) {
            verdict = "both";
            text = saidInDialogue + "，" + practice + because + "。先复习这一章，再和 AI 聊一次，然后做题。";
            action = "dialogue";
        } else if (LayerService.BAD.equals(c)) {
            verdict = "concept";
            text = practice.startsWith("练习掌握度") ? practice + "，不错；但" + saidInDialogue + "。先复习，再和 AI 聊一次。"
                    : saidInDialogue + "。先复习，再和 AI 聊一次。";
            action = "dialogue";
        } else if (LayerService.BAD.equals(a)) {
            verdict = "apply";
            text = (conceptOk ? saidInDialogue + "，但" : "") + practice + because + "。做几道题把它用熟。";
            action = "practice";
        } else if (LayerService.GOOD.equals(a)) {
            verdict = "good";
            text = (conceptOk ? saidInDialogue + "，" : "") + practice + "，已经稳了。";
            action = null;
        } else if (conceptOk) {
            verdict = "few";
            text = saidInDialogue + "，" + practice + "，再做几道确认一下。";
            action = "practice";
        } else {
            verdict = "none";
            text = "还没有足够的数据判断：" + saidInDialogue + "，" + practice + "。多做几道题就能看出来。";
            action = "practice";   // 练习不够 3 次时，多做题是唯一能补上数据的办法
        }
        PracticeAnalysis.Result pr = practiceAnalysis.analyse(studentId, kp, ctx, !"good".equals(verdict),
                "concept".equals(verdict) || "both".equals(verdict));
        if (misconception == null && pr.misconception() != null && !"good".equals(verdict)) {
            misconception = pr.misconception();
        }
        return new PointReport(kp.getId(), kp.getName(), c, a, mastery, attempts, misconception, verdict, text,
                action, pr.details(), pr.plan());
    }
}
