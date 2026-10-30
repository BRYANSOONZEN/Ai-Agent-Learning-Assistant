package com.se.platform.dialogue.controller;

import com.se.platform.common.Result;
import com.se.platform.dialogue.dto.DialogueOpening;
import com.se.platform.dialogue.dto.DialogueView;
import com.se.platform.dialogue.dto.SendMessageRequest;
import com.se.platform.dialogue.service.DialogueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生 AI 对话的接口，前缀 /api/dialogue。每个操作都返回整份 DialogueView。
 * 登录还没做，学生固定为演示学生；章节先默认 1。
 */
@RestController
@RequestMapping("/api/dialogue")
public class DialogueController {

    private final DialogueService service;

    public DialogueController(DialogueService service) {
        this.service = service;
    }

    /** GET /api/dialogue/chapters/1?itemId=3：开场页，本章（或本章某个 AI 对话项）有哪些话题 */
    @GetMapping("/chapters/{chapterId}")
    public Result<DialogueOpening> opening(@PathVariable Long chapterId,
                                           @RequestParam(required = false) Long itemId) {
        return Result.ok(service.opening(chapterId, itemId));
    }

    /** POST /api/dialogue/sessions?chapterId=1&itemId=3：开始对话；itemId 选填，不传 = 整章的话题 */
    @PostMapping("/sessions")
    public Result<DialogueView> start(@RequestParam(defaultValue = "1") Long chapterId,
                                      @RequestParam(required = false) Long itemId) {
        return Result.ok(service.start(chapterId, itemId));
    }

    /** GET /api/dialogue/sessions/{id}：刷新页面后恢复 */
    @GetMapping("/sessions/{id}")
    public Result<DialogueView> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    /** POST /api/dialogue/sessions/{id}/messages {"text": "..."}：学生发一句 */
    @PostMapping("/sessions/{id}/messages")
    public Result<DialogueView> send(@PathVariable Long id, @Valid @RequestBody SendMessageRequest req) {
        return Result.ok(service.send(id, req.text()));
    }

    /** POST /api/dialogue/sessions/{id}/stuck：我卡住了 */
    @PostMapping("/sessions/{id}/stuck")
    public Result<DialogueView> stuck(@PathVariable Long id) {
        return Result.ok(service.stuck(id));
    }

    /** POST /api/dialogue/sessions/{id}/resume：暂停后继续 */
    @PostMapping("/sessions/{id}/resume")
    public Result<DialogueView> resume(@PathVariable Long id) {
        return Result.ok(service.resume(id));
    }
}
