package com.se.platform.ai.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.AiCallException;
import com.se.platform.ai.client.AiClient;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.client.ChatResult;
import com.se.platform.ai.config.LlmProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 调模型并记日志：业务代码通过这里调 AiClient，每次调用（成功或失败）都会写一条 ai_call_log，
 * 记下 token、费用、总耗时（包含重试）。老师拆要点和学生对话都走这里。
 */
@Service
public class AiCallLogService {

    /** 任务名：老师拆要点 */
    public static final String TASK_TOPIC_DRAFT = "TOPIC_DRAFT";
    /** 任务名：学生对话的一轮 */
    public static final String TASK_DIALOGUE_TURN = "DIALOGUE_TURN";

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiClient aiClient;
    private final AiCallLogMapper mapper;
    private final LlmProperties props;
    private final ObjectMapper objectMapper;

    public AiCallLogService(AiClient aiClient, AiCallLogMapper mapper, LlmProperties props, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.mapper = mapper;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** 调用结果 + 这次调用的日志 id（之后可以用 markCheck 补记"输出是否通过检查"） */
    public record Logged(ChatResult result, Long logId) {
    }

    /** 调模型并记一条日志。失败时也记，然后照常抛出 AiCallException，调用方决定怎么兜底 */
    public Logged call(String task, String promptVersion, ChatRequest request) {
        AiCallLog log = new AiCallLog();
        log.setTask(task);
        log.setPromptVersion(promptVersion);
        log.setModel(props.getModel());
        log.setRequestJson(toJson(request));

        long start = System.currentTimeMillis();
        try {
            ChatResult result = aiClient.chat(request);
            log.setLatencyMs((int) (System.currentTimeMillis() - start));
            log.setModel(result.model());
            log.setInputTokens(result.inputTokens());
            log.setOutputTokens(result.outputTokens());
            log.setCostCny(cost(result.inputTokens(), result.outputTokens()));
            log.setResponseJson(result.content());
            log.setSuccess(true);
            mapper.insert(log);
            return new Logged(result, log.getId());
        } catch (AiCallException e) {
            log.setLatencyMs((int) (System.currentTimeMillis() - start));
            log.setSuccess(false);
            log.setError(truncate(e.getMessage()));
            mapper.insert(log);
            throw e;
        }
    }

    /** 补记输出检查的结果：格式不对、回复太长等 */
    public void markCheck(Long logId, boolean passed, String error) {
        AiCallLog update = new AiCallLog();
        update.setId(logId);
        update.setPassedCheck(passed);
        if (error != null) {
            update.setError(truncate(error));
        }
        mapper.updateById(update);   // updateById 只更新不为 null 的字段
    }

    /** 最近的若干条，新的在前 */
    public List<AiCallLog> recent(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .orderByDesc(AiCallLog::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    /** 汇总：一共调用了几次、多少 token、总耗时、总费用 */
    public record Totals(int calls, int failures, long inputTokens, long outputTokens, long totalLatencyMs,
                         long avgLatencyMs, BigDecimal costCny) {
    }

    public Totals totals() {
        List<AiCallLog> all = mapper.selectList(null);
        long in = 0;
        long out = 0;
        long ms = 0;
        int failures = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (AiCallLog l : all) {
            in += l.getInputTokens() == null ? 0 : l.getInputTokens();
            out += l.getOutputTokens() == null ? 0 : l.getOutputTokens();
            ms += l.getLatencyMs() == null ? 0 : l.getLatencyMs();
            failures += Boolean.TRUE.equals(l.getSuccess()) ? 0 : 1;
            cost = cost.add(l.getCostCny() == null ? BigDecimal.ZERO : l.getCostCny());
        }
        return new Totals(all.size(), failures, in, out, ms, all.isEmpty() ? 0 : ms / all.size(), cost);
    }

    /** 费用 = 输入 token × 输入单价 ÷ 100 万 + 输出 token × 输出单价 ÷ 100 万，保留 6 位小数 */
    BigDecimal cost(int inputTokens, int outputTokens) {
        return props.getPriceInputPerMillion().multiply(BigDecimal.valueOf(inputTokens))
                .add(props.getPriceOutputPerMillion().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }

    private String truncate(String s) {
        return s == null || s.length() <= 255 ? s : s.substring(0, 252) + "...";
    }
}
