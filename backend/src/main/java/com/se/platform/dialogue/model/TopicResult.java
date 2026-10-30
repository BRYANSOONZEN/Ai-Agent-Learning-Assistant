package com.se.platform.dialogue.model;

/** 一个话题最后的结果，总结页按它分组 */
public enum TopicResult {
    /** 没错过、没卡住，自己答对 */
    SELF_CORRECT,
    /** 提示后答对 */
    HINT_CORRECT,
    /** 错两次或卡住两次，看了讲解 */
    REVEALED,
    /** 答非所问两次，跳过 */
    SKIPPED_IRRELEVANT
}
