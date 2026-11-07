package com.se.platform.dialogue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.AiCallException;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.client.ChatResult;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.common.BusinessException;
import com.se.platform.dialogue.dto.TopicDraft;
import com.se.platform.dialogue.dto.TopicDraftRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 老师填了情境问题和参考答案后，让 AI 拆出要点、简单问法、三级提示和变式题。
 * 结果只是草稿，老师修改确认后才通过 DialogueTopicService 保存。
 */
@Slf4j
@Service
public class TopicDraftService {

    /** 模型返回的格式不对时，前端看到的错误码。503 = 服务暂时不可用 */
    private static final int AI_UNAVAILABLE = 503;

    private final AiCallLogService callLog;
    private final ObjectMapper objectMapper;
    private final PromptTemplate template = PromptTemplate.load("topic_draft_v3");

    public TopicDraftService(AiCallLogService callLog, ObjectMapper objectMapper) {
        this.callLog = callLog;
        this.objectMapper = objectMapper;
    }

    public TopicDraft draft(TopicDraftRequest req) {
        String prompt = template.render(Map.of(
                "question", req.question().trim(),
                "answer", req.answer().trim(),
                "source", formatSource(req.sourceRef(), req.sourceText())));

        // temperature 0.2：拆要点要稳定，不需要创意；maxTokens 给大一些，Gemini 2.5 的"思考"也算在里面
        ChatRequest request = new ChatRequest(List.of(ChatMessage.user(prompt)), 0.2, 4096, true);
        // 通过 AiCallLogService 调用：成功、失败都会记一条日志（token、耗时、费用）
        AiCallLogService.Logged logged;
        try {
            logged = callLog.call(AiCallLogService.TASK_TOPIC_DRAFT, template.name(), request);
        } catch (AiCallException e) {
            throw new BusinessException(AI_UNAVAILABLE, "AI 暂时不可用：" + e.getMessage());
        }
        ChatResult result = logged.result();
        log.info("AI 拆要点完成：model={}，耗时 {} ms，token 输入 {} / 输出 {}",
                result.model(), result.latencyMs(), result.inputTokens(), result.outputTokens());
        try {
            TopicDraft draft = parse(result);
            callLog.markCheck(logged.logId(), true, null);
            return draft;
        } catch (BusinessException e) {
            callLog.markCheck(logged.logId(), false, e.getMessage());
            throw e;
        }
    }

    private String formatSource(String ref, String text) {
        boolean hasRef = ref != null && !ref.isBlank();
        boolean hasText = text != null && !text.isBlank();
        if (!hasRef && !hasText) {
            return "（老师没有提供，只依据参考答案）";
        }
        return (hasRef ? ref.trim() + "：" : "") + (hasText ? text.trim() : "");
    }

    /** 把模型返回的 JSON 变成 TopicDraft；缺字段或类型不对就报错，不把半成品给老师 */
    TopicDraft parse(ChatResult result) {
        JsonNode json;
        try {
            json = objectMapper.readTree(stripCodeFence(result.content()));
        } catch (JsonProcessingException e) {
            throw new BusinessException(AI_UNAVAILABLE, "AI 返回的不是 JSON，请再试一次");
        }
        List<String> keyPoints = textList(json.path("key_points"));
        List<String> hints = textList(json.path("hints"));
        String title = json.path("title").asText("").trim();
        String simpleQuestion = json.path("simple_question").asText("").trim();
        if (keyPoints.isEmpty() || title.isEmpty()) {
            throw new BusinessException(AI_UNAVAILABLE, "AI 返回的内容缺少话题名或要点，请再试一次");
        }
        return new TopicDraft(title, keyPoints, simpleQuestion, hints,
                json.path("transfer_question").asText("").trim(), textList(json.path("transfer_points")),
                json.path("transfer_reveal").asText("").trim(), result.model(), result.latencyMs());
    }

    /** 有的模型即使要求只输出 JSON，也会包一层 ```json ... ```，这里去掉 */
    private String stripCodeFence(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            s = s.substring(s.indexOf('\n') + 1);
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
        }
        return s.trim();
    }

    /** JSON 数组 → 字符串列表，去掉空项 */
    private List<String> textList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String s = item.asText("").trim();
                if (!s.isEmpty()) {
                    list.add(s);
                }
            }
        }
        return list;
    }
}
