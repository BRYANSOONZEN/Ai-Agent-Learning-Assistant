package com.se.platform.record.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「学习记录」页用到的返回格式，都是只装数据的 record。
 */
public final class RecordDtos {

    private RecordDtos() {
    }

    /** POST /api/learn/events 的请求体：type = view_item / finish_item */
    public record EventRequest(String type, Long itemId, Integer durationSec) {
    }

    /** 学习记录页一次拿全 */
    public record RecordOverview(Summary summary, List<CourseProgress> courses, List<QuizAttempt> quizzes,
                                 List<DialogueRecord> dialogues, List<TimelineEntry> timeline) {
    }

    /** 顶部的几个总数 */
    public record Summary(int minutes, int activeDays, int itemsFinished, int quizAttempts,
                          int questionsAnswered, int correctAnswers, int dialogues) {
    }

    /** 每门学过的课：学完几项 / 一共几项，学了多少分钟 */
    public record CourseProgress(Long courseId, String courseName, int done, int total, int minutes) {
    }

    /** 一次交卷 */
    public record QuizAttempt(Long itemId, String title, String courseName, String chapterTitle,
                              LocalDateTime submittedAt, int correct, int total, List<WrongAnswer> wrong) {
    }

    /** 交卷里答错的一题 */
    public record WrongAnswer(String stem, String chosen, String answer) {
    }

    /** 一次 AI 对话 */
    public record DialogueRecord(Long sessionId, String title, String courseName, String chapterTitle,
                                 String status, int studentMessages, LocalDateTime startedAt,
                                 LocalDateTime updatedAt) {
    }

    /**
     * 时间线上的一条。kind：study 学习了一段时间 / finish 学完一项 / quiz 交卷 / dialogue AI 对话
     */
    public record TimelineEntry(LocalDateTime time, String kind, String title, String courseName,
                                String chapterTitle, String detail) {
    }
}
