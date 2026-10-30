package com.se.platform.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.se.platform.ai.config.LlmProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 用 OpenAI 兼容格式调模型：POST {base-url}/chat/completions。
 * Gemini、DeepSeek、通义千问都支持这个格式，换模型只改 application.yml，不改代码。
 *
 * <p>请求体示例：
 * <pre>
 * {"model": "gemini-2.5-flash",
 *  "messages": [{"role": "user", "content": "..."}],
 *  "temperature": 0.2, "max_tokens": 1024,
 *  "response_format": {"type": "json_object"}}
 * </pre>
 * 回复里取 choices[0].message.content，以及 usage.prompt_tokens / completion_tokens。
 */
@Slf4j
@Component
public class OpenAiCompatibleClient implements AiClient {

    /** 重试前等多久（毫秒），第 n 次重试等 n 倍 */
    private static final long RETRY_DELAY_MS = 2000;

    private final LlmProperties props;
    private final RestClient restClient;

    /**
     * 构造器注入：Spring 创建这个对象时，自动把 LlmProperties 和 RestClient.Builder 传进来。
     * RestClient 是 Spring 自带的 HTTP 客户端，这里给它设好地址和超时，之后每次调用都复用。
     */
    public OpenAiCompatibleClient(LlmProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        this.restClient = builder.baseUrl(props.getBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        if (!props.isApiKeyConfigured()) {
            throw new AiCallException("没有设置环境变量 LLM_API_KEY");
        }
        Map<String, Object> body = buildBody(request);

        // 第一次 + 重试次数。超时、5xx 是临时问题，值得再试；4xx（key 错、额度用完）再试也没用
        int attempts = 1 + Math.max(0, props.getMaxRetries());
        AiCallException last = null;
        for (int i = 1; i <= attempts; i++) {
            long start = System.currentTimeMillis();
            try {
                JsonNode json = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + props.getApiKey())
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                return parse(json, System.currentTimeMillis() - start);
            } catch (HttpClientErrorException e) {
                throw new AiCallException(describe4xx(e), e);
            } catch (HttpServerErrorException e) {
                last = new AiCallException("模型服务报错 " + e.getStatusCode().value(), e);
            } catch (ResourceAccessException e) {
                last = new AiCallException("连接模型超时或网络不通", e);
            } catch (RestClientException e) {
                // 读回复时超时，Spring 会包成这个更笼统的异常（实测 Gemini 慢时就是这样），同样值得重试
                last = new AiCallException("等待模型回复超时", e);
            }
            log.warn("调用模型失败（第 {}/{} 次）：{}", i, attempts, last.getMessage());
            if (i < attempts) {
                pause(RETRY_DELAY_MS * i);   // 503 多半是服务一时繁忙，马上重试往往还是失败，先等一下
            }
        }
        throw last;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 恢复中断标记，让上层知道线程被打断了
        }
    }

    private Map<String, Object> buildBody(ChatRequest request) {
        // LinkedHashMap 会保持放入的顺序，打印出来和上面注释里的示例一致，方便排查
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("messages", request.messages());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        if (request.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.putAll(props.getExtraBody());
        return body;
    }

    private ChatResult parse(JsonNode json, long latencyMs) {
        // path() 和 get() 的区别：字段不存在时 path() 返回一个"缺失"节点而不是 null，不会空指针
        JsonNode content = json == null ? null : json.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new AiCallException("模型返回的内容为空或格式不对");
        }
        JsonNode usage = json.path("usage");
        return new ChatResult(
                content.asText(),
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                latencyMs,
                json.path("model").asText(props.getModel()));
    }

    /** 4xx 的常见原因翻译成中文；附上服务方返回的前 200 个字符，方便排查（里面不含我们的 key） */
    private String describe4xx(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String reason = switch (status) {
            case 400 -> "请求格式不对（常见原因：模型名写错、key 无效）";
            case 401, 403 -> "API key 无效或没有权限";
            case 404 -> "接口地址或模型名不存在";
            case 429 -> "调用太频繁或免费额度用完了";
            default -> "请求被拒绝";
        };
        String detail = e.getResponseBodyAsString();
        if (detail.length() > 200) {
            detail = detail.substring(0, 200) + "...";
        }
        return reason + "（HTTP " + status + "）：" + detail;
    }
}
