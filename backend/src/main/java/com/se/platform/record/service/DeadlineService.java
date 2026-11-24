package com.se.platform.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.intervention.dto.InterventionDtos.InterventionView;
import com.se.platform.intervention.service.InterventionService;
import com.se.platform.record.dto.DeadlineDtos.ItemDeadline;
import com.se.platform.record.dto.DeadlineDtos.Student;
import com.se.platform.record.dto.DeadlineDtos.StudentDeadline;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 截止时间：练习和 AI 对话可以设截止时间（chapter_item.due_at）。
 * <ul>
 *   <li>学生：还没做的项，按 已过期 → 快到期 → 还早 列出来（学生首页「快到期」）</li>
 *   <li>学生还会看到老师布置的干预任务的期限（干预开始执行时定，默认 7 天）</li>
 *   <li>老师：自己课程里每一项有多少人做了、几个人迟交、谁还没做（老师首页「截止情况」）</li>
 * </ul>
 * 只算学生看得到的项：课程已发布、章节已发布。
 *
 * <p>"做了"取最早的一次完成时间（定义见 CompletionService）。晚于截止时间就算迟交。
 */
@Service
public class DeadlineService {

    public static final String OVERDUE = "overdue";
    public static final String SOON = "soon";
    public static final String LATER = "later";

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final CompletionService completionService;
    private final UserMapper userMapper;
    private final int remindDays;
    private final InterventionService interventionService;

    /** 干预类型 → 学生看到的名字 */
    private static final Map<String, String> TASK_LABELS = Map.of(
            "PRACTICE", "变式练习", "REVIEW", "复习 + 重做对话", "REVIEW_PRACTICE", "先复习再练习");

    public DeadlineService(CourseMapper courseMapper, ChapterMapper chapterMapper, ChapterItemMapper itemMapper,
                           CompletionService completionService, UserMapper userMapper,
                           InterventionService interventionService,
                           @Value("${platform.deadline.remind-days:3}") int remindDays) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.completionService = completionService;
        this.userMapper = userMapper;
        this.remindDays = remindDays;
        this.interventionService = interventionService;
    }

    /** 学生：还没做的、设了截止时间的项 */
    public List<StudentDeadline> mine(long studentId, LocalDateTime now) {
        Visible v = visible(null);
        Map<Long, Map<Long, LocalDateTime>> finished = finishedAt(v.items, List.of(studentId));
        List<StudentDeadline> list = new ArrayList<>();
        for (ChapterItem it : v.items) {
            if (finished.getOrDefault(it.getId(), Map.of()).containsKey(studentId)) {
                continue;
            }
            Chapter ch = v.chapters.get(it.getChapterId());
            Course c = v.courses.get(it.getCourseId());
            list.add(new StudentDeadline("item", it.getId(), null, c.getId(), c.getName(), ch.getId(), ch.getTitle(),
                    it.getType(),
                    it.getTitle(), it.getDueAt(), allowLate(it), state(it.getDueAt(), now),
                    Duration.between(now, it.getDueAt()).toMinutes()));
        }
        // 老师布置的干预任务：执行中的都有期限；过期后仍然可以做（补救练习不拦），只标出来
        for (InterventionView iv : interventionService.mine(studentId).running()) {
            if (iv.dueAt() == null) {
                continue;
            }
            list.add(new StudentDeadline("intervention", null, iv.id(), null, null, null, null, iv.type(),
                    "老师布置：" + TASK_LABELS.getOrDefault(iv.type(), "任务") + "（" + iv.knowledgePointName() + "）",
                    iv.dueAt(), true, state(iv.dueAt(), now), Duration.between(now, iv.dueAt()).toMinutes()));
        }
        list.sort(Comparator.comparing(StudentDeadline::dueAt));
        return list;
    }

    /** 老师：自己课程里设了截止时间的项，全班完成情况。已过期的在前，同一类按截止时间排 */
    public List<ItemDeadline> teacher(long teacherId, LocalDateTime now) {
        Visible v = visible(teacherId);
        List<User> students = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student").eq(User::getStatus, 1).orderByAsc(User::getId));
        Map<Long, Map<Long, LocalDateTime>> finished =
                finishedAt(v.items, students.stream().map(User::getId).toList());
        List<ItemDeadline> list = new ArrayList<>();
        for (ChapterItem it : v.items) {
            Map<Long, LocalDateTime> byStudent = finished.getOrDefault(it.getId(), Map.of());
            List<Student> missing = students.stream().filter(s -> !byStudent.containsKey(s.getId()))
                    .map(s -> new Student(s.getId(), s.getUsername(), s.getName())).toList();
            int late = (int) byStudent.values().stream().filter(t -> t.isAfter(it.getDueAt())).count();
            Chapter ch = v.chapters.get(it.getChapterId());
            Course c = v.courses.get(it.getCourseId());
            list.add(new ItemDeadline(it.getId(), c.getId(), c.getName(), ch.getId(), ch.getTitle(), it.getType(),
                    it.getTitle(), it.getDueAt(), allowLate(it), state(it.getDueAt(), now), students.size(),
                    byStudent.size(), late, missing));
        }
        List<String> order = List.of(OVERDUE, SOON, LATER);
        list.sort(Comparator.comparing((ItemDeadline d) -> order.indexOf(d.state())).thenComparing(ItemDeadline::dueAt));
        return list;
    }

    /** 已过期 / 快到期（离截止不到 remind-days 天）/ 还早 */
    public String state(LocalDateTime dueAt, LocalDateTime now) {
        if (now.isAfter(dueAt)) {
            return OVERDUE;
        }
        return now.plusDays(remindDays).isAfter(dueAt) ? SOON : LATER;
    }

    private static boolean allowLate(ChapterItem it) {
        return !Boolean.FALSE.equals(it.getAllowLate());
    }

    /** 学生看得到的、设了截止时间的项。teacherId 不为空时只取这位老师的课 */
    private Visible visible(Long teacherId) {
        Map<Long, Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, "published")
                        .eq(teacherId != null, Course::getTeacherId, teacherId))
                .stream().collect(Collectors.toMap(Course::getId, Function.identity()));
        if (courses.isEmpty()) {
            return new Visible(courses, Map.of(), List.of());
        }
        Map<Long, Chapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .in(Chapter::getCourseId, courses.keySet())
                        .eq(Chapter::getStatus, "published"))
                .stream().collect(Collectors.toMap(Chapter::getId, Function.identity()));
        if (chapters.isEmpty()) {
            return new Visible(courses, chapters, List.of());
        }
        List<ChapterItem> items = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                .in(ChapterItem::getChapterId, chapters.keySet())
                .isNotNull(ChapterItem::getDueAt));
        return new Visible(courses, chapters, items);
    }

    /** 每一项、每个学生最早一次完成的时间：itemId → (studentId → 时间)。"完成"的定义统一在 CompletionService */
    private Map<Long, Map<Long, LocalDateTime>> finishedAt(List<ChapterItem> items, List<Long> studentIds) {
        return completionService.finishedAt(items.stream().map(ChapterItem::getId).toList(), studentIds);
    }

    private record Visible(Map<Long, Course> courses, Map<Long, Chapter> chapters, List<ChapterItem> items) {
    }
}
