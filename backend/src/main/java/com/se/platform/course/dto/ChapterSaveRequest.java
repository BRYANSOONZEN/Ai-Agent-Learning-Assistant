package com.se.platform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 修改章节。courseId 只在新建时用，修改时忽略（章节不能换课程）。
 * intro、status 新建时可以不填。
 */
public record ChapterSaveRequest(
        Long courseId,
        @NotBlank(message = "章节标题不能为空") @Size(max = 100, message = "章节标题不超过 100 字") String title,
        String intro,
        @Pattern(regexp = "draft|published", message = "状态只能是 draft / published") String status) {
}
