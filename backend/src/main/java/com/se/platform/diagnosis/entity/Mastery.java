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
    /** 当前掌握度 0～1（画像颜色、推荐、干预都看它），算法见 MasteryService */
    private Double mastery;
    private Integer attemptCount;
    private Integer correctCount;
    /** 原始掌握度：练习里每道题第一次作答的平均分，补练不改它 */
    private Double firstMastery;
    private Integer firstCount;
    /** 补练新题第一次作答的平均分、题数、状态（recovered 已补回来 / practicing 补练中） */
    private Double remedy;
    private Integer remedyCount;
    private String remedyStatus;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
