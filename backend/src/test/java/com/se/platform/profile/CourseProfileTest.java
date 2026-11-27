package com.se.platform.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.se.platform.common.DemoIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 课程四维画像（docs/模块说明/学生画像-设计.md）：持续性、完成度、按时完成率、有效作答正确率。
 * 12 号是种子数据里什么都没学的学生；第 1 章 = 文档(1) + 练习(2) + AI 对话(3)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CourseProfileTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private ResultActions profile() throws Exception {
        return mockMvc.perform(get("/api/profiles/me/courses/1").header(DemoIdentity.HEADER, "12"));
    }

    private void finished(long itemId) {
        jdbc.update("INSERT INTO learning_event (student_id, event_type, target_id, course_id, created_at) "
                + "VALUES (12, 'finish_item', ?, 1, NOW())", itemId);
    }

    private static String dim(String key) {
        return "$.data.dimensions[?(@.key == '" + key + "')]";
    }

    /** 交一次卷：wrongFirst = 第一题故意答错 */
    private void submit(boolean wrongFirst) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, answer FROM exercise WHERE item_id = 2 ORDER BY id");
        Map<String, String> answers = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            String right = (String) rows.get(i).get("answer");
            answers.put(String.valueOf(rows.get(i).get("id")), wrongFirst && i == 0 ? ("A".equals(right) ? "B" : "A") : right);
        }
        StringBuilder json = new StringBuilder("{\"answers\": {");
        answers.forEach((k, v) -> json.append('"').append(k).append("\": \"").append(v).append("\","));
        json.setLength(json.length() - 1);
        json.append("}}");
        mockMvc.perform(post("/api/learn/items/2/submit").header(DemoIdentity.HEADER, "12")
                        .contentType(MediaType.APPLICATION_JSON).content(json.toString()))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void nothingLearned_noDataInsteadOfZero() throws Exception {
        profile().andExpect(jsonPath("$.data.open").value(false))
                .andExpect(jsonPath("$.data.chaptersDone").value(0))
                .andExpect(jsonPath(dim("consistency") + ".value").value(Matchers.contains(0.0)))
                // 完成度 = 视频、文档、练习：0/2（AI 对话不算）
                .andExpect(jsonPath(dim("completion") + ".numerator").value(Matchers.contains(0)))
                .andExpect(jsonPath(dim("completion") + ".denominator").value(Matchers.contains(2)))
                // 没有过了截止的练习、没有已判的作答：显示“暂无数据”（null），不用 0 补
                .andExpect(jsonPath(dim("onTime") + ".value").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath(dim("accuracy") + ".value").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$.data.snapshot").value(Matchers.nullValue())); // 没学完不存快照
    }

    @Test
    void allLearned_opensCourseProfile_onlyFirstAttemptCounts() throws Exception {
        finished(1);
        finished(3);
        int n = jdbc.queryForObject("SELECT COUNT(*) FROM exercise WHERE item_id = 2", Integer.class);
        submit(true);    // 第一次：第一题错
        submit(false);   // 重做全对：不算进正确率

        profile().andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.chaptersDone").value(1))
                .andExpect(jsonPath(dim("completion") + ".value").value(Matchers.contains(1.0)))
                // 每道题只看第一次：n 题里 n-1 题全对
                .andExpect(jsonPath(dim("accuracy") + ".numerator").value(Matchers.contains(n - 1)))
                .andExpect(jsonPath(dim("accuracy") + ".denominator").value(Matchers.contains(n)))
                // 今天交过已判的题：1 个有效学习日，1/7
                .andExpect(jsonPath(dim("consistency") + ".numerator").value(Matchers.contains(1)))
                .andExpect(jsonPath(dim("consistency") + ".value").value(Matchers.contains(0.1429)))
                .andExpect(jsonPath("$.data.days.length()").value(14))
                // 学完了：存一份快照，四维和当时一样
                .andExpect(jsonPath("$.data.snapshot.savedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.snapshot.dimensions[?(@.key == 'accuracy')].numerator").value(Matchers.contains(n - 1)));
        // 之后老师加了新章节：学生没学完，四维关上，但快照还在、内容不变，也不会再存第二份
        jdbc.update("INSERT INTO chapter (course_id, title, intro, status, sort_order, created_at, updated_at) "
                + "VALUES (1, '新章', '介绍', 'published', 9, NOW(), NOW())");
        long chId = jdbc.queryForObject("SELECT MAX(id) FROM chapter", Long.class);
        jdbc.update("INSERT INTO chapter_item (course_id, chapter_id, type, title, description, sort_order, created_at, updated_at) "
                + "VALUES (1, ?, 'document', '新文档', '正文', 0, NOW(), NOW())", chId);
        profile().andExpect(jsonPath("$.data.open").value(false))
                .andExpect(jsonPath("$.data.snapshot.dimensions[?(@.key == 'completion')].value").value(Matchers.contains(1.0)));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM profile_snapshot WHERE student_id = 12", Integer.class));
    }

    @Test
    void onTime_firstSubmissionBeforeDeadline() throws Exception {
        finished(1);
        finished(3);
        submit(false);
        // 截止时间在 1 天后：还没过截止，不算进按时完成率
        jdbc.update("UPDATE chapter_item SET due_at = DATEADD('DAY', 1, NOW()) WHERE id = 2");
        profile().andExpect(jsonPath(dim("onTime") + ".value").value(Matchers.contains(Matchers.nullValue())));
        // 改成已经过了截止（1 小时前），第一次交卷在那之前：按时
        jdbc.update("UPDATE answer_record SET submitted_at = DATEADD('HOUR', -3, NOW()) WHERE student_id = 12");
        jdbc.update("UPDATE chapter_item SET due_at = DATEADD('HOUR', -1, NOW()) WHERE id = 2");
        profile().andExpect(jsonPath(dim("onTime") + ".value").value(Matchers.contains(1.0)));
        // 第一次交卷在截止之后：不按时
        jdbc.update("UPDATE chapter_item SET due_at = DATEADD('HOUR', -5, NOW()) WHERE id = 2");
        profile().andExpect(jsonPath(dim("onTime") + ".value").value(Matchers.contains(0.0)));
    }
}
