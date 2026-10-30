package com.se.platform.dialogue.model;

import java.util.List;

/**
 * 模型一轮返回的内容：判断类型、这句答到了哪几条要点、写给学生的一句话。
 *
 * @param hitPoints 要点编号，从 1 开始
 */
public record Judgement(JudgedType type, List<Integer> hitPoints, String reply) {
}
