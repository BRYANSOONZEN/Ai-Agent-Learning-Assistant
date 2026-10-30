package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 章节内容项，对应 chapter_item 表。一章 = 一串内容项，学生按 sortOrder 一项一项学。
 * type：video 视频 / document 文档 / exercise 练习 / dialogue AI 对话。
 */
@Data
@TableName("chapter_item")
public class ChapterItem {

    private Long id;
    private Long courseId;
    private Long chapterId;
    private String type;
    private String title;
    /** 说明；文档项也可以不传文件，直接在这里写正文 */
    private String description;

    /** 视频 / 文档项的文件 → material.id。换文件、删文件时要能改回 null，所以设成 ALWAYS */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long materialId;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;
}
