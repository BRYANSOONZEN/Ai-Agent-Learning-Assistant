package com.se.platform.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一个视频 / 文档项转文字的进度（material_job 表） */
@Data
@TableName("material_job")
public class MaterialJob {

    public static final String QUEUED = "queued";
    public static final String PROCESSING = "processing";
    public static final String DONE = "done";
    public static final String FAILED = "failed";

    private Long id;
    private Long itemId;
    private Long chapterId;
    private Long materialId;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String message;
    private String meta;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
