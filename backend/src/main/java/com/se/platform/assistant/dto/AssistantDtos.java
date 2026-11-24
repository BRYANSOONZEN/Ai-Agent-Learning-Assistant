package com.se.platform.assistant.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人学习助手的返回格式（docs/计划-个人学习助手.md）。
 * 只装这个学生自己的数据，不含题目答案、话题要点和其他学生的任何数据。
 */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    /**
     * 助手面板要的全部内容。
     *
     * @param chapters       学生开始学过的章节，最近学的在前
     * @param focusChapterId 面板默认展开哪一章：最近学完的一章；还没学完任何一章时，是最近在学的一章
     */
    public record Summary(List<ChapterProgress> chapters, Long focusChapterId) {
    }

    /**
     * 一章的进度。学完 = 这一章所有学习项都完成过一次。
     *
     * @param nextItemId     没学完时，按学习顺序下一个还没完成的学习项
     * @param dialogueItemId 这一章的 AI 对话项，用来"巩固一下"
     * @param report         学完了才有：个人总结
     */
    public record ChapterProgress(Long courseId, String courseName, Long chapterId, String title, int done,
                                  int total, boolean completed, LocalDateTime lastAt, Long nextItemId,
                                  String nextItemTitle, Long dialogueItemId, ChapterReport report) {
    }

    /**
     * 学完一章后的个人总结。
     *
     * @param headline 一句话总结
     * @param good     稳了的知识点个数
     * @param weak     需要补的知识点个数
     */
    public record ChapterReport(String headline, int good, int weak, List<PointReport> points) {
    }

    /**
     * 一个知识点的两层判断和建议。
     *
     * @param concept       概念层（AI 对话）：good / fair / bad / none
     * @param application   应用层（练习）：good / bad / none
     * @param mastery       练习掌握度，没练过为空
     * @param misconception 这个学生在这个知识点上反复出现的误解（至少 2 次），没有为空
     * @param verdict       good 稳了 / apply 会讲不会用 / concept 概念没讲出来 / both 两层都差 /
     *                      few 练得太少 / none 没有数据
     * @param text          给学生看的一句话
     * @param action        practice 去做题 / dialogue 先巩固（对话）/ 为空 不用做什么
     * @param details       从练习里看出的具体不足：哪种考查类型做错、哪条要点没掌握
     * @param plan          自动给出的复习方案（不经过老师），按顺序做
     */
    public record PointReport(Long knowledgePointId, String name, String concept, String application,
                              Double mastery, int attempts, String misconception, String verdict, String text,
                              String action, List<String> details, List<PlanStep> plan) {
    }

    /**
     * 复习方案的一步。
     *
     * @param kind     review 看资料 / practice 做题 / dialogue 和 AI 同学再讲一次
     * @param text     给学生看的一句话
     * @param itemId   review：去看哪一项（视频、文档）
     * @param startSec review 视频：从第几秒开始
     */
    public record PlanStep(String kind, String text, Long itemId, Integer startSec) {
    }
}
