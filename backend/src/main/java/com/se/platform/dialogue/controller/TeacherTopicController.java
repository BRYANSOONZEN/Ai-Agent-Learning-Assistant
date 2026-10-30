package com.se.platform.dialogue.controller;

import com.se.platform.ai.config.LlmProperties;
import com.se.platform.common.Result;
import com.se.platform.dialogue.dto.TopicDraft;
import com.se.platform.dialogue.dto.TopicDraftRequest;
import com.se.platform.dialogue.dto.TopicSaveRequest;
import com.se.platform.dialogue.dto.TopicView;
import com.se.platform.dialogue.service.DialogueTopicService;
import com.se.platform.dialogue.service.TopicDraftService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
 * 老师管理对话话题的接口，前缀 /api/teacher/topics。
 *
 * <p>章节表还没建，chapterId 先默认 1。登录和权限还没做，暂时不检查是不是老师。
 */
@RestController
@RequestMapping("/api/teacher/topics")
public class TeacherTopicController {

    private final TopicDraftService draftService;
    private final DialogueTopicService topicService;
    private final LlmProperties llmProperties;

    public TeacherTopicController(TopicDraftService draftService, DialogueTopicService topicService,
                                  LlmProperties llmProperties) {
        this.draftService = draftService;
        this.topicService = topicService;
        this.llmProperties = llmProperties;
    }

    /** GET /api/teacher/topics/ai-status：页面打开时检查 AI 能不能用（只说有没有 key，不返回 key） */
    @GetMapping("/ai-status")
    public Result<Map<String, Object>> aiStatus() {
        return Result.ok(Map.of(
                "available", llmProperties.isApiKeyConfigured(),
                "model", llmProperties.getModel()));
    }

    /** POST /api/teacher/topics/draft：AI 拆要点，返回草稿，不保存 */
    @PostMapping("/draft")
    public Result<TopicDraft> draft(@Valid @RequestBody TopicDraftRequest req) {
        return Result.ok(draftService.draft(req));
    }

    /** GET /api/teacher/topics?chapterId=1&itemId=3：本章已保存的话题；带 itemId 只要这个 AI 对话项里的 */
    @GetMapping
    public Result<List<TopicView>> list(@RequestParam(defaultValue = "1") Long chapterId,
                                        @RequestParam(required = false) Long itemId) {
        return Result.ok(topicService.list(chapterId, itemId));
    }

    /** POST /api/teacher/topics?chapterId=1&itemId=3：保存新话题，itemId 选填 */
    @PostMapping
    public Result<TopicView> create(@RequestParam(defaultValue = "1") Long chapterId,
                                    @RequestParam(required = false) Long itemId,
                                    @Valid @RequestBody TopicSaveRequest req) {
        return Result.ok(topicService.create(chapterId, itemId, req));
    }

    /** PUT /api/teacher/topics/{id}：修改话题 */
    @PutMapping("/{id}")
    public Result<TopicView> update(@PathVariable Long id, @Valid @RequestBody TopicSaveRequest req) {
        return Result.ok(topicService.update(id, req));
    }

    /** DELETE /api/teacher/topics/{id}：删除话题（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        topicService.delete(id);
        return Result.ok();
    }
}
