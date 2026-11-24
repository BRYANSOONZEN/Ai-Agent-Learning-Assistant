package com.se.platform.feynman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一个 AI 对话项的费曼对话设置：老师选的知识点和草稿（feynman_setup 表） */
@Data
@TableName("feynman_setup")
public class FeynmanSetup {

    public static final String DRAFT = "draft";
    public static final String CONFIRMED = "confirmed";

    private Long id;
    private Long itemId;
    private Long chapterId;
    private String kpIds;
    private String drafts;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
