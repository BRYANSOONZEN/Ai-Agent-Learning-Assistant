package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 知识点，对应 knowledge_point 表 */
@Data
@TableName("knowledge_point")
public class KnowledgePoint {

    private Long id;
    private Long courseId;
    private Long chapterId;
    private String name;
    private String content;
    private Integer difficulty;
    private Boolean isKey;
    private Boolean isDifficult;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;
}
