package com.se.platform.course.dto;

/** 返回给前端的章节，带上本章各类内容的数量。itemCount = 内容项数（视频、文档、练习、AI 对话） */
public record ChapterView(
        Long id,
        Long courseId,
        String title,
        String intro,
        String status,
        Integer sortOrder,
        long itemCount,
        long knowledgePointCount,
        long exerciseCount,
        long topicCount) {
}
