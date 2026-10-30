package com.se.platform.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 老师点"AI 拆要点"时提交的内容。
 *
 * <p>{@code @NotBlank} / {@code @Size}：参数校验。Controller 参数前加了 {@code @Valid}，
 * 不符合时 Spring 直接拒绝，GlobalExceptionHandler 把 message 返回给前端。
 */
public record TopicDraftRequest(
        @NotBlank(message = "情境问题不能为空") @Size(max = 1000, message = "情境问题不超过 1000 字") String question,
        @NotBlank(message = "参考答案不能为空") @Size(max = 2000, message = "参考答案不超过 2000 字") String answer,
        @Size(max = 100, message = "出处不超过 100 字") String sourceRef,
        @Size(max = 2000, message = "依据原文不超过 2000 字") String sourceText) {
}
