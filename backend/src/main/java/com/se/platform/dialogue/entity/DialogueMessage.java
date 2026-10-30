package com.se.platform.dialogue.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对话里的一条消息（dialogue_message 表） */
@Data
@TableName("dialogue_message")
public class DialogueMessage {

    private Long id;
    private Long sessionId;
    private Integer topicIdx;
    /** STUDENT / AI */
    private String role;
    private String content;
    /** MODEL 模型写的 / TEMPLATE 老师预写的 / RULE 程序拼的 */
    private String source;
    /** 学生这句被判成哪一类（只有学生消息有） */
    private String judgedType;
    /** 这句对应的模型调用 → ai_call_log.id；"我卡住了"不调模型，为 null */
    private Long aiCallId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
