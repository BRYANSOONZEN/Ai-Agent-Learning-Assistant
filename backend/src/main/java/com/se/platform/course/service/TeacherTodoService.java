package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.feynman.entity.FeynmanSetup;
import com.se.platform.feynman.mapper.FeynmanSetupMapper;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.entity.ChapterKpState;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.mapper.ChapterKpStateMapper;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 老师首页的“待你确认”：大模型做完了、要老师看一眼才生效的东西，按课程、章节列出来，点了直接去那一页。
 * <ul>
 *   <li>kp：一章的知识点识别完了，待确认（确认后出题和 AI 对话才能用）</li>
 *   <li>feynman：AI 对话草稿待确认（确认后学生才能对话）</li>
 *   <li>exercise：AI 出的题还是草稿（确认后学生才看得到）</li>
 *   <li>short：简答两次判分不一致，等老师确认（确认前不算进掌握度）</li>
 * </ul>
 */
@Service
public class TeacherTodoService {

    /** 一条待办。view：建课页要打开的那一项（kps 或 item:内容项 id） */
    public record Todo(String kind, Long courseId, String courseName, Long chapterId, String chapterTitle,
                       String view, String title, int count) {
    }

    private final CourseService courseService;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final ExerciseMapper exerciseMapper;
    private final ChapterKpStateMapper kpStateMapper;
    private final ChapterKpMapper kpMapper;
    private final FeynmanSetupMapper setupMapper;
    private final AnswerRecordMapper answerMapper;

    public TeacherTodoService(CourseService courseService, CourseMapper courseMapper, ChapterMapper chapterMapper,
                              ChapterItemMapper itemMapper, ExerciseMapper exerciseMapper,
                              ChapterKpStateMapper kpStateMapper, ChapterKpMapper kpMapper,
                              FeynmanSetupMapper setupMapper, AnswerRecordMapper answerMapper) {
        this.courseService = courseService;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.exerciseMapper = exerciseMapper;
        this.kpStateMapper = kpStateMapper;
        this.kpMapper = kpMapper;
        this.setupMapper = setupMapper;
        this.answerMapper = answerMapper;
    }

    public List<Todo> mine() {
        List<Todo> out = new ArrayList<>();
        Map<Long, Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, courseService.currentTeacherId()))
                .stream().collect(Collectors.toMap(Course::getId, Function.identity()));
        if (courses.isEmpty()) {
            return out;
        }
        Map<Long, Chapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .in(Chapter::getCourseId, courses.keySet()).orderByAsc(Chapter::getCourseId)
                        .orderByAsc(Chapter::getSortOrder))
                .stream().collect(Collectors.toMap(Chapter::getId, Function.identity(), (a, b) -> a,
                        java.util.LinkedHashMap::new));
        if (chapters.isEmpty()) {
            return out;
        }
        Map<Long, ChapterItem> items = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                        .in(ChapterItem::getChapterId, chapters.keySet()))
                .stream().collect(Collectors.toMap(ChapterItem::getId, Function.identity()));

        for (ChapterKpState s : kpStateMapper.selectList(new LambdaQueryWrapper<ChapterKpState>()
                .in(ChapterKpState::getChapterId, chapters.keySet()).eq(ChapterKpState::getStatus, ChapterKpState.DRAFT))) {
            int n = Math.toIntExact(kpMapper.selectCount(new LambdaQueryWrapper<ChapterKp>()
                    .eq(ChapterKp::getChapterId, s.getChapterId())));
            out.add(todo("kp", chapters.get(s.getChapterId()), courses, "kps", "知识点识别完了，待确认", n));
        }
        for (FeynmanSetup f : setupMapper.selectList(new LambdaQueryWrapper<FeynmanSetup>()
                .in(FeynmanSetup::getChapterId, chapters.keySet()).eq(FeynmanSetup::getStatus, FeynmanSetup.DRAFT))) {
            ChapterItem it = items.get(f.getItemId());
            if (it != null) {
                out.add(todo("feynman", chapters.get(it.getChapterId()), courses, "item:" + it.getId(),
                        "AI 对话草稿待确认：" + it.getTitle(), 1));
            }
        }
        if (!items.isEmpty()) {
            exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>()
                            .in(Exercise::getItemId, items.keySet()).eq(Exercise::getDraft, true))
                    .stream().collect(Collectors.groupingBy(Exercise::getItemId, java.util.TreeMap::new, Collectors.counting()))
                    .forEach((itemId, n) -> {
                        ChapterItem it = items.get(itemId);
                        out.add(todo("exercise", chapters.get(it.getChapterId()), courses, "item:" + itemId,
                                n + " 道题还是草稿：" + it.getTitle(), n.intValue()));
                    });
            answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                            .in(AnswerRecord::getItemId, items.keySet()).eq(AnswerRecord::getStatus, Grader.PENDING))
                    .stream().collect(Collectors.groupingBy(AnswerRecord::getItemId, java.util.TreeMap::new, Collectors.counting()))
                    .forEach((itemId, n) -> {
                        ChapterItem it = items.get(itemId);
                        out.add(todo("short", chapters.get(it.getChapterId()), courses, "item:" + itemId,
                                n + " 份简答等你确认：" + it.getTitle(), n.intValue()));
                    });
        }
        return out;
    }

    private static Todo todo(String kind, Chapter ch, Map<Long, Course> courses, String view, String title, int n) {
        Course c = courses.get(ch.getCourseId());
        return new Todo(kind, c.getId(), c.getName(), ch.getId(), ch.getTitle(), view, title, n);
    }
}
