package com.se.platform.intervention.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 老师全班干预的一次推送（class_push 表）：补充讲解 + 新题，推给做错的学生 */
@Data
@TableName("class_push")
public class ClassPush {

    public static final String DRAFT = "draft";
    public static final String PUSHED = "pushed";

    private Long id;
    private Long courseId;
    private Long itemId;
    private String issueIds;
    private Long knowledgePointId;
    private String assessType;
    private String explanation;
    /** ai 大模型起草 / teacher 老师写的 */
    private String explanationBy;
    private String exerciseIds;
    private String studentIds;
    private Double beforeRate;
    private String status;
    private LocalDateTime dueAt;
    private LocalDateTime pushedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
