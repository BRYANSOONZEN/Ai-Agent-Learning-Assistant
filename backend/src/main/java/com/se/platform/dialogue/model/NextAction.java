package com.se.platform.dialogue.model;

/** 规则算出的下一步：只由程序决定，不让模型决定 */
public enum NextAction {
    /** 留在本话题，带学生回到问题；不算没进展 */
    CONTINUE,
    /** 给一条提示或追问 */
    HINT,
    /** 三级提示用完还没进展，给一条还缺的要点 */
    GIVE_POINT,
    /** 进入变式检验 */
    START_TRANSFER,
    /** 显示老师的讲解原文，结束本话题 */
    REVEAL,
    /** 变式阶段一直不作答：结束本话题，不显示答案 */
    END_WITHHELD,
    /** 答对，进入下一个话题 */
    NEXT_TOPIC,
    /** 答非所问或一直不作答，跳过本话题，不显示答案 */
    SKIP
}
