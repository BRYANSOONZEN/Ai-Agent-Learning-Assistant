package com.se.platform.assistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 小助手聊天的一句（assistant_message 表），按学生 + 章节分开 */
@Data
@TableName("assistant_message")
public class AssistantMessage {

    public static final String STUDENT = "student";
    public static final String ASSISTANT = "assistant";

    private Long id;
    private Long studentId;
    private Long chapterId;
    private String role;
    private String content;
    /** 被程序拦下的原因：question 阶段一出了题 / answer 给了具体题目的答案；没拦为空 */
    private String blocked;
    /** 被拦下时大模型原来的回复（不给学生看） */
    private String original;
    private Long aiCallId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
