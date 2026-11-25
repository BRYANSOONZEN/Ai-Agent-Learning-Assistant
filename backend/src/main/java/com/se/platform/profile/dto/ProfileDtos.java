package com.se.platform.profile.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生画像的返回格式。画像不另外存数据，打开页面时用学习记录 + 掌握度 + 对话结果现算（docs/design.md 首版做法）。
 */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record StudentProfile(Long studentId, String studentName, Stats stats, List<Tag> tags,
                                 List<Dimension> dimensions, List<DayActivity> days, List<QuizPoint> quizTrend) {
    }

    /** 几个总数；accuracy 没答过题时为空 */
    public record Stats(int minutes, int activeDays14, int itemsFinished, int questionsAnswered, Double accuracy,
                        int weakPoints) {
    }

    /** 画像标签。type：behavior 学习行为 / strength 优势 / weakness 薄弱；reason 是打这个标签的依据 */
    public record Tag(String type, String name, String reason) {
    }

    /** 一个维度，value 0～1；数据不够算不出来时 value 为空，reason 说明还缺什么 */
    public record Dimension(String key, String label, Double value, String reason) {
    }

    /** 近 14 天里的一天：学了多少分钟、做了几件事（看内容、交卷、学完） */
    public record DayActivity(LocalDate date, int minutes, int actions) {
    }

    /** 一次交卷，按时间从早到晚 */
    public record QuizPoint(LocalDateTime time, String title, int correct, int total) {
    }

    // ------------------------------------------------------------------ 每章画像 + 课程四维（2026-11-25）

    /** 学生能看画像的课程（选择用） */
    public record CourseOption(Long courseId, String name, int chaptersDone, int chapters) {
    }

    /**
     * 一门课的画像：每章分析 + 课程四维。四维只在全部章节学完后开放（open）。
     *
     * @param refDate 学习持续性的参考日：学完时 = 最后一项完成的日期；没学完时 = 今天
     */
    public record CourseProfile(Long courseId, String courseName, String studentName, boolean open,
                                int chaptersDone, int chapters, LocalDate refDate, LocalDateTime computedAt,
                                List<CourseDimension> dimensions, List<ChapterProfile> chapterProfiles,
                                List<DayActivity> days) {
    }

    /**
     * 四维之一：value 0～1；没有分母时 value 为空，reason 说明原因。numerator / denominator 给页面解释用。
     */
    public record CourseDimension(String key, String label, Double value, int numerator, int denominator,
                                  String formula, String source, String reason) {
    }

    /**
     * 每章画像（学的过程中一直显示，学完是“学习小结”）。
     *
     * @param flowDone    学习流程进度（含 AI 对话）
     * @param contentDone 内容进度（视频、文档、练习；不含 AI 对话）
     * @param correct     第一次作答全对的题数；graded 已判的题数；pending 等老师确认的题数
     */
    public record ChapterProfile(Long chapterId, String title, boolean completed, int flowDone, int flowTotal,
                                 int contentDone, int contentTotal, int exercisesSubmitted, int exercises,
                                 int correct, int graded, int pending, List<PointColor> points, String summary) {
    }

    /** 知识点颜色：green 第一次就掌握 / orange 补回来了 / red 还没掌握 / none 没练过 */
    public record PointColor(Long knowledgePointId, String name, String color, Double firstMastery,
                             String remedyStatus) {
    }
}
