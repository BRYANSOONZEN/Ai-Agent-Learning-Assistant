package com.se.platform.ai.log;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 一次模型调用的记录（ai_call_log 表）：token、费用、耗时、是否成功 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    private Long id;
    private String task;
    private String model;
    private String promptVersion;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal costCny;
    private Integer latencyMs;
    private Boolean success;
    private Boolean passedCheck;
    private String error;
    private String requestJson;
    private String responseJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
