package com.se.platform.assistant.controller;

import com.se.platform.assistant.dto.AssistantDtos.ChatLine;
import com.se.platform.assistant.dto.AssistantDtos.ChatReply;
import com.se.platform.assistant.dto.AssistantDtos.ChatSend;
import com.se.platform.assistant.dto.AssistantDtos.RemedyResult;
import com.se.platform.assistant.dto.AssistantDtos.RemedySubmit;
import com.se.platform.assistant.dto.AssistantDtos.RemedyView;
import com.se.platform.assistant.dto.AssistantDtos.Summary;
import com.se.platform.assistant.service.AssistantChatService;
import com.se.platform.intervention.dto.ClassAnalysisDtos.MyPush;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushResult;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushSubmit;
import com.se.platform.intervention.service.ClassPushService;
import com.se.platform.assistant.service.RemedyService;
import java.util.List;
import com.se.platform.assistant.service.AssistantService;
import com.se.platform.common.Result;
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
    private final RecordService recordService;
    private final RemedyService remedyService;
    private final AssistantChatService chatService;
    private final ClassPushService pushService;

    public AssistantController(AssistantService assistantService,
                               RecordService recordService, RemedyService remedyService,
                               AssistantChatService chatService, ClassPushService pushService) {
        this.pushService = pushService;
        this.chatService = chatService;
        this.remedyService = remedyService;
        this.assistantService = assistantService;
        this.recordService = recordService;
    }

    /** GET /api/assistant/summary：学过的章节、进度，学完的章节带个人总结 */
    @GetMapping("/summary")
    public Result<Summary> summary() {
        return Result.ok(assistantService.summary(recordService.currentStudentId()));
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

    /** GET /api/assistant/pushes：老师推给我的补充讲解 + 新题 */
    @GetMapping("/pushes")
    public Result<List<MyPush>> pushes() {
        return Result.ok(pushService.mine(recordService.currentStudentId()));
    }

    /** POST /api/assistant/pushes/{id}/accept：接收老师的推送（接收后存到学习推荐，在学习推荐页里做） */
    @PostMapping("/pushes/{id}/accept")
    public Result<Void> acceptPush(@PathVariable Long id) {
        pushService.accept(recordService.currentStudentId(), id);
        return Result.ok();
    }

    /** POST /api/assistant/pushes/{id}/submit：做老师推送的新题（算进补练，只算第一次） */
    @PostMapping("/pushes/{id}/submit")
    public Result<PushResult> submitPush(@PathVariable Long id, @RequestBody PushSubmit body) {
        return Result.ok(pushService.submit(recordService.currentStudentId(), id, body.answers()));
    }

    /** GET /api/assistant/notices：老师的提醒（最近 10 条） */
    @GetMapping("/notices")
    public Result<List<String>> notices() {
        return Result.ok(pushService.notices(recordService.currentStudentId()));
    }

    /** POST /api/assistant/extend/{kpId}/start?assessType=scenario：拓展（E1），已掌握的知识点来点更难的题 */
    @PostMapping("/extend/{kpId}/start")
    public Result<RemedyView> startExtend(@PathVariable Long kpId,
                                          @RequestParam(required = false) String assessType) {
        return Result.ok(remedyService.startExtend(recordService.currentStudentId(), kpId, assessType));
    }

    /** POST /api/assistant/extend/{kpId}/submit：交拓展题（不算进掌握度） */
    @PostMapping("/extend/{kpId}/submit")
    public Result<RemedyResult> submitExtend(@PathVariable Long kpId, @RequestBody RemedySubmit body) {
        return Result.ok(remedyService.submitExtend(recordService.currentStudentId(), kpId, body.answers()));
    }
}
