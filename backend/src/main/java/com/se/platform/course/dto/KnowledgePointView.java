package com.se.platform.course.dto;

/** 返回给前端的知识点，exerciseCount = 关联了它的题目数 */
public record KnowledgePointView(
        Long id,
        Long chapterId,
        String name,
        String content,
        Integer difficulty,
        Boolean isKey,
        Boolean isDifficult,
        Integer sortOrder,
        long exerciseCount) {
}
