package com.se.platform.course.controller;

import com.se.platform.common.Result;
import com.se.platform.course.dto.ChapterItemView;
import com.se.platform.course.dto.ChapterSaveRequest;
import com.se.platform.course.dto.ChapterView;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.KnowledgePointView;
import com.se.platform.course.dto.OrderRequest;
import com.se.platform.course.service.ChapterItemService;
import com.se.platform.course.service.ChapterService;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.course.service.KnowledgePointService;
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
 * 章节接口，前缀 /api/chapters（docs/api.md 3.2）。
 * 本章的资料、知识点、题目也从这里按章节查：GET /api/chapters/{id}/materials 等。
 */
@RestController
@RequestMapping("/api/chapters")
public class ChapterController {

    private final ChapterService chapterService;
    private final ChapterItemService itemService;
    private final KnowledgePointService kpService;
    private final ExerciseService exerciseService;

    public ChapterController(ChapterService chapterService, ChapterItemService itemService,
                             KnowledgePointService kpService, ExerciseService exerciseService) {
        this.chapterService = chapterService;
        this.itemService = itemService;
        this.kpService = kpService;
        this.exerciseService = exerciseService;
    }

    @GetMapping("/{id}")
    public Result<ChapterView> get(@PathVariable Long id) {
        return Result.ok(chapterService.get(id));
    }

    @PostMapping
    public Result<ChapterView> create(@Valid @RequestBody ChapterSaveRequest req) {
        return Result.ok(chapterService.create(req));
    }

    @PutMapping("/{id}")
    public Result<ChapterView> update(@PathVariable Long id, @Valid @RequestBody ChapterSaveRequest req) {
        return Result.ok(chapterService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chapterService.delete(id);
        return Result.ok();
    }

    /** GET /api/chapters/{id}/items：本章的内容项，按学习顺序 */
    @GetMapping("/{id}/items")
    public Result<List<ChapterItemView>> items(@PathVariable Long id) {
        return Result.ok(itemService.list(id));
    }

    /** PUT /api/chapters/{id}/items/order  { ids: [...] } */
    @PutMapping("/{id}/items/order")
    public Result<List<ChapterItemView>> reorderItems(@PathVariable Long id, @Valid @RequestBody OrderRequest req) {
        return Result.ok(itemService.reorder(id, req.ids()));
    }

    @GetMapping("/{id}/knowledge-points")
    public Result<List<KnowledgePointView>> knowledgePoints(@PathVariable Long id) {
        return Result.ok(kpService.list(id));
    }

    /** GET /api/chapters/{id}/exercises?itemId=：本章题目；带 itemId 只要这一次练习的 */
    @GetMapping("/{id}/exercises")
    public Result<List<ExerciseView>> exercises(@PathVariable Long id, @RequestParam(required = false) Long itemId) {
        return Result.ok(exerciseService.list(id, itemId));
    }
}
