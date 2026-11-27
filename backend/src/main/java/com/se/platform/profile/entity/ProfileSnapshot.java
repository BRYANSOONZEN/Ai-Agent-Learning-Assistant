package com.se.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 学完一门课那一刻的课程画像（四维 + 每章），JSON 存在 data 里；每人每门课一份，之后不再改 */
@Data
@TableName("profile_snapshot")
public class ProfileSnapshot {
    private Long id;
    private Long studentId;
    private Long courseId;
    private String data;
    private LocalDateTime createdAt;
}
