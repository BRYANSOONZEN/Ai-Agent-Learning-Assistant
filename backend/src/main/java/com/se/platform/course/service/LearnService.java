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
import com.se.platform.record.service.RecordService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public LearnService(CourseService courseService, ChapterMapper chapterMapper,
                        ChapterItemService itemService, ExerciseService exerciseService,
                        RecordService recordService, MasteryService masteryService) {
        this.courseService = courseService;
        this.chapterMapper = chapterMapper;
        this.itemService = itemService;
        this.exerciseService = exerciseService;
        this.recordService = recordService;
        this.masteryService = masteryService;
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
        return new LearnCourse(courseService.get(courseId), chapters, recordService.doneItemIds(courseId));
    }

    /** 一次练习的题目，不带答案 */
    public List<QuizQuestion> questions(Long itemId) {
        return exercises(itemService.require(itemId)).stream().map(e -> new QuizQuestion(e.id(), e.stem(), e.options())).toList();
    }

    /** 判卷：没作答的题算错 */
    public QuizResult submit(Long itemId, Map<Long, String> answers) {
        ChapterItem item = itemService.require(itemId);
        List<QuestionResult> results = new ArrayList<>();
        int correct = 0;
        for (ExerciseView e : exercises(item)) {
            QuestionResult r = grade(e, answers == null ? null : answers.get(e.id()));
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

    /** 判一道题；选错了就带上这个错误选项的误解说明，错题讲解要用 */
    public static QuestionResult grade(ExerciseView e, String chosen) {
        chosen = chosen == null ? null : chosen.trim().toUpperCase();
        boolean right = e.answer().equals(chosen);
        String note = null;
        if (!right && chosen != null && chosen.length() == 1) {
            int idx = chosen.charAt(0) - 'A';
            if (idx >= 0 && idx < e.optionNotes().size() && !e.optionNotes().get(idx).isBlank()) {
                note = e.optionNotes().get(idx);
            }
        }
        return new QuestionResult(e.id(), e.stem(), e.options(), chosen, e.answer(), right, e.analysis(), note);
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
        return exerciseService.list(item.getChapterId(), item.getId());
    }
}
