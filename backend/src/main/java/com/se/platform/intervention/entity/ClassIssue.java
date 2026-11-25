package com.se.platform.intervention.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 全班分析发现的一个问题（class_issue 表）：
 * question 一道题错误率 > 80%，先给老师检查；cell 要点 × 考查类型错误率 > 50%，给老师推荐方案。
 */
@Data
@TableName("class_issue")
public class ClassIssue {

    public static final String QUESTION = "question";
    public static final String CELL = "cell";

    public static final String OPEN = "open";
    public static final String OK = "ok";
    public static final String FIXED = "fixed";
    public static final String VOIDED = "voided";
    public static final String IGNORED = "ignored";
    public static final String PUSHED = "pushed";

    private Long id;
    private Long analysisId;
    private Long itemId;
    private Long courseId;
    private String kind;
    private Long knowledgePointId;
    private Integer keyPointNo;
    private String assessType;
    private Long exerciseId;
    private Integer attempts;
    private Integer wrong;
    /** 做错的学生，逗号分隔 */
    private String studentIds;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
