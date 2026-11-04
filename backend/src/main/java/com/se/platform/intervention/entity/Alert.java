package com.se.platform.intervention.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 预警（alert 表）：一个学生在一个知识点上，按两层判断出的问题 */
@Data
@TableName("alert")
public class Alert {

    /** 应用问题：概念懂（对话答出），练习做不对 */
    public static final String A = "A";
    /** 概念问题：练习做得对（或没做），对话讲不出来 */
    public static final String B = "B";
    /** 两层都不好 */
    public static final String AB = "AB";

    public static final String OPEN = "open";
    public static final String HANDLED = "handled";
    public static final String IGNORED = "ignored";

    private Long id;
    private Long courseId;
    private Long studentId;
    private Long knowledgePointId;
    private String type;
    /** high / medium */
    private String level;
    private String message;
    /** 判断时的数据快照（JSON） */
    private String evidence;
    private String status;
    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
