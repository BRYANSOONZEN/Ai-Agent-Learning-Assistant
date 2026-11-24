package com.se.platform.material.controller;

import com.se.platform.common.Result;
import com.se.platform.material.dto.KpDtos.ChapterBadge;
import com.se.platform.material.dto.KpDtos.KpMap;
import com.se.platform.material.dto.KpDtos.KpSaveRequest;
import com.se.platform.material.dto.KpDtos.KpStatus;
import com.se.platform.material.dto.KpDtos.KpView;
import com.se.platform.material.service.ChapterKpService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资料 → 知识点。老师：看进度、上传完毕、重新识别、改知识点、确认、看地图；学生：看已确认的知识点地图。
 * 上传文件本身还是走原来的 POST /api/chapter-items/{id}/file，上传后自动排队转文字。
 */
@RestController
public class ChapterKpController {

    private final ChapterKpService service;

    public ChapterKpController(ChapterKpService service) {
        this.service = service;
    }

    /** GET /api/chapters/{id}/kp-status：识别走到哪一步、每份资料的进度、知识点草稿 */
    @GetMapping("/api/chapters/{id}/kp-status")
    public Result<KpStatus> status(@PathVariable Long id) {
        return Result.ok(service.status(id));
    }

    /** POST /api/chapters/{id}/kp/upload-done：老师点“上传完毕”，资料都处理好就自动识别 */
    @PostMapping("/api/chapters/{id}/kp/upload-done")
    public Result<KpStatus> uploadDone(@PathVariable Long id) {
        return Result.ok(service.uploadDone(id));
    }

    /** POST /api/chapters/{id}/kp/rerun：重新识别，保留老师改过的知识点 */
    @PostMapping("/api/chapters/{id}/kp/rerun")
    public Result<KpStatus> rerun(@PathVariable Long id) {
        return Result.ok(service.rerun(id));
    }

    /** POST /api/chapters/{id}/kp/confirm：老师确认，学生看得到知识点地图 */
    @PostMapping("/api/chapters/{id}/kp/confirm")
    public Result<KpStatus> confirm(@PathVariable Long id) {
        return Result.ok(service.confirm(id));
    }

    @PostMapping("/api/chapters/{id}/kps")
    public Result<KpView> create(@PathVariable Long id, @Valid @RequestBody KpSaveRequest req) {
        return Result.ok(service.create(id, req));
    }

    @PutMapping("/api/chapter-kps/{id}")
    public Result<KpView> update(@PathVariable Long id, @Valid @RequestBody KpSaveRequest req) {
        return Result.ok(service.update(id, req));
    }

    @DeleteMapping("/api/chapter-kps/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /** GET /api/chapters/{id}/kp-map：老师看知识点地图（草稿也能看） */
    @GetMapping("/api/chapters/{id}/kp-map")
    public Result<KpMap> teacherMap(@PathVariable Long id) {
        return Result.ok(service.teacherMap(id));
    }

    /** GET /api/courses/{id}/kp-badges：课程大纲里每章“知识点”入口的标签 */
    @GetMapping("/api/courses/{id}/kp-badges")
    public Result<List<ChapterBadge>> badges(@PathVariable Long id) {
        return Result.ok(service.badges(id));
    }

    /** GET /api/learn/chapters/{id}/kp-map：学生看知识点地图（只有老师确认过的） */
    @GetMapping("/api/learn/chapters/{id}/kp-map")
    public Result<KpMap> studentMap(@PathVariable Long id) {
        return Result.ok(service.studentMap(id));
    }
}
