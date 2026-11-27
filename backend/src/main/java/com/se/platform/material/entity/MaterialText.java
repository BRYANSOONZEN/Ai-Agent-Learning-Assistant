package com.se.platform.material.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** 资料转出来的一段文字（material_text 表）：课件一页、一张图、视频 30～60 秒，或老师写在页面上的文字 */
@Data
@TableName("material_text")
public class MaterialText {

    private Long id;
    private Long itemId;
    private Long chapterId;
    /** 这份资料里的编号：P4、P13-图1、01 */
    private String segKey;
    /** pdf / image / video / text */
    private String kind;
    private Integer page;
    private BigDecimal startSec;
    private BigDecimal endSec;
    private String content;
    /** 同音字纠正前的原文（VideoTermFixer 改过才有） */
    private String original;
    private Integer sortOrder;
}
