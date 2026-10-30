package com.se.platform.dialogue.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 某次对话里某个话题的进度（dialogue_topic_state 表）。
 * 规则（DialogueRules）读写这里的计数，总结也从这里来；模型的输出只用来更新它，不直接决定流程。
 */
@Data
@TableName("dialogue_topic_state")
public class DialogueTopicState {

    private Long id;
    private Long sessionId;
    private Integer topicIdx;
    private Long topicId;
    private Integer wrongCount;
    private Integer stuckCount;
    private Integer irrelevantCount;
    private Integer hintLevel;
    /** 已答到的要点序号，逗号分隔，如 "1,3" */
    private String hitPoints;
    /** 话题结果，见 TopicResult；null 表示还没结束 */
    private String result;
}
