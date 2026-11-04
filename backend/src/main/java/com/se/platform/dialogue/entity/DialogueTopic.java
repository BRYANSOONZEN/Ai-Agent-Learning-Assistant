package com.se.platform.dialogue.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 实体类：和 dialogue_topic 表一一对应，一个对象 = 表里一行。
 *
 * <p>{@code @TableName}：告诉 MyBatis-Plus 对应哪张表。
 * 字段名用驼峰（keyPoints），yml 里开了 map-underscore-to-camel-case，会自动对应列 key_points。
 *
 * <p>keyPoints 和 hints 在库里存的是 JSON 字符串，这里也用 String；
 * 转成 List 的工作放在 Service 里做，实体类只负责和表对应。
 */
@Data
@TableName("dialogue_topic")
public class DialogueTopic {

    private Long id;
    private Long chapterId;
    /** 属于哪个 AI 对话内容项 → chapter_item.id；老的独立话题页建的话题为空 */
    private Long itemId;
    /** 考哪个知识点（概念层），干预按它和练习题（应用层）对上 */
    private Long knowledgePointId;
    private Integer sortOrder;
    private String title;
    private String question;
    private String simpleQuestion;
    private String keyPoints;
    private String hints;
    private String revealText;
    private String sourceRef;
    private String sourceText;
    private Boolean createdByAi;
    private Boolean enabled;

    /** 插入时由 MybatisPlusConfig 里的 timeFillHandler 自动填当前时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，yml 里全局配置了 deleted 字段，删除时自动改成 1 */
    private Integer deleted;
}
