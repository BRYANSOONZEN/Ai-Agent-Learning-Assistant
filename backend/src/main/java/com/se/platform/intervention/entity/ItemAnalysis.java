package com.se.platform.intervention.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一次练习的全班分析（item_analysis 表）：按哪个截止时间、交卷几人、作废还是已分析 */
@Data
@TableName("item_analysis")
public class ItemAnalysis {

    public static final String DONE = "done";
    public static final String VOID = "void";

    private Long id;
    private Long itemId;
    private LocalDateTime dueAt;
    private String status;
    private Integer students;
    private Integer submitted;
    private LocalDateTime analyzedAt;
}
