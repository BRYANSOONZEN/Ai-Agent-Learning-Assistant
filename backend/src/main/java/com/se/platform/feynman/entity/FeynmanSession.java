package com.se.platform.feynman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一个学生的一次费曼对话（feynman_session 表）；进度存在 state（JSON） */
@Data
@TableName("feynman_session")
public class FeynmanSession {

    public static final String OPEN = "open";
    public static final String DONE = "done";
    /** 老师重新生成了草稿，没聊完的旧对话作废（记录保留），学生再打开从新草稿开始 */
    public static final String EXPIRED = "expired";

    private Long id;
    private Long studentId;
    private Long itemId;
    private String status;
    private String state;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
