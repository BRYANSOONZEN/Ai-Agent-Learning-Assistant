package com.se.platform.dialogue.model;

import java.util.List;

/**
 * 模型一轮返回的内容：判断类型、这句答到了哪几条要点、学生的错误想法、写给学生的一句话。
 *
 * @param hitPoints     要点编号，从 1 开始
 * @param misconception 学生的错误想法，没有就是空字符串；只记录，不算进展
 */
public record Judgement(JudgedType type, List<Integer> hitPoints, String misconception, String reply) {
}
