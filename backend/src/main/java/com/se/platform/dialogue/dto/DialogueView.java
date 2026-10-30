package com.se.platform.dialogue.dto;

import java.util.List;

/**
 * 返回给学生对话页的全部状态：每次发言、卡住、继续之后都整份返回，前端直接重画，不用自己拼。
 *
 * @param status     OPEN / PAUSED / FINISHED
 * @param currentIdx 当前第几个话题（从 0 开始）
 * @param summary    结束后才有，否则为 null
 */
public record DialogueView(
        Long sessionId,
        String status,
        int currentIdx,
        List<TopicProgress> topics,
        List<MessageView> messages,
        Summary summary) {

    /** 一个话题的进度。result 为 null 表示还没结束 */
    public record TopicProgress(String title, String result) {
    }

    /** 一条消息。role：STUDENT / AI；source：MODEL / TEMPLATE / RULE */
    public record MessageView(String role, String content, String source, Integer topicIdx) {
    }

    /** 总结（不调模型，按每个话题的结果生成） */
    public record Summary(int total, int completed, List<String> good, List<String> improve, List<String> skipped) {
    }
}
