package com.se.platform.dialogue.model;

/** 一个话题最后的结果，总结页按它分组 */
public enum TopicResult {
    /** 没错过、没卡住，自己答对 */
    SELF_CORRECT,
    /** 追问或提示后答对 */
    HINT_CORRECT,
    /** 被给过要点，变式题答全 */
    REVEALED_APPLIED,
    /** 被给过要点，变式题没答全或一直不答；没有变式题的话题也算这个 */
    REVEALED,
    /** 答非所问或一直不作答，跳过 */
    SKIPPED_IRRELEVANT
}
