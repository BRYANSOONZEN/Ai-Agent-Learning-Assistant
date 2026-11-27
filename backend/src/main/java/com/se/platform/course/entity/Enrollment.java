package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 选课：一个学生加入了一门课 */
@Data
@TableName("enrollment")
public class Enrollment {
    private Long id;
    private Long courseId;
    private Long studentId;
    private LocalDateTime createdAt;
}
