package com.se.platform.course.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 调整顺序：按新顺序传全部 id，第一个排最前 */
public record OrderRequest(@NotEmpty(message = "ids 不能为空") List<Long> ids) {
}
