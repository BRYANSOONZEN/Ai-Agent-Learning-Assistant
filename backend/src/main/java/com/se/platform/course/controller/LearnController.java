package com.se.platform.course.controller;

import com.se.platform.common.PageResult;
import com.se.platform.common.Result;
import com.se.platform.course.dto.CourseView;
import com.se.platform.course.dto.LearnDtos.LearnCourse;
import com.se.platform.course.dto.LearnDtos.QuizQuestion;
import com.se.platform.course.dto.LearnDtos.QuizResult;
import com.se.platform.course.dto.LearnDtos.QuizSubmit;
import com.se.platform.course.service.LearnService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生学习的接口，前缀 /api/learn：课程列表、加入课程、学习页（大纲、学完和锁住的项）、取题、交卷。
 * 只返回已发布的课程和章节；题目在交卷前不带答案。
 */
@RestController
@RequestMapping("/api/learn")
public class LearnController {

    private final LearnService learnService;

    /** 选课：没加入这门课的学生不能学（字段注入，单元测试里直接 new 时为空） */
    @org.springframework.beans.factory.annotation.Autowired
    private com.se.platform.course.service.EnrollmentService enrollment;

    public LearnController(LearnService learnService) {
        this.learnService = learnService;
    }

    /** GET /api/learn/courses?page=1&size=12&keyword=：已发布的课程 */
    @GetMapping("/courses")
    public Result<PageResult<CourseView>> courses(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "12") int size,
                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(learnService.courses(page, size, keyword));
    }

    /** POST /api/learn/courses/{id}/join：加入这门课（选课），加入后才能学 */
    @PostMapping("/courses/{id}/join")
    public Result<Void> join(@PathVariable Long id) {
        enrollment.join(com.se.platform.common.CurrentUser.studentId(), id);
        return Result.ok();
    }

    /** GET /api/learn/enrollments：我加入了哪些课（课程 id） */
    @GetMapping("/enrollments")
    public Result<java.util.Set<Long>> enrollments() {
        return Result.ok(enrollment.courseIds(com.se.platform.common.CurrentUser.studentId()));
    }

    /** GET /api/learn/courses/{id}：学习页的大纲（已发布的章节 + 每章的内容项） */
    @GetMapping("/courses/{id}")
    public Result<LearnCourse> course(@PathVariable Long id) {
        return Result.ok(learnService.course(id));
    }

    /** GET /api/learn/items/{id}/questions：一次练习的题目，不带答案 */
    @GetMapping("/items/{id}/questions")
    public Result<List<QuizQuestion>> questions(@PathVariable Long id) {
        return Result.ok(learnService.questions(id));
    }

    /** POST /api/learn/items/{id}/submit  {"answers": {"1": "A", "2": "C"}}：交卷，返回对错和讲解 */
    @PostMapping("/items/{id}/submit")
    public Result<QuizResult> submit(@PathVariable Long id, @RequestBody QuizSubmit req) {
        return Result.ok(learnService.submit(id, req.answers()));
    }
}
