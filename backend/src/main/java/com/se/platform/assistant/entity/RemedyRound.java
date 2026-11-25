package com.se.platform.assistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 小助手自动干预的一轮补练（remedy_round 表）：一个学生在一个知识点上，第几轮、这一轮的补练题。
 * 设计见 docs/模块说明/小助手-自动干预与聊天.md 第四节。
 */
@Data
@TableName("remedy_round")
public class RemedyRound {

    public static final String OPEN = "open";
    public static final String DONE = "done";

    private Long id;
    private Long studentId;
    private Long knowledgePointId;
    /** remedy 补练 / extend 拓展 */
    private String kind;
    /** 拓展：学生选的考查类型 */
    private String assessType;
    private Integer roundNo;
    /** 这一轮的补练题 id，逗号分隔 */
    private String exerciseIds;
    /** open 还没交 / done 交了 */
    private String status;
    /** 这一轮做完后：平均分 */
    private Double score;
    /** 2 轮补练做完还没补回来（只记录，老师能看到；2026-11-25 起不转给老师处理） */
    private Boolean escalated;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
