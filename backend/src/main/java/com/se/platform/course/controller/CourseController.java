package com.se.platform.course.controller;

import com.se.platform.common.PageResult;
import com.se.platform.common.Result;
import com.se.platform.course.dto.ChapterView;
import com.se.platform.course.dto.CourseSaveRequest;
import com.se.platform.course.dto.CourseView;
import com.se.platform.course.dto.OrderRequest;
import com.se.platform.course.service.ChapterService;
import com.se.platform.course.service.CourseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程接口，前缀 /api/courses（docs/api.md 3.1）。
 * 登录和权限还没做，暂时不检查角色，也不按"学生只看已发布"过滤。
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final ChapterService chapterService;

    public CourseController(CourseService courseService, ChapterService chapterService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
    }

    @GetMapping
    public Result<PageResult<CourseView>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String keyword) {
        return Result.ok(courseService.page(page, size, keyword));
    }

    /** GET /api/courses/mine：当前老师自己的课程（老师端「课程管理」） */
    @GetMapping("/mine")
    public Result<PageResult<CourseView>> mine(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String keyword) {
        return Result.ok(courseService.pageMine(page, size, keyword));
    }

    /** GET /api/courses/mine/{id}：老师进入建课页，不是自己的课返回 403 */
    @GetMapping("/mine/{id}")
    public Result<CourseView> getMine(@PathVariable Long id) {
        return Result.ok(courseService.getOwned(id));
    }

    @GetMapping("/{id}")
    public Result<CourseView> get(@PathVariable Long id) {
        return Result.ok(courseService.get(id));
    }

    @PostMapping
    public Result<CourseView> create(@Valid @RequestBody CourseSaveRequest req) {
        return Result.ok(courseService.create(req));
    }

    @PutMapping("/{id}")
    public Result<CourseView> update(@PathVariable Long id, @Valid @RequestBody CourseSaveRequest req) {
        return Result.ok(courseService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.ok();
    }

    /** GET /api/courses/{id}/chapters：本课程的章节，按顺序 */
    @GetMapping("/{id}/chapters")
    public Result<List<ChapterView>> chapters(@PathVariable Long id) {
        return Result.ok(chapterService.list(id));
    }

    /** PUT /api/courses/{id}/chapters/order：调整章节顺序 */
    @PutMapping("/{id}/chapters/order")
    public Result<List<ChapterView>> reorder(@PathVariable Long id, @Valid @RequestBody OrderRequest req) {
        return Result.ok(chapterService.reorder(id, req.ids()));
    }
}
