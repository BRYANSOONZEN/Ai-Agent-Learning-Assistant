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
    /** 题型，见 {@link com.se.platform.course.service.Grader} 里的常量 */
    private String type;
    /** 考查类型：concept / scenario / data / process */
    private String assessType;
    /** 考的是知识点的第几条要点（从 1 开始），可以为空 */
    private Integer keyPointNo;
    private String stem;
    private String options;
    private String answer;
    private String analysis;
    private String optionNotes;
    /** 两段式：理由选项、正确理由、每个错误理由的误解（JSON） */
    private String reasons;
    private String reasonAnswer;
    private String reasonNotes;
    /** 填空：都算对的说法（JSON 数组） */
    private String accepts;
    /** 简答：参考答案、判分要点（JSON 数组） */
    private String reference;
    private String keyPoints;
    /** manual / upload / ai */
    private String source;
    /** 草稿：老师还没确认，学生看不到 */
    private Boolean draft;
    private String checkNote;
    private Integer difficulty;
    private Integer sortOrder;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;

    /**
     * 能不能推荐给学生、布置成干预练习：不是草稿，而且是单选或判断（推荐页只能选一个选项）。
     * 两段式、多选、填空、简答只在练习里做。
     */
    public boolean recommendable() {
        // 小助手给某个学生当场生成的补练题只给他自己，不推荐给别人、不布置成干预
        // 老师全班推送的题（class）只给推送对象做
        return !Boolean.TRUE.equals(draft) && !"assistant".equals(source) && !"class".equals(source)
                && (type == null || "single_choice".equals(type) || "judge".equals(type));
    }
}
