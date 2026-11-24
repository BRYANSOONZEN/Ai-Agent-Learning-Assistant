package com.se.platform.record.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 截止时间相关的返回数据 */
public final class DeadlineDtos {

    private DeadlineDtos() {
    }

    /**
     * 学生端：一项还没做、有截止时间的任务。
     * kind：item 章节里的练习 / AI 对话；intervention 老师布置的干预任务（这时 itemId 为空，interventionId 有值）。
     * state：overdue 已过期 / soon 快到期（离截止不到 remind-days 天）/ later 还早。
     * minutesLeft：离截止还有几分钟，已过期是负数。
     */
    public record StudentDeadline(String kind, Long itemId, Long interventionId, Long courseId, String courseName,
                                  Long chapterId, String chapterTitle, String type, String title, LocalDateTime dueAt,
                                  boolean allowLate, String state, long minutesLeft) {
    }

    /** 没做的学生 */
    public record Student(Long id, String username, String name) {
    }

    /**
     * 老师端：一项设了截止时间的内容，全班完成情况。
     * total：应做人数（现在没有选课表，= 全部正常状态的学生）；done：已完成；late：完成了但晚于截止时间；
     * missing：还没做的学生。
     */
    public record ItemDeadline(Long itemId, Long courseId, String courseName, Long chapterId, String chapterTitle,
                               String type, String title, LocalDateTime dueAt, boolean allowLate, String state,
                               int total, int done, int late, List<Student> missing) {
    }
}
