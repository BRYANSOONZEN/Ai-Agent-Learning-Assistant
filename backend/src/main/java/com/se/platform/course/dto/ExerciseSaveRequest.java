package com.se.platform.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 新建 / 修改单选题。chapterId、itemId（放进哪一次练习）只在新建时用。
 * optionNotes：每个选项的误解说明，和 options 一一对应，正确选项留空。
 */
public record ExerciseSaveRequest(
        Long chapterId,
        Long itemId,
        @NotBlank(message = "题干不能为空") String stem,
        @NotEmpty(message = "选项不能为空") @Size(min = 2, max = 6, message = "选项为 2～6 个") List<String> options,
        @NotBlank(message = "请选择正确答案") String answer,
        String analysis,
        List<String> optionNotes,
        @Min(value = 1, message = "难度为 1～3") @Max(value = 3, message = "难度为 1～3") Integer difficulty,
        @NotEmpty(message = "至少关联一个知识点") List<Long> knowledgePointIds) {
}
