package com.se.platform.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 新建 / 修改题目。chapterId、itemId（放进哪一次练习）只在新建时用。
 * 不同题型要填的字段不同，由 ExerciseService 按题型检查（见 Grader 的题型常量）：
 * <ul>
 *   <li>单选 / 判断 / 多选：options、answer（多选是几个字母，如 AC）、optionNotes（每个错误选项的误解）</li>
 *   <li>两段式单选：再加 reasons（理由选项）、reasonAnswer、reasonNotes</li>
 *   <li>填空：accepts（都算对的说法，1～5 个）</li>
 *   <li>简答：reference（参考答案）、keyPoints（判分要点，2～4 条）</li>
 * </ul>
 * type 不填 = 单选（兼容原来的接口）；assessType 不填 = 概念辨析。
 */
public record ExerciseSaveRequest(
        Long chapterId,
        Long itemId,
        String type,
        String assessType,
        Integer keyPointNo,
        @NotBlank(message = "题干不能为空") String stem,
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
        @Min(value = 1, message = "难度为 1～3") @Max(value = 3, message = "难度为 1～3") Integer difficulty,
        @NotEmpty(message = "至少关联一个知识点") List<Long> knowledgePointIds) {
}
