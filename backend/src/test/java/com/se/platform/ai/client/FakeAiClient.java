package com.se.platform.ai.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 测试替身：不发网络请求，直接返回预设的回复。测试不花钱、结果固定。
 *
 * <p>用法：{@code fake.reply("{...json...}")} 预设回复（之后每次都返回它）；
 * {@code fake.reply(a).then(b).then(c)} 依次返回 a、b、c，最后一条会一直重复；
 * {@code fake.fail("超时")} 让下一次调用抛 AiCallException；
 * {@code fake.requests()} 查看业务代码实际发了什么（比如检查提示词里有没有某段文字）。
 */
public class FakeAiClient implements AiClient {

    private final Deque<String> replies = new ArrayDeque<>(List.of("{}"));
    private String nextError;
    private final List<ChatRequest> requests = new ArrayList<>();

    public FakeAiClient reply(String content) {
        replies.clear();
        replies.add(content);
        nextError = null;
        return this;
    }

    public FakeAiClient then(String content) {
        replies.add(content);
        return this;
    }

    public FakeAiClient fail(String message) {
        this.nextError = message;
        return this;
    }

    public List<ChatRequest> requests() {
        return requests;
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        requests.add(request);
        if (nextError != null) {
            String e = nextError;
            nextError = null;
            throw new AiCallException(e);
        }
        String content = replies.size() > 1 ? replies.poll() : replies.peek();
        return new ChatResult(content, 100, 50, 10, "fake-model");
    }
}
