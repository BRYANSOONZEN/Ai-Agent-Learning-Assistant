package com.se.platform.recommend.dto;

import java.util.List;
import java.util.Map;

/**
 * 学习推荐的返回格式。按薄弱知识点分组：为什么推荐（掌握度、误解）、先回看什么、再做哪几道题。
 */
public final class RecommendDtos {

    private RecommendDtos() {
    }

    /** weakCount：薄弱知识点个数；doneCount：推荐过、已经做对的题数 */
    public record RecommendationList(String studentName, int weakCount, int doneCount,
                                     List<PointRecommendation> points) {
    }

    /**
     * 一个薄弱知识点。content：老师写的知识点说明；review：本章可以回看的视频 / 文档；
     * exercises：推荐的题（可能为空，说明这个知识点的题都做完了）
     */
    public record PointRecommendation(Long knowledgePointId, String name, Long courseId, String courseName,
                                      Long chapterId, String chapterTitle, Double mastery, String level,
                                      int attemptCount, int correctCount, String misconception, String content,
                                      List<ReviewLink> review, List<RecExercise> exercises) {
    }

    /** 回看的内容项（chapter_item，type 是 video / document） */
    public record ReviewLink(Long itemId, String type, String title) {
    }

    /** 推荐的一道题，不带答案；reason：上次做错了 / 还没做过 / 以前做错过 */
    public record RecExercise(Long recommendationId, Long exerciseId, String stem, List<String> options,
                              Integer difficulty, String reason) {
    }

    /** 做推荐的题：题目 id → 选的字母，可以只交一道 */
    public record PracticeRequest(Map<Long, String> answers) {
    }

    /** 反馈：done 已完成 / dismissed 换一道 */
    public record FeedbackRequest(String status) {
    }
}
