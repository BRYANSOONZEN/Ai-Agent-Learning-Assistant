package com.se.platform.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 老师确认后保存（新建或修改）一个话题 */
public record TopicSaveRequest(
        @NotBlank(message = "话题名不能为空") @Size(max = 100, message = "话题名不超过 100 字") String title,
        @NotBlank(message = "情境问题不能为空") String question,
        String simpleQuestion,
        @NotEmpty(message = "至少要有一条要点") List<String> keyPoints,
        List<String> hints,
        @NotBlank(message = "讲解原文不能为空") String revealText,
        @Size(max = 100, message = "出处不超过 100 字") String sourceRef,
        String sourceText,
        Boolean createdByAi) {
}
