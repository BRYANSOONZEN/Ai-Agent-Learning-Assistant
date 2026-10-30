package com.se.platform.record.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一道题的一次作答（answer_record 表）。同一次交卷的几题 submittedAt 相同 */
@Data
@TableName("answer_record")
public class AnswerRecord {

    private Long id;
    private Long studentId;
    private Long exerciseId;
    private Long courseId;
    /** 哪一次练习 → chapter_item.id */
    private Long itemId;
    private String answer;
    private Boolean isCorrect;
    private Integer timeSpentSec;
    private LocalDateTime submittedAt;
}
