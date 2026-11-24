package com.se.platform.course.dto;

import java.util.List;

/** 返回给前端的题目（老师端，带答案、解析和检查结果） */
public record ExerciseView(
        Long id,
        Long chapterId,
        Long itemId,
        String type,
        String assessType,
        Integer keyPointNo,
        String stem,
        List<String> options,
        String answer,
        String analysis,
        List<String> optionNotes,
        List<String> reasons,
        String reasonAnswer,
        List<String> reasonNotes,
        List<String> accepts,
        String reference,
        List<String> keyPoints,
        String source,
        boolean draft,
        String checkNote,
        Integer difficulty,
        Integer sortOrder,
        List<KpRef> knowledgePoints) {

    /** 题目关联的知识点，只要 id 和名称 */
    public record KpRef(Long id, String name) {
    }
}
