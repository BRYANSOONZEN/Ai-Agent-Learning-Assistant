package com.se.platform.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.se.platform.ai.client.AiClient;
import com.se.platform.ai.client.FakeAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 老师话题接口的集成测试，用 FakeAiClient，不调用真实 API。
 *
 * <p>{@code @TestConfiguration} + {@code @Primary}：测试时多注册一个 AiClient（FakeAiClient），
 * 并让它优先，于是 TopicDraftService 拿到的是假的，真实的 OpenAiCompatibleClient 不会被用到。
 *
 * <p>{@code @DirtiesContext}：每个测试结束后重建 Spring 环境（数据库也重新初始化），测试之间互不影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TeacherTopicControllerTest {

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
    private AiClient aiClient;

    private FakeAiClient fake;

    @BeforeEach
    void setUp() {
        fake = (FakeAiClient) aiClient;
    }

    private static final String DRAFT_BODY = """
            {"question": "每单 1 到 99 件，测下限选哪几个数量？",
             "answer": "取 0、1、2。0 越过边界应拒绝，1 在边界上，2 在内侧。",
             "sourceRef": "课件第 13 页",
             "sourceText": "在边界上、边界外侧、边界内侧各取值"}
            """;

    @Test
    void draft_returnsParsedKeyPoints() throws Exception {
        fake.reply("""
                {"title": "下边界取值", "key_points": ["越过边界的 0", "边界上的 1", "内侧的 2"],
                 "simple_question": "最少能买几件？", "hints": ["提示一", "提示二"]}
                """);

        mockMvc.perform(post("/api/teacher/topics/draft").contentType(MediaType.APPLICATION_JSON).content(DRAFT_BODY))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("下边界取值"))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(3)))
                .andExpect(jsonPath("$.data.hints[1]").value("提示二"));

        // 提示词里要带上老师的参考答案和出处
        String prompt = fake.requests().get(0).messages().get(0).content();
        assertThat(prompt).contains("取 0、1、2").contains("课件第 13 页");

        // 调用日志：记下任务、token、通过检查
        mockMvc.perform(get("/api/ai/logs"))
                .andExpect(jsonPath("$.data.totals.calls").value(1))
                .andExpect(jsonPath("$.data.totals.inputTokens").value(100))
                .andExpect(jsonPath("$.data.records[0].task").value("TOPIC_DRAFT"))
                .andExpect(jsonPath("$.data.records[0].promptVersion").value("topic_draft_v2"))
                .andExpect(jsonPath("$.data.records[0].outputTokens").value(50))
                .andExpect(jsonPath("$.data.records[0].success").value(true))
                .andExpect(jsonPath("$.data.records[0].passedCheck").value(true));
    }

    @Test
    void draft_acceptsJsonWrappedInCodeFence() throws Exception {
        fake.reply("```json\n{\"title\": \"t\", \"key_points\": [\"a\"], \"hints\": []}\n```");

        mockMvc.perform(post("/api/teacher/topics/draft").contentType(MediaType.APPLICATION_JSON).content(DRAFT_BODY))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keyPoints[0]").value("a"));
    }

    @Test
    void draft_badJson_returns503() throws Exception {
        fake.reply("我不知道");

        mockMvc.perform(post("/api/teacher/topics/draft").contentType(MediaType.APPLICATION_JSON).content(DRAFT_BODY))
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void draft_aiFails_returns503WithReason() throws Exception {
        fake.fail("连接模型超时或网络不通");

        mockMvc.perform(post("/api/teacher/topics/draft").contentType(MediaType.APPLICATION_JSON).content(DRAFT_BODY))
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("AI 暂时不可用：连接模型超时或网络不通"));

        // 失败也要记日志
        mockMvc.perform(get("/api/ai/logs"))
                .andExpect(jsonPath("$.data.totals.failures").value(1))
                .andExpect(jsonPath("$.data.records[0].success").value(false))
                .andExpect(jsonPath("$.data.records[0].error").value("连接模型超时或网络不通"));
    }

    @Test
    void draft_missingAnswer_returns400() throws Exception {
        mockMvc.perform(post("/api/teacher/topics/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"q\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参考答案不能为空"));
    }

    @Test
    void seededTopics_areListedInOrder() throws Exception {
        mockMvc.perform(get("/api/teacher/topics"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].title").value("下边界取哪些值"))
                .andExpect(jsonPath("$.data[0].keyPoints", hasSize(3)));
    }

    @Test
    void saveThenDelete() throws Exception {
        mockMvc.perform(post("/api/teacher/topics").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "新话题", "question": "问题", "keyPoints": ["要点一", " ", "要点二"],
                         "hints": ["提示"], "revealText": "讲解", "createdByAi": true}
                        """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.sortOrder").value(2))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(2)));   // 空白要点被去掉

        mockMvc.perform(delete("/api/teacher/topics/3")).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/teacher/topics")).andExpect(jsonPath("$.data", hasSize(2)));
    }
}
