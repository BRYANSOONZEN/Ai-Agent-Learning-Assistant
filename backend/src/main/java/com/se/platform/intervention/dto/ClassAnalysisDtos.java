package com.se.platform.intervention.dto;

import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.dto.LearnDtos.QuizQuestion;
import com.se.platform.diagnosis.dto.DiagnosisDtos.MasteryChange;
import java.time.LocalDateTime;
import java.util.List;

/** 老师干预：全班分析（docs/模块说明/老师干预-全班分析.md） */
public final class ClassAnalysisDtos {

    private ClassAnalysisDtos() {
    }

    /** 一门课的全部练习的分析 */
    public record ClassBoard(Long courseId, String courseName, double minSubmitRatio, List<ItemBoard> items) {
    }

    /**
     * 一次练习。
     *
     * @param status  waiting 还没到截止（也没全交完）/ void 交卷人数不够，作废 / done 已分析 / nodue 没设截止时间
     * @param missing 作废时：没交的学生（老师可以提醒）
     */
    public record ItemBoard(Long itemId, String title, Long chapterId, String chapterTitle, LocalDateTime dueAt,
                            int students, int submitted, String status, List<StudentRef> missing,
                            List<KpGroup> groups, List<PushView> pushes) {
    }

    public record StudentRef(Long id, String name) {
    }

    /** 按知识点分组 */
    public record KpGroup(Long knowledgePointId, String name, List<IssueView> issues) {
    }

    /**
     * 一个问题。
     *
     * @param kind     question 题目检查 / cell 要点 × 考查类型
     * @param rate     错误率 0～1
     * @param method   推荐的讲解形式（按考查类型），question 为空
     * @param advice   给老师看的一句建议
     */
    public record IssueView(Long id, String kind, Integer keyPointNo, String keyPointText, String assessType,
                            String assessLabel, Long exerciseId, String stem, int attempts, int wrong, double rate,
                            int students, String status, String method, String advice) {
    }

    // ------------------------------------------------------------------ 推送（补充讲解 + 新题）

    /** 处理卡片：同一个知识点的几张卡片可以合并；aiExplanation = 让大模型起草讲解（否则老师自己写） */
    public record PushCreate(List<Long> issueIds, boolean aiExplanation) {
    }

    /** 老师修改：讲解、期限、推送对象（不填就是做错的学生） */
    public record PushUpdate(String explanation, LocalDateTime dueAt, List<Long> studentIds) {
    }

    /**
     * 一次推送（老师看，题目带答案）。
     *
     * @param method 按考查类型的讲解形式
     */
    public record PushView(Long id, Long itemId, Long knowledgePointId, String knowledgePointName,
                           List<String> keyPoints, String assessType, String assessLabel, String method,
                           String explanation, String explanationBy, List<ExerciseView> questions,
                           List<StudentRef> students, double beforeRate, String status, LocalDateTime dueAt,
                           LocalDateTime pushedAt, Effect effect) {
    }

    /**
     * 效果：推送对象做新题（第一次作答）的情况。
     *
     * @param afterRate 新题的错误率，和 beforeRate 对比；还没人做为空
     * @param recovered 做完新题的人里，这个知识点已经不是红色的人数（补回来了）
     */
    public record Effect(int targeted, int done, int attempts, int wrong, Double afterRate, int recovered) {
    }

    /** 学生看到的推送：老师补充讲解 + 新题（不带答案） */
    public record MyPush(Long id, String knowledgePointName, List<String> keyPoints, String assessLabel,
                         String explanation, LocalDateTime dueAt, boolean done, List<QuizQuestion> questions) {
    }

    public record PushSubmit(java.util.Map<Long, String> answers) {
    }

    public record PushResult(List<QuestionResult> results, double score, List<MasteryChange> changes) {
    }

    /** 分析作废时：提醒没交的学生（显示在他们的小助手里） */
    public record Remind(String message) {
    }

    public record Extend(LocalDateTime dueAt) {
    }
}
