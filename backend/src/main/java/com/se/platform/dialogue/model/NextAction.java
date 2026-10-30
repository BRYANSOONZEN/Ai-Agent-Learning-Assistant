package com.se.platform.dialogue.model;

/** 规则算出的下一步：只由程序决定，不让模型决定 */
public enum NextAction {
    /** 留在本话题，重复问题或换简单问法 */
    CONTINUE,
    /** 给一条提示 */
    HINT,
    /** 显示老师的讲解原文，结束本话题 */
    REVEAL,
    /** 答对，进入下一个话题 */
    NEXT_TOPIC,
    /** 连续答非所问，跳过本话题 */
    SKIP
}
