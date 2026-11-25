package com.se.platform.assistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 老师给学生的提醒（assistant_notice 表），显示在小助手里 */
@Data
@TableName("assistant_notice")
public class AssistantNotice {

    private Long id;
    private Long studentId;
    private Long itemId;
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
