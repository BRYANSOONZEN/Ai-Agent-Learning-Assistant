package com.se.platform.record.controller;

import com.se.platform.common.CurrentUser;
import com.se.platform.common.Result;
import com.se.platform.record.dto.ProgressDtos.ProgressView;
import com.se.platform.record.dto.ProgressDtos.ReadingReport;
import com.se.platform.record.dto.ProgressDtos.VideoReport;
import com.se.platform.record.service.ProgressService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 视频和文档的学习进度（学生），判定学完后解锁下一项。规则见 ProgressService */
@RestController
@RequestMapping("/api/learn/items/{id}")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /** POST /api/learn/items/{id}/video-progress {"duration": 600, "ranges": [[0, 12.5]], "position": 12.5} */
    @PostMapping("/video-progress")
    public Result<ProgressView> video(@PathVariable Long id, @RequestBody VideoReport req) {
        return Result.ok(progressService.video(CurrentUser.studentId(), id, req));
    }

    /** POST /api/learn/items/{id}/reading {"seconds": 10}：文档页面在前台又过了几秒 */
    @PostMapping("/reading")
    public Result<ProgressView> reading(@PathVariable Long id, @RequestBody ReadingReport req) {
        return Result.ok(progressService.reading(CurrentUser.studentId(), id, req));
    }

    /** POST /api/learn/items/{id}/finish-reading：学生点"我已读完"（读满 30 秒才行） */
    @PostMapping("/finish-reading")
    public Result<ProgressView> finishReading(@PathVariable Long id) {
        return Result.ok(progressService.finishReading(CurrentUser.studentId(), id));
    }
}
