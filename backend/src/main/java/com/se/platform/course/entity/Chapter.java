package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 章节，对应 chapter 表。一章 = 章节介绍 + 一串内容项（chapter_item） */
@Data
@TableName("chapter")
public class Chapter {

    private Long id;
    private Long courseId;
    private Long parentId;
    private String title;
    private String intro;

    /** draft 草稿 / published 已发布 */
    private String status;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;
}
