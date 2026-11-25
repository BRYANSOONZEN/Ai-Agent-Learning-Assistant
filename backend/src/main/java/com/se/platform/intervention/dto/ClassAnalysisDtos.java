package com.se.platform.intervention.dto;

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
                            List<KpGroup> groups) {
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
}
