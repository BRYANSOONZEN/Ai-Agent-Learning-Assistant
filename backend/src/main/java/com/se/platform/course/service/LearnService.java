package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.common.BusinessException;
import com.se.platform.common.PageResult;
import com.se.platform.course.dto.CourseView;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.LearnChapter;
import com.se.platform.course.dto.LearnDtos.LearnCourse;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.dto.LearnDtos.QuizQuestion;
import com.se.platform.course.dto.LearnDtos.QuizResult;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.record.service.CompletionService;
import com.se.platform.record.service.ProgressService;
import com.se.platform.record.service.RecordService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 学生学习：只能看到已发布的课程和已发布的章节。
 * 交卷时判对错、给讲解，每题的作答存进学习记录（RecordService），再重算相关知识点的掌握度（MasteryService）。
 */
@Service
public class LearnService {

    private final CourseService courseService;
    private final ChapterMapper chapterMapper;
    private final ChapterItemService itemService;
    private final ExerciseService exerciseService;
    private final RecordService recordService;
    private final MasteryService masteryService;
    private final ChapterItemMapper itemMapper;
    private final CompletionService completionService;
    private final ProgressService progressService;
    private final ShortAnswerJudge shortJudge;

    public LearnService(CourseService courseService, ChapterMapper chapterMapper,
                        ChapterItemService itemService, ExerciseService exerciseService,
                        RecordService recordService, MasteryService masteryService,
                        ChapterItemMapper itemMapper, CompletionService completionService,
                        ProgressService progressService, ShortAnswerJudge shortJudge) {
        this.courseService = courseService;
        this.chapterMapper = chapterMapper;
        this.itemService = itemService;
        this.exerciseService = exerciseService;
        this.recordService = recordService;
        this.masteryService = masteryService;
        this.itemMapper = itemMapper;
        this.completionService = completionService;
        this.progressService = progressService;
        this.shortJudge = shortJudge;
    }

    public PageResult<CourseView> courses(int page, int size, String keyword) {
        return courseService.pagePublished(page, size, keyword);
    }

    /** 学习页的大纲：已发布的章节，每章的内容项按学习顺序 */
    public LearnCourse course(Long courseId) {
        Course course = courseService.require(courseId);
        if (!"published".equals(course.getStatus())) {
            throw BusinessException.notFound("课程");
        }
        List<LearnChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getCourseId, courseId)
                        .eq(Chapter::getStatus, "published")
                        .orderByAsc(Chapter::getSortOrder)
                        .orderByAsc(Chapter::getId))
                .stream()
                .map(ch -> new LearnChapter(ch.getId(), ch.getTitle(), ch.getIntro(), itemService.list(ch.getId())))
                .toList();
        long student = recordService.currentStudentId();
        List<Long> itemIds = chapters.stream().flatMap(ch -> ch.items().stream()).map(it -> it.id()).toList();
        Set<Long> done = completionService.doneItemIds(student, itemIds);
        Set<Long> locked = completionService.lockedItemIds(done,
                chapters.stream().map(ch -> ch.items().stream().map(it -> it.id()).toList()).toList());
        var progress = itemIds.isEmpty() ? java.util.Map.<Long, com.se.platform.record.dto.ProgressDtos.ProgressView>of()
                : progressService.views(student, itemMapper.selectBatchIds(itemIds));
        return new LearnCourse(courseService.get(courseId), chapters,
                itemIds.stream().filter(done::contains).toList(),
                itemIds.stream().filter(locked::contains).toList(), progress);
    }

    /** 一次练习的题目，不带答案 */
    public List<QuizQuestion> questions(Long itemId) {
        ChapterItem item = itemService.require(itemId);
        completionService.requireUnlocked(recordService.currentStudentId(), item);
        return exercises(item).stream()
                .map(e -> new QuizQuestion(e.id(), e.type(), e.stem(), e.options(), e.reasons())).toList();
    }

    /** 判卷：没作答的题算错；客观题程序判，简答题交给 ShortAnswerJudge（大模型判两次） */
    public QuizResult submit(Long itemId, Map<Long, String> answers) {
        ChapterItem item = itemService.require(itemId);
        ChapterItemService.requireOpen(item, LocalDateTime.now());
        completionService.requireUnlocked(recordService.currentStudentId(), item);
        List<QuestionResult> results = new ArrayList<>();
        int correct = 0;
        for (ExerciseView e : exercises(item)) {
            String a = answers == null ? null : answers.get(e.id());
            QuestionResult r = Grader.SHORT.equals(e.type()) ? shortJudge.judge(e, a) : grade(e, a);
            if (r.correct()) {
                correct++;
            }
            results.add(r);
        }
        recordService.saveAnswers(item, results);
        var changes = masteryService.update(recordService.currentStudentId(),
                results.stream().map(QuestionResult::id).toList());
        return new QuizResult(results.size(), correct, results, changes);
    }

    /** 判一道客观题（学习推荐也用）；选错了就带上这个错误选项的误解说明 */
    public static QuestionResult grade(ExerciseView e, String chosen) {
        return Grader.grade(e, chosen);
    }

    /** 练习项的题目（带答案，只在服务端用）。章节没发布时学生看不到 */
    private List<ExerciseView> exercises(ChapterItem item) {
        if (!"exercise".equals(item.getType())) {
            throw BusinessException.badRequest("这一项不是练习");
        }
        Chapter chapter = chapterMapper.selectById(item.getChapterId());
        if (chapter == null || !"published".equals(chapter.getStatus())) {
            throw BusinessException.notFound("练习");
        }
        return exerciseService.list(item.getChapterId(), item.getId()).stream().filter(e -> !e.draft()).toList();
    }
}
