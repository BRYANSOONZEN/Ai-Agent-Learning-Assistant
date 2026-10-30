package com.se.platform.dialogue.model;

/**
 * 模型对学生这句话的判断，只能是这 6 种之一。
 *
 * <p>{@code enum}（枚举）：取值固定的类型，写错名字编译就会报错，比到处写字符串 "correct" 安全。
 */
public enum JudgedType {
    /** 要点全部答到 */
    CORRECT,
    /** 答到一部分 */
    PARTIAL,
    /** 相关，但答错 */
    WRONG,
    /** 和问题无关，如"哈哈"、闲聊 */
    IRRELEVANT,
    /** 直接要答案 */
    WANT_ANSWER,
    /** 问资料以外的问题 */
    OFF_TOPIC
}
