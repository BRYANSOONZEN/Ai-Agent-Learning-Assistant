package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 教学资料（课件、文档、视频），对应 material 表。文件本身在磁盘上，这里只存相对路径 */
@Data
@TableName("material")
public class Material {

    private Long id;
    private Long courseId;
    private Long chapterId;
    private Long knowledgePointId;
    private String title;
    private String originalName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private Long uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer deleted;
}
