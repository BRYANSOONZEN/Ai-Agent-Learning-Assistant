package com.se.platform.record.controller;

import com.se.platform.common.Result;
import com.se.platform.record.dto.RecordDtos.EventRequest;
import com.se.platform.record.dto.RecordDtos.RecordOverview;
import com.se.platform.record.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「记」的接口，和学习接口同一个前缀 /api/learn。
 * 答题记录不用单独调：交卷（POST /api/learn/items/{id}/submit）时自动存。
 */
@RestController
@RequestMapping("/api/learn")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    /** POST /api/learn/events  {"type": "view_item", "itemId": 1, "durationSec": 120}：记一条学习行为 */
    @PostMapping("/events")
    public Result<Void> event(@RequestBody EventRequest req) {
        recordService.logEvent(req.type(), req.itemId(), req.durationSec());
        return Result.ok();
    }

    /** GET /api/learn/records：学习记录页（总数、每门课进度、交卷、AI 对话、最近动态） */
    @GetMapping("/records")
    public Result<RecordOverview> records() {
        return Result.ok(recordService.overview(recordService.currentStudentId()));
    }
}
