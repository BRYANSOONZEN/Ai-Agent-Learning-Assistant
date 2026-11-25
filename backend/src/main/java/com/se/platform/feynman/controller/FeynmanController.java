package com.se.platform.feynman.controller;

import com.se.platform.common.Result;
import com.se.platform.feynman.dto.FeynmanDtos.Availability;
import com.se.platform.feynman.dto.FeynmanDtos.DraftRequest;
import com.se.platform.feynman.dto.FeynmanDtos.EditRequest;
import com.se.platform.feynman.dto.FeynmanDtos.SayRequest;
import com.se.platform.feynman.dto.FeynmanDtos.SessionView;
import com.se.platform.feynman.dto.FeynmanDtos.SetupView;
import com.se.platform.feynman.service.FeynmanDraftService;
import com.se.platform.feynman.service.FeynmanEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 费曼式 AI 对话。老师：选知识点、生成草稿、确认；学生：开始、说话 */
@RestController
public class FeynmanController {

    private final FeynmanDraftService drafts;
    private final FeynmanEngine engine;

    public FeynmanController(FeynmanDraftService drafts, FeynmanEngine engine) {
        this.drafts = drafts;
        this.engine = engine;
    }

    // ---------------- 老师

    /** GET /api/chapter-items/{id}/feynman：可选的知识点、已选的、草稿 */
    @GetMapping("/api/chapter-items/{id}/feynman")
    public Result<SetupView> setup(@PathVariable Long id) {
        return Result.ok(drafts.view(id));
    }

    /** POST /api/chapter-items/{id}/feynman/draft {"kpIds": [3]}：大模型起草（最多 2 个知识点） */
    @PostMapping("/api/chapter-items/{id}/feynman/draft")
    public Result<SetupView> draft(@PathVariable Long id, @RequestBody DraftRequest req) {
        return Result.ok(drafts.draft(id, req.kpIds()));
    }

    /** PUT /api/chapter-items/{id}/feynman/draft {"kpId": 3, "draft": {...}}：老师改草稿里的文字 */
    @PutMapping("/api/chapter-items/{id}/feynman/draft")
    public Result<SetupView> edit(@PathVariable Long id, @RequestBody EditRequest req) {
        return Result.ok(drafts.edit(id, req.kpId(), req.draft()));
    }

    @PostMapping("/api/chapter-items/{id}/feynman/confirm")
    public Result<SetupView> confirm(@PathVariable Long id) {
        return Result.ok(drafts.confirm(id));
    }

    // ---------------- 学生

    /** GET /api/learn/items/{id}/feynman：这个对话项有没有可用的费曼对话 */
    @GetMapping("/api/learn/items/{id}/feynman")
    public Result<Availability> availability(@PathVariable Long id) {
        return Result.ok(engine.availability(id));
    }

    /** POST /api/learn/items/{id}/feynman/start：开始，或接着上次没聊完的 */
    @PostMapping("/api/learn/items/{id}/feynman/start")
    public Result<SessionView> start(@PathVariable Long id) {
        return Result.ok(engine.start(id));
    }

    @GetMapping("/api/learn/feynman/{sessionId}")
    public Result<SessionView> get(@PathVariable Long sessionId) {
        return Result.ok(engine.get(sessionId));
    }

    /** POST /api/learn/feynman/{sessionId}/reply {"text": "..."} */
    @PostMapping("/api/learn/feynman/{sessionId}/reply")
    public Result<SessionView> reply(@PathVariable Long sessionId, @RequestBody SayRequest req) {
        return Result.ok(engine.reply(sessionId, req.text()));
    }
}
