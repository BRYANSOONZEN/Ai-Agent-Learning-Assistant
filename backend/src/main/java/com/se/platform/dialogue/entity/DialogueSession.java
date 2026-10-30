package com.se.platform.dialogue.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一次对话：学生在某一章开始的巩固对话（dialogue_session 表） */
@Data
@TableName("dialogue_session")
public class DialogueSession {

    public static final String OPEN = "OPEN";
    public static final String PAUSED = "PAUSED";
    public static final String FINISHED = "FINISHED";

    private Long id;
    private Long studentId;
    private Long chapterId;
    /** 章节里的哪个 AI 对话项；为空 = 整章的话题 */
    private Long itemId;
    private String kind;
    private String status;
    private Integer currentIdx;
    private Integer irrelevantTopicsInRow;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
