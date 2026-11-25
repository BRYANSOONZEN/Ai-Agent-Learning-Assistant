package com.se.platform.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import com.se.platform.ai.client.FakeAiClient;
import com.se.platform.common.DemoIdentity;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 小助手：自动干预（个人补练题）和自由聊天（docs/模块说明/小助手-自动干预与聊天.md）。
 * 演示数据：学生01（5）交过第 1 章的练习，知识点 1 第一次错（红）；学生08（12）还没交卷（阶段一）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RemedyAndChatTest {

    @TestConfiguration
    static class FakeAiConfig {
        @Bean
        @Primary
        FakeAiClient fakeAiClient() {
            return new FakeAiClient();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FakeAiClient fake;

    /** 3 道补练题（正确答案都是 A），AI 不看答案做的时候也都答 A */
    private static final String THREE = """
            {"questions": [
              {"type": "single_choice", "assess_type": "scenario", "key_point_no": 1, "stem": "停车 1～24 小时，测上限选哪组？",
               "options": ["23、24、25", "1、12、24", "24、48", "22、23、24"], "answer": "A",
               "option_notes": ["", "混在一起", "太远", "只取了边界和内侧，缺少越过边界的值"], "analysis": "上边界内外都要测"},
              {"type": "single_choice", "assess_type": "concept", "key_point_no": 1, "stem": "积分 0～9999，测下限选哪组？",
               "options": ["-1、0、1", "0、1、2", "0、5000", "-100、0"], "answer": "A",
               "option_notes": ["", "只取了边界和内侧，缺少越过边界的值", "混在一起", "太远"], "analysis": "下边界内外都要测"},
              {"type": "judge", "assess_type": "concept", "key_point_no": 1, "stem": "边界外的值也要测。",
               "options": ["正确", "错误"], "answer": "A", "option_notes": ["", "以为只测边界内"], "analysis": "要测"}]}""";
    private static final String SOLVED = "{\"answers\": [\"A\"]}";

    private static MockHttpServletRequestBuilder as(long studentId, MockHttpServletRequestBuilder req) {
        return req.header(DemoIdentity.HEADER, String.valueOf(studentId));
    }

    @BeforeEach
    void chapterKp() {
        // 第 1 章知识点 1 的识别结果（出补练题、聊天要用）
        jdbc.update("""
                INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, misconceptions,
                  sources, edited, knowledge_point_id, created_at, updated_at)
                VALUES (1, 0, '边界测试值的选取', '边界上、外、内各取值', '重点',
                  '[{"text":"边界外的值也要测，比如 0","sources":["D1-T0"]},{"text":"错误常发生在边界附近","sources":[]}]',
                  '["测中间值最有代表性"]', '[]', 0, 1, NOW(), NOW())""");
    }

    private ResultActions start(long student) throws Exception {
        return mockMvc.perform(as(student, post("/api/assistant/remedy/1/start")));
    }

    private ResultActions submit(long student, String answer) throws Exception {
        String body = start(student).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data.questions[*].id");
        StringBuilder a = new StringBuilder("{\"answers\":{");
        for (int i = 0; i < ids.size(); i++) {
            a.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append("\":\"").append(answer).append('"');
        }
        return mockMvc.perform(as(student, post("/api/assistant/remedy/1/submit"))
                .contentType(MediaType.APPLICATION_JSON).content(a.append("}}").toString()));
    }

    // ------------------------------------------------------------------ 自动干预

    @Test
    void remedyNeedsSubmittedPractice() throws Exception {
        // 阶段一（还没交卷）：不出题
        start(12).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("先完成本章的 AI 对话和练习")));
    }

    @Test
    void remedyGeneratedFromOwnMistakes_thenRecovered() throws Exception {
        fake.reply(THREE).then(SOLVED);
        int before = fake.requests().size();
        start(5).andExpect(jsonPath("$.data.round").value(1))
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist());
        // 出题提示词里写的是这个学生自己的错：第 1 题考要点 1，他选 B 对应的误解
        String prompt = fake.requests().get(before).messages().toString();
        assertThat(prompt).contains("个人补练题（第 1 轮）").contains("把上下边界和中间值混在一起");
        // 补练题只给他自己：来源 assistant，不属于任何练习，不出现在推荐里
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise WHERE source = 'assistant' AND item_id IS NULL",
                Integer.class)).isEqualTo(3);

        // 全对：补练 3 道平均 1 → 已补回来；画像从红变橙（曾经错过，要留意），不变绿
        submit(5, "A").andExpect(jsonPath("$.data.recovered").value(true))
                .andExpect(jsonPath("$.data.change.remedyStatus").value("recovered"))
                .andExpect(jsonPath("$.data.change.color").value("orange"))
                .andExpect(jsonPath("$.data.change.level").value("good"))
                .andExpect(jsonPath("$.data.results[0].analysis").isNotEmpty())
                .andExpect(jsonPath("$.data.canContinue").value(false));
        start(5).andExpect(jsonPath("$.data.questions.length()").value(0))
                .andExpect(jsonPath("$.data.text", Matchers.containsString("已经补回来了")));
        mockMvc.perform(as(5, get("/api/assistant/summary")))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].verdict").value("orange"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].text", Matchers.containsString("曾经错过")));
        mockMvc.perform(as(5, get("/api/diagnosis/me")))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].points[0].color").value("orange"))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].points[1].color").value("green"));
        // 其他学生的推荐里没有这些题
        mockMvc.perform(as(6, get("/api/recommendations")))
                .andExpect(jsonPath("$..exerciseId", Matchers.not(Matchers.hasItem(10))));
    }

    @Test
    void ambiguousRemedyQuestionDropped() throws Exception {
        // AI 不看答案做第 2 题时答了 B（和标准答案 A 不一样）→ 这道丢掉
        fake.reply(THREE).then(SOLVED).then("{\"answers\": [\"B\"]}").then(SOLVED);
        start(5).andExpect(jsonPath("$.data.questions.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exercise WHERE source = 'assistant' AND deleted = 0",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void twoRoundsWrong_escalatedToTeacher() throws Exception {
        fake.reply(THREE).then(SOLVED);
        submit(5, "B").andExpect(jsonPath("$.data.recovered").value(false))
                .andExpect(jsonPath("$.data.canContinue").value(true))
                .andExpect(jsonPath("$.data.change.remedyStatus").value("practicing"));
        fake.reply(THREE).then(SOLVED);
        int before = fake.requests().size();
        submit(5, "B").andExpect(jsonPath("$.data.escalated").value(true))
                .andExpect(jsonPath("$.data.canContinue").value(false))
                .andExpect(jsonPath("$.data.text", Matchers.containsString("告诉老师")));
        assertThat(fake.requests().get(before).messages().toString()).contains("第 2 轮");
        start(5).andExpect(jsonPath("$.data.escalated").value(true))
                .andExpect(jsonPath("$.data.questions.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remedy_round WHERE escalated = 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void summaryOffersRemedyForRedPoint() throws Exception {
        mockMvc.perform(as(5, get("/api/assistant/summary")))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].level").value("weak"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].action").value("remedy"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].plan[0].kind").value("review"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].plan[1].kind").value("remedy"));
    }

    // ------------------------------------------------------------------ 自由聊天

    private ResultActions say(long student, String text) throws Exception {
        return mockMvc.perform(as(student, post("/api/assistant/chat")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chapterId\": 1, \"message\": \"" + text + "\"}"));
    }

    @Test
    void stageOne_noQuestionsInChat() throws Exception {
        fake.reply("来考考你：\nA. 0、1、2\nB. 1、50、99\n你选哪个？");
        say(12, "边界值要怎么取？").andExpect(jsonPath("$.data.stage").value("learning"))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.reply", Matchers.containsString("先不出题")));
        String prompt = fake.requests().get(fake.requests().size() - 1).messages().get(0).content();
        assertThat(prompt).contains("学习中（本章练习还没交卷）").contains("边界外的值也要测");
        // 原来的回复留给开发看，学生看到的是固定的话
        assertThat(jdbc.queryForObject("SELECT original FROM assistant_message WHERE blocked = 'question'",
                String.class)).contains("考考你");
    }

    @Test
    void pastedQuestion_answerBlocked_conceptAllowed() throws Exception {
        // 学生贴了第 1 题的题干问答案；模型说了“选 A” → 拦下
        fake.reply("选 A，因为 17、18、19 刚好覆盖边界外、边界上和边界内。");
        say(5, "报名年龄要求为 18～60 岁。测试下边界时，下面哪组取值最合适？选哪个").andExpect(
                        jsonPath("$.data.stage").value("submitted"))
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.reply", Matchers.containsString("不能直接告诉你答案")))
                .andExpect(jsonPath("$.data.reply", Matchers.containsString("边界测试值的选取")));
        String prompt = fake.requests().get(fake.requests().size() - 1).messages().get(0).content();
        assertThat(prompt).contains("【注意】学生这一句是在问一道具体的题").contains("练习已交卷")
                .contains("还不熟");

        // 问概念：回复讲到了同一个知识点，但没有针对某道题给答案 → 不拦
        fake.reply("边界外的值也要测，因为程序常把 >= 写成 >，只有正好在边界上、刚越过边界时才暴露（课件第 1 项）。");
        say(5, "为什么边界外的值也要测？").andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.reply", Matchers.containsString("边界外的值也要测")));
        mockMvc.perform(as(5, get("/api/assistant/chat")).param("chapterId", "1"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].role").value("student"));
    }
}
