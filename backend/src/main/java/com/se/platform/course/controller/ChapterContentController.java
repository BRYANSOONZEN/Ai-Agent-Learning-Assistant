package com.se.platform.course.controller;

import com.se.platform.common.Result;
import com.se.platform.course.dto.ChapterItemSaveRequest;
import com.se.platform.course.dto.ChapterItemView;
import com.se.platform.course.dto.ExerciseSaveRequest;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.KnowledgePointSaveRequest;
import com.se.platform.course.dto.KnowledgePointView;
import com.se.platform.course.entity.Material;
import com.se.platform.course.service.ChapterItemService;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.course.service.KnowledgePointService;
import com.se.platform.course.service.MaterialService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 章节里的内容：内容项（/api/chapter-items）、知识点（/api/knowledge-points）、题目（/api/exercises），
 * 以及读文件（/api/materials/{id}/file）。列表接口按章节查，在 ChapterController 里；这里是新建、修改、删除。
 */
@RestController
public class ChapterContentController {

    private final ChapterItemService itemService;
    private final KnowledgePointService kpService;
    private final ExerciseService exerciseService;
    private final MaterialService materialService;

    public ChapterContentController(ChapterItemService itemService, KnowledgePointService kpService,
                                    ExerciseService exerciseService, MaterialService materialService) {
        this.itemService = itemService;
        this.kpService = kpService;
        this.exerciseService = exerciseService;
        this.materialService = materialService;
    }

    // ---------- 内容项 ----------

    /** POST /api/chapter-items  { chapterId, type, title, description } */
    @PostMapping("/api/chapter-items")
    public Result<ChapterItemView> createItem(@Valid @RequestBody ChapterItemSaveRequest req) {
        return Result.ok(itemService.create(req));
    }

    @PutMapping("/api/chapter-items/{id}")
    public Result<ChapterItemView> updateItem(@PathVariable Long id, @Valid @RequestBody ChapterItemSaveRequest req) {
        return Result.ok(itemService.update(id, req));
    }

    /** 删除内容项，连同里面的文件、题目、话题 */
    @DeleteMapping("/api/chapter-items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        itemService.delete(id);
        return Result.ok();
    }

    /** POST /api/chapter-items/{id}/file：multipart 字段 file，给视频 / 文档项上传或替换文件 */
    @PostMapping("/api/chapter-items/{id}/file")
    public Result<ChapterItemView> uploadItemFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.ok(itemService.uploadFile(id, file));
    }

    @DeleteMapping("/api/chapter-items/{id}/file")
    public Result<ChapterItemView> removeItemFile(@PathVariable Long id) {
        return Result.ok(itemService.removeFile(id));
    }

    // ---------- 知识点 ----------

    @PostMapping("/api/knowledge-points")
    public Result<KnowledgePointView> createKp(@Valid @RequestBody KnowledgePointSaveRequest req) {
        return Result.ok(kpService.create(req));
    }

    @PutMapping("/api/knowledge-points/{id}")
    public Result<KnowledgePointView> updateKp(@PathVariable Long id, @Valid @RequestBody KnowledgePointSaveRequest req) {
        return Result.ok(kpService.update(id, req));
    }

    @DeleteMapping("/api/knowledge-points/{id}")
    public Result<Void> deleteKp(@PathVariable Long id) {
        kpService.delete(id);
        return Result.ok();
    }

    // ---------- 题目 ----------

    @PostMapping("/api/exercises")
    public Result<ExerciseView> createExercise(@Valid @RequestBody ExerciseSaveRequest req) {
        return Result.ok(exerciseService.create(req));
    }

    @PutMapping("/api/exercises/{id}")
    public Result<ExerciseView> updateExercise(@PathVariable Long id, @Valid @RequestBody ExerciseSaveRequest req) {
        return Result.ok(exerciseService.update(id, req));
    }

    @DeleteMapping("/api/exercises/{id}")
    public Result<Void> deleteExercise(@PathVariable Long id) {
        exerciseService.delete(id);
        return Result.ok();
    }

    // ---------- 文件 ----------

    /**
     * GET /api/materials/{id}/file：返回文件本身（不是 JSON），inline 表示让浏览器直接打开预览。
     * 返回 Resource 时，Spring 会自动处理 Range 请求，视频可以拖动进度条。
     */
    @GetMapping("/api/materials/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        Material m = materialService.require(id);
        Resource resource = new FileSystemResource(materialService.file(m));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(m.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }
}
