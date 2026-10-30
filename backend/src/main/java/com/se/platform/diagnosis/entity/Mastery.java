package com.se.platform.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一个学生在一个知识点上的掌握度（mastery 表），交卷后重算 */
@Data
@TableName("mastery")
public class Mastery {

    private Long id;
    private Long studentId;
    private Long knowledgePointId;
    /** 0～1，算法见 MasteryService */
    private Double mastery;
    private Integer attemptCount;
    private Integer correctCount;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
