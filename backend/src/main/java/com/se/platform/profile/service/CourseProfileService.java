package com.se.platform.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.Grader;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.profile.dto.ProfileDtos.ChapterProfile;
import com.se.platform.profile.dto.ProfileDtos.CourseDimension;
import com.se.platform.profile.dto.ProfileDtos.CourseOption;
import com.se.platform.profile.dto.ProfileDtos.CourseProfile;
import com.se.platform.profile.dto.ProfileDtos.DayActivity;
import com.se.platform.profile.dto.ProfileDtos.PointColor;
import com.se.platform.profile.dto.ProfileDtos.Snapshot;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.mapper.LearningEventMapper;
import com.se.platform.record.service.CompletionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.se.platform.feynman.service.FeynmanDiagnosis;
import com.se.platform.profile.entity.ProfileSnapshot;
import com.se.platform.profile.mapper.ProfileSnapshotMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 学生画像（docs/模块说明/学生画像-设计.md）：每章画像 + 课程四维。全部现算，不另外存表。
 * <ul>
 *   <li>每章：学习流程进度（含 AI 对话）、内容进度（不含）、练习交了几组、第一次作答全对率、知识点颜色（绿 / 橙 / 红）</li>
 *   <li>课程四维（全部章节学完才开放）：学习持续性、学习完成度、按时完成率、有效作答正确率</li>
 *   <li>AI 对话不进四维的任何分子分母，只算“流程有没有完成”</li>
 *   <li>没有分母的维度 value 为空并说明原因，不用 0 或 100 补</li>
 *   <li>各章合并时把分子、分母加起来再除，不平均百分比</li>
 * </ul>
 */
@Service
public class CourseProfileService {

    /** 学习持续性：看参考日在内的最近 14 天；有效学习日达到 7 天算满 */
    static final int WINDOW_DAYS = 14;
    static final int FULL_DAYS = 7;
    /** 一天看视频、文档累计多少秒算“有学习” */
    static final int MIN_SECONDS = 30;

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final KnowledgePointMapper kpMapper;
    private final MasteryMapper masteryMapper;
    private final AnswerRecordMapper answerMapper;
    private final LearningEventMapper eventMapper;
    private final CompletionService completion;
    private final FeynmanDiagnosis feynmanDiagnosis;
    private final ProfileSnapshotMapper snapshotMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public CourseProfileService(CourseMapper courseMapper, ChapterMapper chapterMapper, ChapterItemMapper itemMapper,
                                KnowledgePointMapper kpMapper,
                                MasteryMapper masteryMapper, AnswerRecordMapper answerMapper,
                                LearningEventMapper eventMapper, CompletionService completion,
                                FeynmanDiagnosis feynmanDiagnosis, ProfileSnapshotMapper snapshotMapper,
                                com.fasterxml.jackson.databind.ObjectMapper json) {
        this.feynmanDiagnosis = feynmanDiagnosis;
        this.snapshotMapper = snapshotMapper;
        this.json = json;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.kpMapper = kpMapper;
        this.masteryMapper = masteryMapper;
        this.answerMapper = answerMapper;
        this.eventMapper = eventMapper;
        this.completion = completion;
    }

    /** 已发布的课程，带“学完几章” */
    public List<CourseOption> courses(long studentId) {
        List<CourseOption> out = new ArrayList<>();
        for (Course c : courseMapper.selectList(new LambdaQueryWrapper<Course>().eq(Course::getStatus, "published")
                .orderByAsc(Course::getId))) {
            Scope sc = scope(c.getId());
            Set<Long> done = completion.doneItemIds(studentId, sc.allItemIds());
            int chaptersDone = (int) sc.items.values().stream()
                    .filter(list -> !list.isEmpty() && list.stream().allMatch(i -> done.contains(i.getId()))).count();
            out.add(new CourseOption(c.getId(), c.getName(), chaptersDone, sc.chapters.size()));
        }
        return out;
    }

    public CourseProfile profile(long studentId, String studentName, Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || !"published".equals(course.getStatus())) {
            throw BusinessException.notFound("课程");
        }
        Scope sc = scope(courseId);
        Map<Long, LocalDateTime> finished = new HashMap<>();
        completion.finishedAt(sc.allItemIds(), List.of(studentId))
                .forEach((itemId, byStudent) -> finished.put(itemId, byStudent.get(studentId)));
        finished.values().removeIf(Objects::isNull);

