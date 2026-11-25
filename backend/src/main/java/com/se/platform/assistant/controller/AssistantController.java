package com.se.platform.assistant.controller;

import com.se.platform.assistant.dto.AssistantDtos.ChatLine;
import com.se.platform.assistant.dto.AssistantDtos.ChatReply;
import com.se.platform.assistant.dto.AssistantDtos.ChatSend;
import com.se.platform.assistant.dto.AssistantDtos.RemedyResult;
import com.se.platform.assistant.dto.AssistantDtos.RemedySubmit;
import com.se.platform.assistant.dto.AssistantDtos.RemedyView;
import com.se.platform.assistant.dto.AssistantDtos.Summary;
import com.se.platform.assistant.service.AssistantChatService;
import com.se.platform.assistant.service.RemedyService;
import java.util.List;
import com.se.platform.assistant.service.AssistantService;
import com.se.platform.common.Result;
import com.se.platform.intervention.service.InterventionService;
import com.se.platform.record.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人学习助手，前缀 /api/assistant（docs/计划-个人学习助手.md）。
 * 登录还没做，学生是演示学生（见 DemoIdentity）。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final InterventionService interventionService;
    private final RecordService recordService;
    private final RemedyService remedyService;
    private final AssistantChatService chatService;

    public AssistantController(AssistantService assistantService, InterventionService interventionService,
                               RecordService recordService, RemedyService remedyService,
                               AssistantChatService chatService) {
        this.chatService = chatService;
        this.remedyService = remedyService;
        this.assistantService = assistantService;
        this.interventionService = interventionService;
        this.recordService = recordService;
    }

    /** GET /api/assistant/summary：学过的章节、进度，学完的章节带个人总结 */
    @GetMapping("/summary")
    public Result<Summary> summary() {
        long student = recordService.currentStudentId();
        synchronized (InterventionService.SCAN_LOCK) {
            // 先扫描这个学生：有新问题时干预自动执行，助手里马上能看到"老师布置"
            interventionService.scanStudent(student);
        }
        return Result.ok(assistantService.summary(student));
    }

    /** POST /api/assistant/remedy/{kpId}/start：开始（或继续）这个知识点的补练，当场生成个人补练题 */
    @PostMapping("/remedy/{kpId}/start")
    public Result<RemedyView> startRemedy(@PathVariable Long kpId) {
        return Result.ok(remedyService.start(recordService.currentStudentId(), kpId));
    }

    /** POST /api/assistant/remedy/{kpId}/submit：交这一轮补练题 */
    @PostMapping("/remedy/{kpId}/submit")
    public Result<RemedyResult> submitRemedy(@PathVariable Long kpId, @RequestBody RemedySubmit body) {
        return Result.ok(remedyService.submit(recordService.currentStudentId(), kpId, body.answers()));
    }

    /** GET /api/assistant/chat?chapterId=：这一章和小助手的聊天记录 */
    @GetMapping("/chat")
    public Result<List<ChatLine>> chatHistory(@RequestParam Long chapterId) {
        return Result.ok(chatService.history(recordService.currentStudentId(), chapterId));
    }

    /** POST /api/assistant/chat：问小助手一句（调大模型，按两个阶段的规则回答，程序检查有没有出题、给答案） */
    @PostMapping("/chat")
    public Result<ChatReply> chat(@RequestBody ChatSend body) {
        return Result.ok(chatService.chat(recordService.currentStudentId(), body.chapterId(), body.message()));
    }
}
