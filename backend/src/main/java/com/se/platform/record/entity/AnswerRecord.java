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
    /** 得分 0～1；旧记录为空，按 isCorrect 算 */
    private Double score;
    /** 蒙对的概率 g；旧记录为空，按题型算 */
    private Double guess;
    /** 可信度权重 r；旧记录为空，按 1 算 */
    private Double reliability;
    /** graded 已判 / pending 等老师确认 */
    private String status;
    /** 简答判分明细（JSON） */
    private String detail;
    private Long reviewedBy;
    private Integer timeSpentSec;
    private LocalDateTime submittedAt;
}
