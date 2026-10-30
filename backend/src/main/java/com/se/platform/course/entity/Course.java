package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 课程，对应 course 表 */
@Data
@TableName("course")
public class Course {

    private Long id;
    private String name;
    private String description;
    private Long teacherId;
    /** draft 草稿 / published 已发布 / archived 已归档 */
    private String status;
    /** 开启的模块，逗号分隔，如 "video,document,knowledge,dialogue,exercise"，见 CourseService.MODULES */
    private String modules;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;
}