        // 每章
        List<ChapterProfile> chapterProfiles = new ArrayList<>();
        int chaptersDone = 0;
        for (Chapter ch : sc.chapters) {
            ChapterProfile cp = chapter(studentId, ch, sc.items.get(ch.getId()), finished);
            chapterProfiles.add(cp);
            if (cp.completed()) {
                chaptersDone++;
            }
        }
        boolean open = !sc.chapters.isEmpty() && chaptersDone == sc.chapters.size();
        LocalDateTime lastDone = finished.values().stream().max(LocalDateTime::compareTo).orElse(null);
        LocalDate refDate = open && lastDone != null ? lastDone.toLocalDate() : LocalDate.now();

        List<DayActivity> days = new ArrayList<>();
        List<CourseDimension> dims = List.of(
                consistency(studentId, courseId, sc, refDate, days),
                completionRate(sc, finished),
                onTime(studentId, sc),
                accuracy(studentId, sc));
        return new CourseProfile(courseId, course.getName(), studentName, open, chaptersDone, sc.chapters.size(),
                refDate, LocalDateTime.now(), dims, chapterProfiles, days, snapshot(studentId, courseId, open, refDate, dims));
    }

    // ------------------------------------------------------------------ 学完时的快照

    /**
     * 全部章节学完后第一次打开课程画像时，把当时的四维存一份（每人每门课一份，之后不再改）；以后再打开都带上这一份。
     * 平时的画像照常现算：老师之后加了新章节，学生没学完时 open 变回 false，但快照还在。
     */
    private Snapshot snapshot(long studentId, Long courseId, boolean open, LocalDate refDate, List<CourseDimension> dims) {
        ProfileSnapshot saved = snapshotMapper.selectOne(new LambdaQueryWrapper<ProfileSnapshot>()
                .eq(ProfileSnapshot::getStudentId, studentId).eq(ProfileSnapshot::getCourseId, courseId));
        try {
            if (saved == null && open) {
                saved = new ProfileSnapshot();
                saved.setStudentId(studentId);
                saved.setCourseId(courseId);
                saved.setData(json.writeValueAsString(new Snapshot(null, refDate, dims)));
                saved.setCreatedAt(LocalDateTime.now());
                snapshotMapper.insert(saved);
            }
            if (saved == null) {
                return null;
            }
            Snapshot s = json.readValue(saved.getData(), Snapshot.class);
            return new Snapshot(saved.getCreatedAt(), s.refDate(), s.dimensions());
        } catch (Exception e) {
            throw new IllegalStateException("画像快照读写失败", e);
        }
    }

    // ------------------------------------------------------------------ 每章

    private ChapterProfile chapter(long studentId, Chapter ch, List<ChapterItem> items,
                                   Map<Long, LocalDateTime> finished) {
        int flowDone = (int) items.stream().filter(i -> finished.containsKey(i.getId())).count();
        List<ChapterItem> content = items.stream().filter(CourseProfileService::isContent).toList();
        int contentDone = (int) content.stream().filter(i -> finished.containsKey(i.getId())).count();
        List<ChapterItem> exercises = items.stream().filter(i -> "exercise".equals(i.getType())).toList();
        int submitted = (int) exercises.stream().filter(i -> answerMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getStudentId, studentId).eq(AnswerRecord::getItemId, i.getId())) > 0).count();
        int[] acc = firstAttemptAccuracy(studentId, exercises.stream().map(ChapterItem::getId).toList());

        List<PointColor> points = new ArrayList<>();
        Map<Long, String> dialogue = feynmanDiagnosis.byKnowledgePoint(studentId, ch.getId());
        for (KnowledgePoint kp : kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getChapterId, ch.getId()).orderByAsc(KnowledgePoint::getSortOrder)
                .orderByAsc(KnowledgePoint::getId))) {
            Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                    .eq(Mastery::getStudentId, studentId).eq(Mastery::getKnowledgePointId, kp.getId()));
            points.add(new PointColor(kp.getId(), kp.getName(), MasteryService.color(m),
                    m == null ? null : m.getFirstMastery(), m == null ? null : m.getRemedyStatus(),
                    dialogue.get(kp.getId())));
        }
        boolean completed = !items.isEmpty() && flowDone == items.size();
        long red = points.stream().filter(p -> MasteryService.RED.equals(p.color())).count();
        long orange = points.stream().filter(p -> MasteryService.ORANGE.equals(p.color())).count();
        long green = points.stream().filter(p -> MasteryService.GREEN.equals(p.color())).count();
        String summary = items.isEmpty() ? "这一章内容待配置。"
                : String.format("学习流程完成 %d/%d 项（AI 对话只算完成状态）；内容完成 %d/%d 项；练习交了 %d/%d 组。",
                flowDone, items.size(), contentDone, content.size(), submitted, exercises.size())
                + (acc[1] == 0 ? "" : String.format("第一次作答 %d/%d 题全对%s。", acc[0], acc[1],
                acc[2] > 0 ? "，另有 " + acc[2] + " 题等老师确认" : ""))
                + (points.isEmpty() ? "" : String.format("知识点：%d 个第一次就掌握，%d 个补回来了，%d 个还没掌握。",
                green, orange, red));
        return new ChapterProfile(ch.getId(), ch.getTitle(), completed, flowDone, items.size(), contentDone,
                content.size(), submitted, exercises.size(), acc[0], acc[1], acc[2], points, summary);
    }

    /** 这几次练习里每道题的第一次作答：[全对题数, 已判题数, 等老师确认题数] */
    private int[] firstAttemptAccuracy(long studentId, List<Long> itemIds) {
        int[] r = new int[3];
        if (itemIds.isEmpty()) {
            return r;
        }
        Map<Long, AnswerRecord> first = new LinkedHashMap<>();
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId).in(AnswerRecord::getItemId, itemIds)
                        .orderByAsc(AnswerRecord::getSubmittedAt).orderByAsc(AnswerRecord::getId))
                .forEach(a -> first.putIfAbsent(a.getExerciseId(), a));
        for (AnswerRecord a : first.values()) {
            if (Grader.PENDING.equals(a.getStatus())) {
                r[2]++;
                continue;
            }
            r[1]++;
            double score = a.getScore() != null ? a.getScore() : Boolean.TRUE.equals(a.getIsCorrect()) ? 1 : 0;
            if (score >= 1) {
                r[0]++;
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ 四维

    /**
     * 学习持续性 = min(1, 有效学习日 ÷ 7)，看参考日在内的最近 14 天。
     * 有效学习日：这一天看视频、文档累计 ≥ 30 秒（各章先加起来再比），或者交过 ≥ 1 道已判的题。AI 对话不算。
     */
    CourseDimension consistency(long studentId, Long courseId, Scope sc, LocalDate refDate, List<DayActivity> days) {
        LocalDate from = refDate.minusDays(WINDOW_DAYS - 1);
        Set<Long> contentIds = sc.items.values().stream().flatMap(List::stream)
                .filter(i -> "video".equals(i.getType()) || "document".equals(i.getType()))
                .map(ChapterItem::getId).collect(Collectors.toSet());
        Map<LocalDate, Integer> seconds = new HashMap<>();
        if (!contentIds.isEmpty()) {
            eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                            .eq(LearningEvent::getStudentId, studentId).eq(LearningEvent::getEventType, LearningEvent.VIEW_ITEM)
                            .in(LearningEvent::getTargetId, contentIds)
                            .ge(LearningEvent::getCreatedAt, from.atStartOfDay())
                            .lt(LearningEvent::getCreatedAt, refDate.plusDays(1).atStartOfDay()))
                    .forEach(e -> seconds.merge(e.getCreatedAt().toLocalDate(),
                            e.getDurationSec() == null ? 0 : e.getDurationSec(), Integer::sum));
        }
        Map<LocalDate, Integer> answers = new HashMap<>();
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId).eq(AnswerRecord::getCourseId, courseId)
                        .ge(AnswerRecord::getSubmittedAt, from.atStartOfDay())
                        .lt(AnswerRecord::getSubmittedAt, refDate.plusDays(1).atStartOfDay()))
                .stream().filter(a -> !Grader.PENDING.equals(a.getStatus()))
                .forEach(a -> answers.merge(a.getSubmittedAt().toLocalDate(), 1, Integer::sum));
        int active = 0;
        for (LocalDate d = from; !d.isAfter(refDate); d = d.plusDays(1)) {
            int sec = seconds.getOrDefault(d, 0);
            int n = answers.getOrDefault(d, 0);
            if (sec >= MIN_SECONDS || n >= 1) {
                active++;
            }
            days.add(new DayActivity(d, sec / 60, n));
        }
        return new CourseDimension("consistency", "学习持续性", round(Math.min(1.0, (double) active / FULL_DAYS)),
                active, FULL_DAYS, "min(1, 有效学习日 ÷ 7)，近 14 天",
                "页面停留记录（视频、文档）、作答时间；AI 对话不算",
                String.format("%s 前 14 天里有 %d 天有效学习（看视频、文档 ≥ 30 秒或交过题）", refDate, active));
    }

    /** 学习完成度 = 完成的视频、文档、练习项 ÷ 全部视频、文档、练习项（AI 对话不算） */
    CourseDimension completionRate(Scope sc, Map<Long, LocalDateTime> finished) {
        List<ChapterItem> content = sc.items.values().stream().flatMap(List::stream)
                .filter(CourseProfileService::isContent).toList();
        int done = (int) content.stream().filter(i -> finished.containsKey(i.getId())).count();
        return new CourseDimension("completion", "学习完成度", content.isEmpty() ? null : round((double) done / content.size()),
                done, content.size(), "完成的视频、文档、练习 ÷ 全部视频、文档、练习",
                "完成判定：视频看到 90%、文档读满 30 秒并点已读完、练习交卷",
                content.isEmpty() ? "这门课没有可统计的内容" : String.format("完成 %d/%d 项", done, content.size()));
    }

    /** 按时完成率 = 截止前第一次交卷的练习 ÷ 已经过了截止时间的练习 */
    CourseDimension onTime(long studentId, Scope sc) {
        LocalDateTime now = LocalDateTime.now();
        List<ChapterItem> due = sc.items.values().stream().flatMap(List::stream)
                .filter(i -> "exercise".equals(i.getType()) && i.getDueAt() != null && !i.getDueAt().isAfter(now))
                .toList();
        int ok = 0;
        for (ChapterItem i : due) {
            AnswerRecord first = answerMapper.selectOne(new LambdaQueryWrapper<AnswerRecord>()
                    .eq(AnswerRecord::getStudentId, studentId).eq(AnswerRecord::getItemId, i.getId())
                    .orderByAsc(AnswerRecord::getSubmittedAt).last("LIMIT 1"));
            if (first != null && !first.getSubmittedAt().isAfter(i.getDueAt())) {
                ok++;
            }
        }
        return new CourseDimension("onTime", "按时完成率", due.isEmpty() ? null : round((double) ok / due.size()),
                ok, due.size(), "截止前第一次交卷的练习 ÷ 已过截止时间的练习",
                "练习的截止时间、第一次交卷时间",
                due.isEmpty() ? "还没有到截止时间的练习" : String.format("%d 次练习里 %d 次按时交", due.size(), ok));
    }

    /** 有效作答正确率 = 第一次作答全对的题数 ÷ 已判的题数（等老师确认的简答不算） */
    CourseDimension accuracy(long studentId, Scope sc) {
        List<Long> exerciseItems = sc.items.values().stream().flatMap(List::stream)
                .filter(i -> "exercise".equals(i.getType())).map(ChapterItem::getId).toList();
        int[] acc = firstAttemptAccuracy(studentId, exerciseItems);
        return new CourseDimension("accuracy", "有效作答正确率", acc[1] == 0 ? null : round((double) acc[0] / acc[1]),
                acc[0], acc[1], "第一次作答全对的题数 ÷ 已判的题数",
                "练习的作答记录（每道题只看第一次；等老师确认的不算）",
                acc[1] == 0 ? "还没有已判的作答" : String.format("第一次作答 %d/%d 题全对%s", acc[0], acc[1],
                        acc[2] > 0 ? "，" + acc[2] + " 题等老师确认" : ""));
    }

    // ------------------------------------------------------------------ 范围

    /** 统计范围：这门课已发布的章节和它们的内容项（按学习顺序） */
    record Scope(List<Chapter> chapters, Map<Long, List<ChapterItem>> items) {
        List<Long> allItemIds() {
            return items.values().stream().flatMap(List::stream).map(ChapterItem::getId).toList();
        }
    }

    Scope scope(Long courseId) {
        List<Chapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCourseId, courseId).eq(Chapter::getStatus, "published")
                .orderByAsc(Chapter::getSortOrder).orderByAsc(Chapter::getId));
        Map<Long, List<ChapterItem>> items = new LinkedHashMap<>();
        for (Chapter ch : chapters) {
            items.put(ch.getId(), itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                    .eq(ChapterItem::getChapterId, ch.getId())
                    .orderByAsc(ChapterItem::getSortOrder).orderByAsc(ChapterItem::getId)));
        }
        return new Scope(chapters, items);
    }

    /** 内容项：视频、文档、练习（AI 对话不算进完成度） */
    static boolean isContent(ChapterItem i) {
        return "video".equals(i.getType()) || "document".equals(i.getType()) || "exercise".equals(i.getType());
    }

    private static Double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
