package com.se.platform.course.dto;

import java.time.LocalDateTime;

/**
 * 返回给前端的资料。
 * kind：video / pdf / image / text / office / other，前端按它决定怎么预览。
 * url：预览和下载地址（不含 /api 前缀之外的服务器路径，不暴露磁盘位置）。
 */
public record MaterialView(
        Long id,
        Long chapterId,
        String title,
        String originalName,
        Long fileSize,
        String contentType,
        String kind,
        String url,
        LocalDateTime createdAt) {
}
