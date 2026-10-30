package com.se.platform.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 推荐给学生的一道题（recommendation 表）。同一个学生同一道题只有一行，状态随作答变化 */
@Data
@TableName("recommendation")
public class Recommendation {

    public static final String EXERCISE = "exercise";
    public static final String NEW = "new";
    /** 做对了 */
    public static final String DONE = "done";
    /** 学生点了"换一道" */
    public static final String DISMISSED = "dismissed";

    private Long id;
    private Long studentId;
    private String targetType;
    private Long targetId;
    private Long knowledgePointId;
    private String reason;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
