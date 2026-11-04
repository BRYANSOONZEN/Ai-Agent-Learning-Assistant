package com.se.platform.intervention.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预警与干预用到的返回格式（docs/计划-干预A和B.md）。
 * 两层状态：concept 概念层（AI 对话）good / fair / bad / none；application 应用层（练习）good / bad / none
 */
public final class InterventionDtos {

    private InterventionDtos() {
    }

    // ------------------------------------------------------------------ 证据（存进 alert.evidence）

    /** 判断时的数据快照 */
    public record Evidence(String concept, String application, Double mastery, int attemptCount, int correctCount,
                           List<TopicEvidence> topics, Long sessionId, List<AnswerEvidence> recentAnswers,
                           String misconception) {
    }

    /** 概念层：该知识点的一个对话话题 */
    public record TopicEvidence(String title, String result, int wrongCount, int stuckCount) {
    }

    /** 应用层：最近的一次作答 */
    public record AnswerEvidence(Long exerciseId, String stem, String chosen, boolean correct, String note,
                                 LocalDateTime time) {
    }

    // ------------------------------------------------------------------ 老师端

    public record Board(Long courseId, String courseName, Counts counts, List<AlertView> alerts,
                        List<InterventionView> interventions) {
    }

    /** 待处理预警、执行中、已完成（有效 / 无效）各几个 */
    public record Counts(int open, int running, int done, int effective, int ineffective) {
    }

    public record AlertView(Long id, Long studentId, String studentName, Long knowledgePointId,
                            String knowledgePointName, String chapterTitle, String type, String level,
                            String message, String status, Evidence evidence, InterventionView suggestion,
                            LocalDateTime createdAt) {
    }

    /** 一条干预。exercises / items：布置的内容和学生完成情况 */
    public record InterventionView(Long id, Long alertId, Long studentId, String studentName, Long knowledgePointId,
                                   String knowledgePointName, String type, int step, String note, String source,
                                   String status, List<AssignedExercise> exercises, List<AssignedItem> items,
                                   AssignedDialogue dialogue, String beforeValue, String afterValue,
                                   Boolean effective, LocalDateTime reviewedAt, LocalDateTime completedAt) {
    }

    /** 布置的一道题；answered：干预通过（或进入第 2 步）之后做过；correct：最近一次做对 */
    public record AssignedExercise(Long exerciseId, Long recommendationId, String stem, List<String> options,
                                   Integer difficulty, boolean answered, boolean correct) {
    }

    /** 要复习的内容项；viewed：干预通过之后看过 */
    public record AssignedItem(Long itemId, Long chapterId, Long courseId, String type, String title,
                               boolean viewed) {
    }

    /** 要重做的 AI 对话；finished：干预通过之后做完了一次 */
    public record AssignedDialogue(Long chapterId, Long itemId, Long courseId, String title, boolean finished) {
    }

    /** 老师审核：approve = true 通过（exerciseIds、note 可改），false 驳回 */
    public record ReviewRequest(Boolean approve, List<Long> exerciseIds, String note) {
    }

    // ------------------------------------------------------------------ 学生端

    /** 学生看到的老师布置：只有执行中的，和最近完成的 */
    public record MyAssignments(List<InterventionView> running, List<InterventionView> done) {
    }
}
