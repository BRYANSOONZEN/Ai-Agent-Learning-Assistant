package com.se.platform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 修改章节内容项。chapterId、type 只在新建时用（内容项不能换章节、换类型）。
 * 文件不在这里传，用 POST /api/chapter-items/{id}/file 单独上传。
 */
public record ChapterItemSaveRequest(
        Long chapterId,
        @Pattern(regexp = "video|document|exercise|dialogue", message = "类型只能是 video / document / exercise / dialogue")
        String type,
        @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题不超过 100 字") String title,
        String description) {
}
