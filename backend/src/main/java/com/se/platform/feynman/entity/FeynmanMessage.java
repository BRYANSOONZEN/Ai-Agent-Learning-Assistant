package com.se.platform.feynman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 费曼对话里的一句（feynman_message 表） */
@Data
@TableName("feynman_message")
public class FeynmanMessage {

    public static final String STUDENT = "student";
    public static final String AI = "ai";
    public static final String SYSTEM = "system";

    private Long id;
    private Long sessionId;
    private Integer kpIdx;
    private String role;
    private String content;
    private String judgment;
    private Long aiCallId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
