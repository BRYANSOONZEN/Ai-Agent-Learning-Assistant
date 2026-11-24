package com.se.platform.record.controller;

import com.se.platform.common.CurrentUser;
import com.se.platform.common.Result;
import com.se.platform.record.dto.DeadlineDtos.ItemDeadline;
import com.se.platform.record.dto.DeadlineDtos.StudentDeadline;
import com.se.platform.record.service.DeadlineService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 截止时间提醒。
 * GET /api/learn/deadlines：学生自己还没做、设了截止时间的项（学生接口）。
 * GET /api/teacher/deadlines：老师自己课程里每一项的完成情况和没做的名单（老师接口）。
 */
@RestController
public class DeadlineController {

    private final DeadlineService deadlineService;

    public DeadlineController(DeadlineService deadlineService) {
        this.deadlineService = deadlineService;
    }

    @GetMapping("/api/learn/deadlines")
    public Result<List<StudentDeadline>> mine() {
        return Result.ok(deadlineService.mine(CurrentUser.studentId(), LocalDateTime.now()));
    }

    @GetMapping("/api/teacher/deadlines")
    public Result<List<ItemDeadline>> teacher() {
        return Result.ok(deadlineService.teacher(CurrentUser.teacherId(), LocalDateTime.now()));
    }
}
