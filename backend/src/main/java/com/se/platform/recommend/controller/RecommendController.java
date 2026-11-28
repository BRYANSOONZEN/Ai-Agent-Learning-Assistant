package com.se.platform.recommend.controller;

import com.se.platform.common.Result;
import com.se.platform.recommend.dto.RecommendDtos.RecommendationList;
import com.se.platform.recommend.dto.RecommendDtos.SaveRequest;
import com.se.platform.recommend.service.RecommendService;
import com.se.platform.record.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习推荐，前缀 /api/recommendations。学习推荐是小助手的延伸：分三类列出小助手给我出的补练题（系统补充）、
 * 拓展题，以及我在小助手里接收的老师推送。出题在小助手里；做题调小助手原来的提交接口。
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendController {

    private final RecommendService recommendService;
    private final RecordService recordService;

    public RecommendController(RecommendService recommendService, RecordService recordService) {
        this.recommendService = recommendService;
        this.recordService = recordService;
    }

    /** GET /api/recommendations：我的学习推荐（按时间从新到旧） */
    @GetMapping
    public Result<RecommendationList> mine() {
        return Result.ok(recommendService.student(recordService.currentStudentId()));
    }

    /** PUT /api/recommendations/extend/{roundId}/saved：拓展做完后保存 / 不保存 {saved: true} */
    @PutMapping("/extend/{roundId}/saved")
    public Result<Void> saveExtend(@PathVariable Long roundId, @RequestBody SaveRequest req) {
        recommendService.saveExtend(recordService.currentStudentId(), roundId, req.saved());
        return Result.ok();
    }
}
