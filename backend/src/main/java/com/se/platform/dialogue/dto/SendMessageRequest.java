package com.se.platform.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 学生发的一句话 */
public record SendMessageRequest(
        @NotBlank(message = "说点什么再发送吧") @Size(max = 500, message = "一次最多 500 字") String text) {
}
