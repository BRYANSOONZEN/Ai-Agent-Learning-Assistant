package com.se.platform.record.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 视频和文档的学习进度（item_progress 表），每人每项一行 */
@Data
@TableName("item_progress")
public class ItemProgress {

    private Long id;
    private Long studentId;
    private Long itemId;
    private Long courseId;
    /** 视频总时长（秒），第一次上报时定下 */
    private BigDecimal durationSec;
    /** 视频真正播放过的时间段，合并后的 JSON：[[0,12.5],[14,20]] */
    private String watchedRanges;
    /** 各段长度之和（秒） */
    private BigDecimal watchedSec;
    /** 文档累计阅读秒数 */
    private Integer readSec;
    /** 视频上次播放的位置（秒） */
    private BigDecimal lastPosition;
    /** 判定学完的时间，空 = 还没学完 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
