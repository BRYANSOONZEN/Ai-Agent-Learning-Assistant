package com.se.platform.course.dto;

import java.util.List;

/** 返回给前端的题目（老师端，带答案和解析） */
public record ExerciseView(
        Long id,
        Long chapterId,
        Long itemId,
        String type,
        String stem,
        List<String> options,
        String answer,
        String analysis,
        List<String> optionNotes,
        Integer difficulty,
        Integer sortOrder,
        List<KpRef> knowledgePoints) {

    /** 题目关联的知识点，只要 id 和名称 */
    public record KpRef(Long id, String name) {
    }
}
