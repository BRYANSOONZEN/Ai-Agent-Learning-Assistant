package com.se.platform.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 识别出的一个知识点（chapter_kp 表），老师可以改；key_points、misconceptions、sources 存 JSON */
@Data
@TableName("chapter_kp")
public class ChapterKp {

    private Long id;
    private Long chapterId;
    private Integer sortOrder;
    private String name;
    private String summary;
    private String importance;
    private String keyPoints;
    private String misconceptions;
    private String sources;
    private Boolean edited;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long knowledgePointId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
