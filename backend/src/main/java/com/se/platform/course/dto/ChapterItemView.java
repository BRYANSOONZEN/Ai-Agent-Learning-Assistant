package com.se.platform.course.dto;

/**
 * 返回给前端的内容项。
 * material：视频 / 文档项的文件，没有就是 null。
 * exerciseCount、topicCount：练习项里的题数、AI 对话项里的话题数。
 * ready：这一项内容齐了没有（视频有文件；文档有文件或正文；练习至少一题；对话至少一个话题），发布检查用。
 */
public record ChapterItemView(
        Long id,
        Long chapterId,
        String type,
        String title,
        String description,
        Integer sortOrder,
        MaterialView material,
        long exerciseCount,
        long topicCount,
        boolean ready) {
}
