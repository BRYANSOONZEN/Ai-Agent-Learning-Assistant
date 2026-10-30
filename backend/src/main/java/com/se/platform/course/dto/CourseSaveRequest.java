package com.se.platform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 新建 / 修改课程。modules 不传 = 全部模块 */
public record CourseSaveRequest(
        @NotBlank(message = "课程名不能为空") @Size(max = 100, message = "课程名不超过 100 字") String name,
        String description,
        @Pattern(regexp = "draft|published|archived", message = "状态只能是 draft / published / archived") String status,
        List<String> modules) {
}
