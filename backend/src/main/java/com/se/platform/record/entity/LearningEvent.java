package com.se.platform.record.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一条学习行为（learning_event 表）：看了一项内容多久、学完了哪一项 */
@Data
@TableName("learning_event")
public class LearningEvent {

    /** 看了一项内容，durationSec = 停留秒数 */
    public static final String VIEW_ITEM = "view_item";
    /** 学完一项：文档打开、视频看完、练习交卷、AI 对话结束 */
    public static final String FINISH_ITEM = "finish_item";

    private Long id;
    private Long studentId;
    private String eventType;
    /** 内容项 → chapter_item.id */
    private Long targetId;
    private Long courseId;
    private Integer durationSec;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
