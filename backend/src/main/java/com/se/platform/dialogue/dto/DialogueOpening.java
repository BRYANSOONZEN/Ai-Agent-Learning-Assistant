package com.se.platform.dialogue.dto;

import java.util.List;

/** 开场页：本章要讨论的话题列表 */
public record DialogueOpening(Long chapterId, List<String> topicTitles) {
}
