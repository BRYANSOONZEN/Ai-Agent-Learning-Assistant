package com.se.platform.intervention.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 干预（intervention 表）：系统按预警写好建议（pending），老师通过后推给学生 */
@Data
@TableName("intervention")
public class Intervention {

    /** A：变式练习 */
    public static final String PRACTICE = "PRACTICE";
    /** B：复习 + 重做对话 */
    public static final String REVIEW = "REVIEW";
    /** A + B：先复习 + 重做对话（step 1），再做练习（step 2） */
    public static final String REVIEW_PRACTICE = "REVIEW_PRACTICE";

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String DONE = "done";

    private Long id;
    private Long alertId;
    private Long courseId;
    private Long studentId;
    private Long knowledgePointId;
    private String type;
    private Integer step;
    /** 布置的题，逗号分隔 */
    private String exerciseIds;
    /** 要复习的内容项，逗号分隔 */
    private String itemIds;
    /** 给学生的话 */
    private String note;
    private String source;
    private String status;
    /** A：掌握度；B：话题结果 */
    private String beforeValue;
    private String afterValue;
    private Boolean effective;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime step2At;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
