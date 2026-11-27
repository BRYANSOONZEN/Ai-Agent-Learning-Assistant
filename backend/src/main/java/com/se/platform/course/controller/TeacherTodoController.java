package com.se.platform.course.controller;

import com.se.platform.common.Result;
import com.se.platform.course.service.TeacherTodoService;
import com.se.platform.course.service.TeacherTodoService.Todo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 老师首页的“待你确认”（知识点、AI 对话草稿、AI 出的题、简答） */
@RestController
public class TeacherTodoController {

    private final TeacherTodoService service;

    public TeacherTodoController(TeacherTodoService service) {
        this.service = service;
    }

    /** GET /api/teacher/todo：自己课程里等老师确认的东西，按课程、章节排 */
    @GetMapping("/api/teacher/todo")
    public Result<List<Todo>> todo() {
        return Result.ok(service.mine());
    }
}
