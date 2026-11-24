package com.se.platform.course.controller;

import com.se.platform.common.Result;
import com.se.platform.course.dto.ExerciseToolDtos.CoverageRow;
import com.se.platform.course.dto.ExerciseToolDtos.GenRequest;
import com.se.platform.course.dto.ExerciseToolDtos.GenResult;
import com.se.platform.course.dto.ExerciseToolDtos.ReviewRequest;
import com.se.platform.course.dto.ExerciseToolDtos.ShortAnswerView;
import com.se.platform.course.service.ExerciseGenService;
import com.se.platform.course.service.ExerciseReviewService;
import com.se.platform.course.service.ExerciseService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 练习的出题工具（老师）：AI 生成、上传识别、确认草稿、覆盖检查、简答改判。
 * 设计见 docs/模块说明/练习与掌握度-设计.md。
 */
@RestController
public class ExerciseToolController {

    private final ExerciseGenService gen;
    private final ExerciseReviewService review;
    private final ExerciseService exercises;

    public ExerciseToolController(ExerciseGenService gen, ExerciseReviewService review, ExerciseService exercises) {
        this.gen = gen;
        this.review = review;
        this.exercises = exercises;
    }

    /** POST /api/exercises/generate {itemId, kpIds, assessTypes, types, perKp}：AI 按知识点出题，存成草稿 */
    @PostMapping("/api/exercises/generate")
    public Result<GenResult> generate(@RequestBody GenRequest req) {
        return Result.ok(gen.generate(req));
    }

    /** POST /api/exercises/import（表单：itemId、file 或 text）：识别老师已有的题目，存成草稿 */
    @PostMapping("/api/exercises/import")
    public Result<GenResult> importQuestions(@RequestParam Long itemId,
                                             @RequestParam(required = false) MultipartFile file,
                                             @RequestParam(required = false) String text) {
        return Result.ok(gen.importQuestions(itemId, file, text));
    }

    /** POST /api/exercises/confirm?itemId=：确认这次练习里的草稿题，学生从此看得到 */
    @PostMapping("/api/exercises/confirm")
    public Result<Map<String, Integer>> confirm(@RequestParam Long itemId) {
        return Result.ok(Map.of("confirmed", exercises.confirmDrafts(itemId)));
    }

    /** GET /api/chapters/{id}/exercise-coverage：每个知识点在每种考查类型、题型下有几道题 */
    @GetMapping("/api/chapters/{id}/exercise-coverage")
    public Result<List<CoverageRow>> coverage(@PathVariable Long id) {
        return Result.ok(review.coverage(id));
    }

    /** GET /api/exercises/short-answers?itemId=：这次练习里简答题的作答，待确认的在前 */
    @GetMapping("/api/exercises/short-answers")
    public Result<List<ShortAnswerView>> shortAnswers(@RequestParam Long itemId) {
        return Result.ok(review.shortAnswers(itemId));
    }

    /** PUT /api/exercises/short-answers/{recordId} {hits: [true, false]}：老师确认或改判 */
    @PutMapping("/api/exercises/short-answers/{recordId}")
    public Result<ShortAnswerView> reviewShort(@PathVariable Long recordId, @RequestBody ReviewRequest req) {
        return Result.ok(review.review(recordId, req == null ? null : req.hits()));
    }
}
