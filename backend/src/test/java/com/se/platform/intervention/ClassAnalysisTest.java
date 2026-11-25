package com.se.platform.intervention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 老师干预：全班分析（docs/模块说明/老师干预-全班分析.md）。
 * 演示数据：第 1 章练习（内容项 2）全班 10 人，截止前交卷 7 人（学生01～07）。
 * 第 1 题（知识点 1）第一次作答：7 人里 5 人错（71%）。测试里把交卷门槛设成 60%。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "platform.class-analysis.min-submit-ratio=0.6")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClassAnalysisTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private ResultActions board() throws Exception {
        return mockMvc.perform(get("/api/class-analysis").param("courseId", "1"));
    }

    private void deadlinePassed() {
        jdbc.update("UPDATE chapter_item SET due_at = TIMESTAMPADD(HOUR, -1, NOW()) WHERE id = 2");
    }

    @Test
    void beforeDeadline_waiting() throws Exception {
        board().andExpect(jsonPath("$.data.items[0].itemId").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("waiting"))
                .andExpect(jsonPath("$.data.items[0].submitted").value(7))
                .andExpect(jsonPath("$.data.items[0].students").value(10));
    }

    @Test
    void afterDeadline_cellCardForKeyPointAndAssessType() throws Exception {
        deadlinePassed();
        // 第 1 题 71%（没超过 80%，不用检查题目）；知识点 1 · 概念辨析 71% > 50% → 卡片，推送给做错的 5 人
        board().andExpect(jsonPath("$.data.items[0].status").value("done"))
                .andExpect(jsonPath("$.data.items[0].groups.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].groups[0].name").value("边界测试值的选取"))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].kind").value("cell"))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].assessLabel").value("概念辨析"))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].method").value("对比讲解"))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].attempts").value(7))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].wrong").value(5))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].students").value(5));
        // 截止后补做的第一次不算进全班分析
        jdbc.update("INSERT INTO answer_record (student_id, exercise_id, course_id, item_id, answer, is_correct, submitted_at)"
                + " VALUES (12, 1, 1, 2, 'A', 1, NOW())");
        board().andExpect(jsonPath("$.data.items[0].submitted").value(7));
    }

    @Test
    void questionOver80_checkedFirst_thenBackToCell() throws Exception {
        // 学生04 第 1 题也做错 → 7 人里 6 人错（86%）> 80% → 先给老师检查，这道题暂时不算进要点 × 考查类型
        jdbc.update("UPDATE answer_record SET answer = 'B', is_correct = 0 WHERE student_id = 8 AND exercise_id = 1");
        deadlinePassed();
        String body = board().andExpect(jsonPath("$.data.items[0].groups[0].issues.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].kind").value("question"))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].exerciseId").value(1))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[0].advice", Matchers.containsString("先检查这道题")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.items[0].groups[0].issues[0].id");
        // 老师检查：题目没问题 → 算回要点 × 考查类型，出卡片
        mockMvc.perform(post("/api/class-issues/" + id + "/ok")).andExpect(jsonPath("$.code").value(200));
        board().andExpect(jsonPath("$.data.items[0].groups[0].issues[1].kind").value("cell"))
                .andExpect(jsonPath("$.data.items[0].groups[0].issues[1].wrong").value(6));
        mockMvc.perform(post("/api/class-issues/" + id + "/ok")).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void voidQuestion_removedAndMasteryRecomputed() throws Exception {
        jdbc.update("UPDATE answer_record SET answer = 'B', is_correct = 0 WHERE student_id = 8 AND exercise_id = 1");
        deadlinePassed();
        String body = board().andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.items[0].groups[0].issues[0].id");
        mockMvc.perform(post("/api/class-issues/" + id + "/void")).andExpect(jsonPath("$.code").value(200));
        assertThat(jdbc.queryForObject("SELECT deleted FROM exercise WHERE id = 1", Integer.class)).isEqualTo(1);
        // 学生01 知识点 1 只做过第 1 题：作废后没有算得上的作答了
        assertThat(jdbc.queryForObject("SELECT first_count FROM mastery WHERE student_id = 5 AND knowledge_point_id = 1",
                Integer.class)).isZero();
        board().andExpect(jsonPath("$.data.items[0].groups[0].issues[0].status").value("voided"));
    }

    @Test
    void tooFewSubmitted_analysisVoid_teacherSeesMissing() throws Exception {
        jdbc.update("DELETE FROM answer_record WHERE item_id = 2 AND student_id IN (8, 9, 10, 11)");
        deadlinePassed();
        // 3/10 = 30% < 60% → 作废，不出卡片，列出没交的 7 人
        board().andExpect(jsonPath("$.data.items[0].status").value("void"))
                .andExpect(jsonPath("$.data.items[0].submitted").value(3))
                .andExpect(jsonPath("$.data.items[0].missing.length()").value(7))
                .andExpect(jsonPath("$.data.items[0].groups.length()").value(0));
        // 延长截止时间 → 重新等；新的截止过了，延期前交的都算
        jdbc.update("UPDATE chapter_item SET due_at = TIMESTAMPADD(DAY, 1, NOW()) WHERE id = 2");
        board().andExpect(jsonPath("$.data.items[0].status").value("waiting"));
        jdbc.update("INSERT INTO answer_record (student_id, exercise_id, course_id, item_id, answer, is_correct, submitted_at)"
                + " VALUES (8, 1, 1, 2, 'A', 1, TIMESTAMPADD(MINUTE, -2, NOW())), (9, 1, 1, 2, 'A', 1, TIMESTAMPADD(MINUTE, -2, NOW())),"
                + " (10, 1, 1, 2, 'A', 1, TIMESTAMPADD(MINUTE, -2, NOW()))");
        jdbc.update("UPDATE chapter_item SET due_at = TIMESTAMPADD(MINUTE, -1, NOW()) WHERE id = 2");
        board().andExpect(jsonPath("$.data.items[0].status").value("done"))
                .andExpect(jsonPath("$.data.items[0].submitted").value(6));
    }

    @Test
    void ignoreCard() throws Exception {
        deadlinePassed();
        String body = board().andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data.items[0].groups[0].issues[*].id");
        mockMvc.perform(post("/api/class-issues/" + ids.get(0) + "/ignore")).andExpect(jsonPath("$.code").value(200));
        board().andExpect(jsonPath("$.data.items[0].groups[0].issues[0].status").value("ignored"));
    }

    @Test
    void publishNeedsExerciseDeadline() throws Exception {
        jdbc.update("UPDATE chapter_item SET due_at = NULL WHERE id = 2");
        mockMvc.perform(put("/api/chapters/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"第 1 章 边界值分析\", \"status\": \"published\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("截止时间")));
    }
}
