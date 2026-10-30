package com.se.platform.dialogue;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 用 FakeAiClient 走完一整段学生对话（data.sql 里的 2 个话题），不调用真实 API。
 * 话题 1：部分对 → 提示 → 补全 → 下一个话题；话题 2：卡住 → 答错 → 答错 → 讲解 → 结束 → 总结。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DialogueFlowTest {

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

    private ResultActions say(String text) throws Exception {
        return mockMvc.perform(post("/api/dialogue/sessions/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"" + text + "\"}"));
    }

    @Test
    void wholeDialogue_partialHintCorrect_thenStuckWrongReveal_thenSummary() throws Exception {
        mockMvc.perform(get("/api/dialogue/chapters/1"))
                .andExpect(jsonPath("$.data.topicTitles", hasSize(2)));

        mockMvc.perform(post("/api/dialogue/sessions"))
                .andExpect(jsonPath("$.data.sessionId").value(1))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.messages[0].content", containsString("每单购买数量为 1 到 99 件")));

        // 话题 1：模型说 partial，只答到第 2 条 → 显示模型的提示
        fake.reply("{\"type\":\"partial\",\"hit_points\":[2],\"reply\":\"1 选得好，那边界外面的值呢？\"}");
        say("1 和 50").andExpect(jsonPath("$.data.messages[-1].content").value("1 选得好，那边界外面的值呢？"))
                .andExpect(jsonPath("$.data.messages[-1].source").value("MODEL"))
                .andExpect(jsonPath("$.data.currentIdx").value(0));

        // 补上 1、3 → 程序数出三条全了 → 下一个话题，结果是"提示后答对"
        fake.reply("{\"type\":\"partial\",\"hit_points\":[1,3],\"reply\":\"对，三个值齐了。\"}");
        say("0 和 2").andExpect(jsonPath("$.data.currentIdx").value(1))
                .andExpect(jsonPath("$.data.topics[0].result").value("HINT_CORRECT"))
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("为什么要测边界附近")));

        // 话题 2：我卡住了 → 预写提示 L0，不调模型
        int callsBefore = fake.requests().size();
        mockMvc.perform(post("/api/dialogue/sessions/1/stuck"))
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("想想程序员写判断条件时")))
                .andExpect(jsonPath("$.data.messages[-1].source").value("TEMPLATE"));
        org.assertj.core.api.Assertions.assertThat(fake.requests()).hasSize(callsBefore);

        // 答错两次 → 显示老师的讲解原文 → 最后一个话题 → 结束
        fake.reply("{\"type\":\"wrong\",\"hit_points\":[],\"reply\":\"再想想 >= 写成 > 会怎样？\"}");
        say("因为 50 太普通").andExpect(jsonPath("$.data.status").value("OPEN"));
        say("边界会被检查两次")
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.topics[1].result").value("REVEALED"))
                .andExpect(jsonPath("$.data.summary.completed").value(2))
                .andExpect(jsonPath("$.data.summary.good[0]").value("下边界取哪些值（提示后答对）"))
                .andExpect(jsonPath("$.data.summary.improve[0]", containsString("为什么要测边界附近")));

        // 结束后不能再发
        say("还在吗").andExpect(jsonPath("$.code").value(400));

        // 4 次发言调了 4 次模型，"我卡住了"不调模型，所以日志正好 4 条
        mockMvc.perform(get("/api/ai/logs"))
                .andExpect(jsonPath("$.data.totals.calls").value(4))
                .andExpect(jsonPath("$.data.totals.inputTokens").value(400))
                .andExpect(jsonPath("$.data.totals.outputTokens").value(200))
                .andExpect(jsonPath("$.data.records[0].task").value("DIALOGUE_TURN"))
                .andExpect(jsonPath("$.data.records[0].promptVersion").value("dialogue_turn_v1"));
    }

    @Test
    void modelFails_fallsBackToPrewrittenHint() throws Exception {
        mockMvc.perform(post("/api/dialogue/sessions"));
        fake.fail("等待模型回复超时");
        say("17")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.messages[-1].source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("想想边界本身")));
    }

    @Test
    void irrelevantTwice_skipsTopic_andModelCannotForceCorrect() throws Exception {
        mockMvc.perform(post("/api/dialogue/sessions"));
        fake.reply("{\"type\":\"irrelevant\",\"hit_points\":[],\"reply\":\"\"}");
        say("哈哈").andExpect(jsonPath("$.data.messages[-1].content", containsString("我们先回到这个问题")));
        say("嘿嘿").andExpect(jsonPath("$.data.topics[0].result").value("SKIPPED_IRRELEVANT"))
                .andExpect(jsonPath("$.data.currentIdx").value(1));

        // 模型说 correct，但只给了 1 条要点 → 程序改判为 partial，不换话题
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1],\"reply\":\"很好！\"}");
        say("程序员容易写错").andExpect(jsonPath("$.data.currentIdx").value(1))
                .andExpect(jsonPath("$.data.topics[1].result").doesNotExist());
    }
}
