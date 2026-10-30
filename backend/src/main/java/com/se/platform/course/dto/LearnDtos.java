package com.se.platform.course.dto;

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
    public record LearnCourse(CourseView course, List<LearnChapter> chapters, List<Long> doneItemIds) {
    }

    public record LearnChapter(Long id, String title, String intro, List<ChapterItemView> items) {
    }

    /** 做题时看到的题目：只有题干和选项 */
    public record QuizQuestion(Long id, String stem, List<String> options) {
    }

    /** 提交：题目 id → 选的字母，如 {"1": "A", "2": "C"} */
    public record QuizSubmit(Map<Long, String> answers) {
    }

    /** 提交后的结果：做对几道，每道题的对错、正确答案、解析，选错时附上那个选项的误解说明 */
    /** masteryChanges：这次交卷后各知识点掌握度的变化（学情诊断） */
    public record QuizResult(int total, int correct, List<QuestionResult> results,
                             List<MasteryChange> masteryChanges) {
    }

    public record QuestionResult(Long id, String stem, List<String> options, String chosen, String answer,
                                 boolean correct, String analysis, String note) {
    }
}
