package com.se.platform.intervention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import com.se.platform.ai.client.FakeAiClient;
import com.se.platform.common.DemoIdentity;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

    @TestConfiguration
    static class FakeAiConfig {
        @Bean
        @Primary
        FakeAiClient fakeAiClient() {
            return new FakeAiClient();
        }
    }

    @Autowired
    private FakeAiClient fake;
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

    // ------------------------------------------------------------------ 推送：补充讲解 + 新题

    private static MockHttpServletRequestBuilder as(long studentId, MockHttpServletRequestBuilder req) {
        return req.header(DemoIdentity.HEADER, String.valueOf(studentId));
    }

    private static final String THREE = """
            {"questions": [
              {"type": "single_choice", "assess_type": "concept", "key_point_no": 1, "stem": "关于边界外的值，哪个说法对？",
               "options": ["也要测", "不用测", "只测中间", "随便"], "answer": "A",
               "option_notes": ["", "以为只测边界内", "以为中间最好", "乱选"], "analysis": "边界外也要测"},
              {"type": "judge", "assess_type": "concept", "key_point_no": 1, "stem": "边界外的值不用测。",
               "options": ["正确", "错误"], "answer": "B", "option_notes": ["以为只测边界内", ""], "analysis": "要测"},
              {"type": "single_choice", "assess_type": "concept", "key_point_no": 1, "stem": "测 1～99 的下边界选哪组？",
               "options": ["0、1、2", "1、2、3", "50", "99"], "answer": "A",
               "option_notes": ["", "缺边界外", "中间值", "上边界"], "analysis": "0 是边界外"}]}""";

    @Test
    void cardHandled_draftEdited_pushed_studentDoes_effectShown() throws Exception {
        jdbc.update("""
                INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, misconceptions,
                  sources, edited, knowledge_point_id, created_at, updated_at)
                VALUES (1, 0, '边界测试值的选取', '', '重点',
                  '[{"text":"边界外的值也要测，比如 0","sources":["D1-T0"]}]', '["测中间值最有代表性"]', '[]', 0, 1, NOW(), NOW())""");
        deadlinePassed();
        String body = board().andReturn().getResponse().getContentAsString();
        int issueId = JsonPath.read(body, "$.data.items[0].groups[0].issues[0].id");

        // 大模型按考查类型（概念辨析 → 对比讲解）起草讲解，再出 3 道新题（草稿，老师确认）
        fake.reply("边界外和边界上是两回事……原资料：《阅读：边界值分析要点》").then(THREE)
                .then("{\"answers\": [\"A\", \"B\", \"A\"]}");
        int before = fake.requests().size();
        String push = mockMvc.perform(post("/api/class-pushes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueIds\": [" + issueId + "], \"aiExplanation\": true}"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.method").value("对比讲解"))
                .andExpect(jsonPath("$.data.explanationBy").value("ai"))
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[0].answer").value("A")) // 老师看得到答案
                .andExpect(jsonPath("$.data.students.length()").value(5))
                .andExpect(jsonPath("$.data.beforeRate").value(0.71))
                .andReturn().getResponse().getContentAsString();
        String prompt = fake.requests().get(before).messages().get(0).content();
        assertThat(prompt).contains("对比讲解").contains("概念辨析").contains("报名年龄要求为 18～60 岁");
        int pushId = JsonPath.read(push, "$.data.id");

        // 草稿学生看不到；老师改讲解后推送
        mockMvc.perform(as(5, get("/api/assistant/pushes"))).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(put("/api/class-pushes/" + pushId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"explanation\": \"老师改过的讲解：边界外的值也要测。\"}"))
                .andExpect(jsonPath("$.data.explanation").value("老师改过的讲解：边界外的值也要测。"));
        mockMvc.perform(post("/api/class-pushes/" + pushId + "/push")).andExpect(jsonPath("$.data.status").value("pushed"));
        board().andExpect(jsonPath("$.data.items[0].groups[0].issues[0].status").value("pushed"))
                .andExpect(jsonPath("$.data.items[0].pushes[0].id").value(pushId));

        // 学生01（做错过）在小助手里看到，做新题；学生04（没做错）看不到
        String mine = mockMvc.perform(as(5, get("/api/assistant/pushes")))
                .andExpect(jsonPath("$.data[0].explanation").value("老师改过的讲解：边界外的值也要测。"))
                .andExpect(jsonPath("$.data[0].questions[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data[0].done").value(false))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(as(8, get("/api/assistant/pushes"))).andExpect(jsonPath("$.data.length()").value(0));
        List<Integer> qids = JsonPath.read(mine, "$.data[0].questions[*].id");
        String answers = "{\"answers\": {\"" + qids.get(0) + "\": \"A\", \"" + qids.get(1) + "\": \"B\", \""
                + qids.get(2) + "\": \"A\"}}";
        mockMvc.perform(as(5, post("/api/assistant/pushes/" + pushId + "/submit")).contentType(MediaType.APPLICATION_JSON)
                        .content(answers))
                .andExpect(jsonPath("$.data.score").value(1.0))
                .andExpect(jsonPath("$.data.changes[0].color").value("orange")); // 补回来了：红 → 橙
        mockMvc.perform(as(5, post("/api/assistant/pushes/" + pushId + "/submit")).contentType(MediaType.APPLICATION_JSON)
                .content(answers)).andExpect(jsonPath("$.code").value(400));

        // 效果：推给 5 人，1 人做完，新题错误率 0（推送前 0.71），做完的人里 1 人补回来了
        board().andExpect(jsonPath("$.data.items[0].pushes[0].effect.targeted").value(5))
                .andExpect(jsonPath("$.data.items[0].pushes[0].effect.done").value(1))
                .andExpect(jsonPath("$.data.items[0].pushes[0].effect.afterRate").value(0.0))
                .andExpect(jsonPath("$.data.items[0].pushes[0].effect.recovered").value(1));
    }

    @Test
    void voidAnalysis_remindMissing_extendDeadline() throws Exception {
        jdbc.update("DELETE FROM answer_record WHERE item_id = 2 AND student_id IN (8, 9, 10, 11)");
        deadlinePassed();
        board().andExpect(jsonPath("$.data.items[0].status").value("void"));
        mockMvc.perform(get("/api/class-analysis/open-count")).andExpect(jsonPath("$.data").value(1));
        mockMvc.perform(post("/api/class-analysis/items/2/remind").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(jsonPath("$.data").value(7));
        mockMvc.perform(as(12, get("/api/assistant/notices")))
                .andExpect(jsonPath("$.data[0]", Matchers.containsString("还没交")));
        mockMvc.perform(as(5, get("/api/assistant/notices"))).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(post("/api/class-analysis/items/2/extend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueAt\": \"2000-01-01 00:00:00\"}")).andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/class-analysis/items/2/extend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueAt\": \"" + java.time.LocalDateTime.now().plusDays(3)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\"}"))
                .andExpect(jsonPath("$.code").value(200));
        board().andExpect(jsonPath("$.data.items[0].status").value("waiting"));
    }
}
