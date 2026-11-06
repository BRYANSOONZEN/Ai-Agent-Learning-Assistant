package com.se.platform.diagnosis.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学情诊断用到的返回格式，都是只装数据的 record。
 * level：good 掌握（≥ 0.8）/ medium 一般（0.6～0.8）/ weak 薄弱（< 0.6）/ none 还没练过
 */
public final class DiagnosisDtos {

    private DiagnosisDtos() {
    }

    /** 交卷后一个知识点的掌握度变化；before 为空 = 第一次练到 */
    public record MasteryChange(Long knowledgePointId, String name, Double before, Double after, String level) {
    }

    // ------------------------------------------------------------------ 学生诊断

    public record StudentDiagnosis(Long studentId, String studentName, Summary summary, List<PointDiagnosis> weakPoints,
                                   List<CourseDiagnosis> courses, List<TopicOutcome> dialogueTopics) {
    }

    /** 知识点按等级各几个 */
    public record Summary(int total, int good, int medium, int weak, int none) {
    }

    public record CourseDiagnosis(Long courseId, String courseName, List<ChapterDiagnosis> chapters) {
    }

    public record ChapterDiagnosis(Long chapterId, String title, List<PointDiagnosis> points) {
    }

    /**
     * 一个知识点的诊断。recent：最近几次作答对错，从早到晚；misconception：最近一次答错时选项暴露的误解；
     * practiceItemId：可以回去重做的练习（chapter_item.id）
     */
    public record PointDiagnosis(Long knowledgePointId, String name, Long courseId, Long chapterId,
                                 String chapterTitle, boolean key, boolean difficult, Double mastery, String level,
                                 int attemptCount, int correctCount, List<Boolean> recent, String misconception,
                                 Long practiceItemId) {
    }

    /** AI 对话里一个话题的结果：SELF_CORRECT / HINT_CORRECT / REVEALED_APPLIED / REVEALED / SKIPPED_IRRELEVANT */
    public record TopicOutcome(String topicTitle, String itemTitle, String result, int wrongCount, int stuckCount,
                               LocalDateTime time) {
    }

    // ------------------------------------------------------------------ 课程诊断（老师看）

    public record CourseOverview(Long courseId, String courseName, int studentCount, List<ClassPoint> points) {
    }

    /** 全体学生在一个知识点上的情况；misconceptions：答错时最常见的误解，多的在前 */
    public record ClassPoint(Long knowledgePointId, String name, String chapterTitle, boolean key, boolean difficult,
                             Double avgMastery, String level, int practicedStudents, int weakStudents,
                             List<Misconception> misconceptions) {
    }

    public record Misconception(String note, int count) {
    }
}
