package com.se.platform.course.dto;

import com.se.platform.record.dto.ProgressDtos.ProgressView;
import com.se.platform.diagnosis.dto.DiagnosisDtos.MasteryChange;
import java.util.List;
import java.util.Map;

/**
 * 学生端（/api/learn）用到的几个返回格式，放在一个文件里，都是只装数据的 record。
 * 和老师端的区别：题目不带答案、解析和误解说明，提交之后才在 QuizResult 里返回。
 */
public final class LearnDtos {

    private LearnDtos() {
    }

    /** 学习页：课程 + 已发布的章节（每章带内容项，按学习顺序） */
    /** doneItemIds：当前学生学完的内容项（来自学习记录） */
    /**
     * 学习页：doneItemIds 学完的项；lockedItemIds 还锁着的项（上一项没学完）；
     * progress 视频和文档的进度（itemId → 进度），没开始过的项没有。
     */
    public record LearnCourse(CourseView course, List<LearnChapter> chapters, List<Long> doneItemIds,
                              List<Long> lockedItemIds, Map<Long, ProgressView> progress) {
    }

    public record LearnChapter(Long id, String title, String intro, List<ChapterItemView> items) {
    }

    /**
     * 做题时看到的题目：不带答案。type 是题型；两段式多一组 reasons（理由选项）；
     * 判断题的 options 是 ["正确", "错误"]；填空、简答没有选项。
     */
    public record QuizQuestion(Long id, String type, String stem, List<String> options, List<String> reasons) {
    }

    /**
     * 提交：题目 id → 答案。单选、判断 "B"；多选 "AC"；两段式 "B|C"（答案|理由）；填空、简答直接是文字。
     */
    public record QuizSubmit(Map<Long, String> answers) {
    }

    /**
     * 提交后的结果。correct：得分为 1 的题数；score：总得分（多选少选 0.5、简答按要点给分，等老师确认的不算）；
     * pending：简答两次判分不一致、等老师确认的题数。
     * masteryChanges：这次交卷后各知识点掌握度的变化（学情诊断）
     */
    public record QuizResult(int total, int correct, List<QuestionResult> results,
                             List<MasteryChange> masteryChanges, int pending, double score) {

        public QuizResult(int total, int correct, List<QuestionResult> results, List<MasteryChange> masteryChanges) {
            this(total, correct, results, masteryChanges,
                    (int) results.stream().filter(r -> "pending".equals(r.status())).count(),
                    Math.round(results.stream().filter(r -> !"pending".equals(r.status()))
                            .mapToDouble(QuestionResult::score).sum() * 100) / 100.0);
        }
    }

    /**
     * 一道题的判分结果。
     * score 0～1；guess 蒙对的概率、reliability 可信度（只记录，掌握度不用）；status graded / pending。
     * 两段式：reasons、reasonAnswer、reasonChosen；填空：accepts；简答：reference、points（每条要点答到没有）。
     */
    public record QuestionResult(Long id, String type, String stem, List<String> options, String chosen,
                                 String answer, boolean correct, String analysis, String note,
                                 double score, double guess, double reliability, String status,
                                 List<String> reasons, String reasonAnswer, String reasonChosen,
                                 List<String> accepts, String reference, List<PointResult> points) {
    }

    /** 简答的一条要点：hit 答到没有；quote 学生的原话；agreed 两次判得一样 */
    public record PointResult(int no, String text, Boolean hit, String quote, boolean agreed) {
    }
}
