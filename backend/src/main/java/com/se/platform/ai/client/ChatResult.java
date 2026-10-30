package com.se.platform.ai.client;

/**
 * 一次模型调用的结果。
 *
 * @param content      模型回复的文字
 * @param inputTokens  输入用了多少 token（算费用用）
 * @param outputTokens 输出用了多少 token
 * @param latencyMs    从发请求到收到回复用了多少毫秒
 * @param model        实际使用的模型名
 */
public record ChatResult(String content, int inputTokens, int outputTokens, long latencyMs, String model) {
}
