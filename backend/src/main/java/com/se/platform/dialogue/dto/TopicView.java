package com.se.platform.dialogue.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 返回给前端的话题：和实体类的区别是 keyPoints、hints 已经从 JSON 字符串转成了列表 */
public record TopicView(
        Long id,
        Long itemId,
        Integer sortOrder,
        String title,
        String question,
        String simpleQuestion,
        List<String> keyPoints,
        List<String> hints,
        String revealText,
        String sourceRef,
        String sourceText,
        Boolean createdByAi,
        Boolean enabled,
        LocalDateTime createdAt) {
}
