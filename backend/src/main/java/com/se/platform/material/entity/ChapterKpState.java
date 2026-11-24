package com.se.platform.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一章识别知识点走到哪一步（chapter_kp_state 表） */
@Data
@TableName("chapter_kp_state")
public class ChapterKpState {

    public static final String NONE = "none";
    public static final String WAITING = "waiting";
    public static final String RECOGNIZING = "recognizing";
    public static final String DRAFT = "draft";
    public static final String CONFIRMED = "confirmed";
    public static final String FAILED = "failed";

    private Long id;
    private Long chapterId;
    private Boolean uploadDone;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String message;
    private Long aiCallId;
    private LocalDateTime recognizedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
