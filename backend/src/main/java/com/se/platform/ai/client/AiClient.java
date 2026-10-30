package com.se.platform.ai.client;

/**
 * 调模型的接口。
 *
 * <p>{@code interface}：只规定"能做什么"，不写"怎么做"。业务代码只依赖这个接口：
 * 正式运行时 Spring 注入 {@link OpenAiCompatibleClient}（真的发 HTTP 请求），
 * 测试时换成 FakeAiClient（直接返回预设的 JSON），业务代码一行都不用改。
 */
public interface AiClient {

    /**
     * 发一次请求，等模型回复。
     *
     * @throws AiCallException 没配 key、超时、报错、返回格式不对
     */
    ChatResult chat(ChatRequest request);
}
