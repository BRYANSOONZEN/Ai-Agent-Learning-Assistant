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

    public static final String ANSWER = "ANSWER";
    public static final String TRANSFER = "TRANSFER";

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
    /** ANSWER 作答阶段 / TRANSFER 变式检验阶段 */
    private String phase = ANSWER;
    /** 当前阶段已经走了几轮 */
    private Integer turnCount = 0;
    /** 真正作答过几次（要答案、闲聊不算） */
    private Integer answerCount = 0;
    /** 不作答只要答案的次数；进入变式阶段时清零 */
    private Integer refuseCount = 0;
    /** 程序已经告诉学生的要点序号，不算学生答到 */
    private String givenPoints;
    /** 变式题已答到的要点序号 */
    private String transferHitPoints;
    /** 变式题作答了几次 */
    private Integer transferTries = 0;
    /** 模型最近发现的错误想法：用来写追问，也给老师看，不算进展 */
    private String misconception;
}
