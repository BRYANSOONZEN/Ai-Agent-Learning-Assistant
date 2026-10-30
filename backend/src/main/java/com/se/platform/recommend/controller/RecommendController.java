package com.se.platform.recommend.controller;

import com.se.platform.common.Result;
import com.se.platform.course.dto.LearnDtos.QuizResult;
import com.se.platform.recommend.dto.RecommendDtos.FeedbackRequest;
import com.se.platform.recommend.dto.RecommendDtos.PracticeRequest;
import com.se.platform.recommend.dto.RecommendDtos.RecommendationList;
import com.se.platform.recommend.service.RecommendService;
import com.se.platform.record.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习推荐，前缀 /api/recommendations（docs/api.md 第 6 节）。
 * 打开时按最新掌握度挑题（相当于文档里的 generate），登录没做，都是演示学生。
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

    /** GET /api/recommendations：我的推荐，按薄弱知识点分组 */
    @GetMapping
    public Result<RecommendationList> mine() {
        return Result.ok(recommendService.student(recordService.currentStudentId()));
    }

    /** POST /api/recommendations/practice：做推荐的题 {answers: {"3": "A"}}，返回和交卷一样的结果 */
    @PostMapping("/practice")
    public Result<QuizResult> practice(@RequestBody PracticeRequest req) {
        return Result.ok(recommendService.practice(recordService.currentStudentId(), req.answers()));
    }

    /** POST /api/recommendations/{id}/feedback：{status: "done" | "dismissed"} */
    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable Long id, @RequestBody FeedbackRequest req) {
        recommendService.feedback(recordService.currentStudentId(), id, req.status());
        return Result.ok();
    }
}
