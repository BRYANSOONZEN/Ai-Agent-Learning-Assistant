package com.se.platform.dialogue.dto;

import java.util.List;

/**
 * AI 起草的结果，返回给老师修改；还没有保存到数据库。
 *
 * @param latencyMs 这次调用模型用了多少毫秒，页面上显示给老师看
 */
public record TopicDraft(
        String title,
        List<String> keyPoints,
        String simpleQuestion,
        List<String> hints,
        String model,
        long latencyMs) {
}
