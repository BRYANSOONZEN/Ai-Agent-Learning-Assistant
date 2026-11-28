package com.se.platform.recommend.dto;

import com.se.platform.course.dto.LearnDtos.QuestionResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习推荐的返回格式。学习推荐是小助手的延伸：这里只放经过小助手的东西，分三类——
 * 系统补充（小助手按错题出的补练题，一律保存）、拓展（学生选了保存的）、老师推送（学生在小助手里接收了的）。
 */
public final class RecommendDtos {

    private RecommendDtos() {
    }

    /** sets：按时间从新到旧；三类的个数分开给 */
    public record RecommendationList(String studentName, int remedyCount, int extendCount, int teacherCount,
                                     List<SavedSet> sets) {
    }

    /**
     * 一组题（小助手的一轮补练、一组拓展，或者老师的一次推送）。
     *
     * @param kind        remedy 系统补充（小助手按错题出的补练）/ extend 拓展 / teacher 老师推送
     * @param round       补练第几轮（拓展、老师推送是 0）
     * @param assessType  考查类型代码（拓展接着做时要用）
     * @param assessLabel 考查类型（拓展、老师推送有）
     * @param status      open 还没做（在学习推荐页里做）/ done 做完了
     * @param saved       拓展：学生选了保存（补练、老师推送一律保存，总是 true）
     * @param score       这一组的得分 0～1（没做完是 null）
     * @param explanation 老师的补充讲解（只有老师推送有）
     * @param at          出题时间（老师推送是推送时间）
     * @param dueAt       老师推送的截止时间
     * @param questions   每道题：题目、我的答案、对错、正确答案、解析、“你可能是这样想的”；没做完时只有题目
     */
    public record SavedSet(Long id, String kind, Long knowledgePointId, String knowledgePointName, Long courseId,
                           String courseName, Long chapterId, String chapterTitle, int round, String assessType,
                           String assessLabel, String status, boolean saved, Double score, String explanation,
                           LocalDateTime at, LocalDateTime dueAt, List<QuestionResult> questions) {
    }

    /** 拓展做完后：保存（true）或者不保存（false） */
    public record SaveRequest(boolean saved) {
    }
}
