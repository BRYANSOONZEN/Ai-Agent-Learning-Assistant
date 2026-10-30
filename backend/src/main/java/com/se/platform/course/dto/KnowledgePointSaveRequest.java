package com.se.platform.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新建 / 修改知识点。chapterId 只在新建时用 */
public record KnowledgePointSaveRequest(
        Long chapterId,
        @NotBlank(message = "知识点名称不能为空") @Size(max = 100, message = "知识点名称不超过 100 字") String name,
        String content,
        @Min(value = 1, message = "难度为 1～3") @Max(value = 3, message = "难度为 1～3") Integer difficulty,
        Boolean isKey,
        Boolean isDifficult) {
}
