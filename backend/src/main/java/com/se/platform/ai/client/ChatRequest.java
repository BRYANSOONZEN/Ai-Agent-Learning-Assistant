package com.se.platform.ai.client;

import java.util.List;

/**
 * 一次模型调用的请求。
 *
 * @param messages    对话消息，按顺序
 * @param temperature 随机程度，0～1；判断类任务用 0.2 左右，结果更稳定
 * @param maxTokens   回复最多多少 token
 * @param jsonMode    true 时要求模型只输出 JSON
 */
public record ChatRequest(List<ChatMessage> messages, double temperature, int maxTokens, boolean jsonMode) {
}
