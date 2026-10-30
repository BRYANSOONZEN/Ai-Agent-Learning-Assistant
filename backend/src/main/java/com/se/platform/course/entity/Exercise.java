package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 习题，对应 exercise 表。
 * options、optionNotes 在库里是 JSON 字符串，转 List 的工作在 Service 里做（和 DialogueTopic 同样的做法）。
 */
@Data
@TableName("exercise")
public class Exercise {

    private Long id;
    private Long courseId;
    private Long chapterId;
    /** 属于哪一次练习 → chapter_item.id */
    private Long itemId;
    private String type;
    private String stem;
    private String options;
    private String answer;
    private String analysis;
    private String optionNotes;
    private Integer difficulty;
    private Integer sortOrder;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;
}
