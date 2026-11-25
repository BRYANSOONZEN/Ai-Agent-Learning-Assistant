package com.se.platform.assistant.dto;

import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.dto.LearnDtos.QuizQuestion;
import com.se.platform.diagnosis.dto.DiagnosisDtos.MasteryChange;
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
     * 一个知识点（只看练习）。
     *
     * @param level         当前颜色：good 绿 / medium 橙 / weak 红 / none 没练过
     * @param mastery       当前掌握度（补练够 2 道用补练，否则用第一次）
     * @param firstMastery  第一次练习的掌握度（不改）
     * @param remedyStatus  recovered 已补回来 / practicing 补练中 / 空
     * @param remedyRounds  小助手补练做了几轮（含没交的）
     * @param escalated     2 轮补练做完还没补回来（建议看资料、去 AI 对话）
     * @param misconception 这个学生在这个知识点上最常见的误解
     * @param verdict       同 level（没练过为 none），兼容旧前端
     * @param text          给学生看的一句话
     * @param action        remedy 做补练题 / dialogue 去 AI 对话 / 为空 不用做什么
     * @param details       哪种考查类型做错、哪条要点没掌握
     * @param plan          复习方案：看资料（跳到那一秒、那一页）→ 补练题
     */
    public record PointReport(Long knowledgePointId, String name, String level, Double mastery, Double firstMastery,
                              String remedyStatus, int remedyRounds, boolean escalated, int attempts,
                              String misconception, String verdict, String text, String action,
                              List<String> details, List<PlanStep> plan) {
    }

    /**
     * 复习方案的一步。
     *
     * @param kind     review 看资料 / remedy 做补练题 / dialogue 和 AI 同学再讲一次
     * @param text     给学生看的一句话
     * @param itemId   review：去看哪一项（视频、文档）
     * @param startSec review 视频：从第几秒开始
     */
    public record PlanStep(String kind, String text, Long itemId, Integer startSec) {
    }

    // ------------------------------------------------------------------ 自动干预（补练）

    /**
     * 一轮补练：题目不带答案。
     *
     * @param escalated 已经做完 2 轮还没补回来（这时没有题，建议看资料、去 AI 对话）
     * @param text      小助手说的话
     */
    public record RemedyView(Long knowledgePointId, String name, int round, int maxRounds, boolean escalated,
                             List<QuizQuestion> questions, String text) {
    }

    /**
     * 交了一轮补练：每道题的对错、解析，掌握度变化（含补练状态）。
     *
     * @param canContinue 还能做下一轮（没补回来、还没到 2 轮）
     */
    public record RemedyResult(Long knowledgePointId, int round, List<QuestionResult> results, double score,
                               MasteryChange change, boolean recovered, boolean escalated, boolean canContinue,
                               String text) {
    }

    /** 交补练题：题目 id → 答案（和交卷的格式一样） */
    public record RemedySubmit(java.util.Map<Long, String> answers) {
    }

    // ------------------------------------------------------------------ 自由聊天

    /** 学生发一句：哪一章、说了什么 */
    public record ChatSend(Long chapterId, String message) {
    }

    /**
     * 小助手的回复。
     *
     * @param stage   learning 学习中（不出题）/ submitted 练习已交卷
     * @param blocked 大模型原来的回复被程序拦下了（出了题，或者给了答案），换成了固定的话
     */
    public record ChatReply(String reply, String stage, boolean blocked, long latencyMs, double costCny) {
    }

    /** 聊天记录的一句：student / assistant */
    public record ChatLine(String role, String content, LocalDateTime at) {
    }
}
