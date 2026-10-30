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
}
