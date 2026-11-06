package com.se.platform.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 用 FakeAiClient 走学生对话（data.sql 里的 2 个话题），不调用真实 API。
 * 重点确认：不作答拿不到答案；完整讲解只在变式作答之后出现；模型的回复读得到对话、失败时不算学生的错。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DialogueFlowTest {

    /** 话题 1 讲解原文的开头：出现就说明给了完整答案 */
    private static final String REVEAL_1 = "取 0、1、2。";
    private static final String WRONG = "{\"type\":\"wrong\",\"hit_points\":[],\"misconception\":\"\",\"reply\":\"再想想。\"}";
    private static final String WANT = "{\"type\":\"want_answer\",\"hit_points\":[],\"reply\":\"先说说你的想法。\"}";

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

    @Autowired
    private JdbcTemplate jdbc;

    private FakeAiClient fake;

    /** 本次测试新开的对话（种子数据里已有几段别的学生的对话，所以编号不一定是 1） */
    private long sessionId;

    @BeforeEach
    void setUp() {
        fake = (FakeAiClient) aiClient;
    }

    private ResultActions start() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/dialogue/sessions"));
        sessionId = ((Number) JsonPath.read(
                result.andReturn().getResponse().getContentAsString(), "$.data.sessionId")).longValue();
        return result;
    }

    private ResultActions say(String text) throws Exception {
        return mockMvc.perform(post("/api/dialogue/sessions/" + sessionId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"" + text + "\"}"));
    }

    private ResultActions stuck() throws Exception {
        return mockMvc.perform(post("/api/dialogue/sessions/" + sessionId + "/stuck"));
    }

    /** 学生到现在能看到的全部内容 */
    private String everything() throws Exception {
        return mockMvc.perform(get("/api/dialogue/sessions/" + sessionId)).andReturn().getResponse().getContentAsString();
    }

    private String lastPrompt() {
        return fake.requests().get(fake.requests().size() - 1).messages().get(0).content();
    }

    /** 一直答错，走到变式题：3 级提示 + 3 条要点 */
    private void wrongUntilTransfer() throws Exception {
        fake.reply(WRONG);
        for (int i = 0; i < 6; i++) {
            say("50");
        }
    }

    @Test
    void steadyProgress_neverGetsAnswer_endsAsHintCorrect() throws Exception {
        mockMvc.perform(get("/api/dialogue/chapters/1"))
                .andExpect(jsonPath("$.data.topicTitles", hasSize(2)));
        start().andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.messages[0].content", containsString("每单购买数量为 1 到 99 件")));

        fake.reply("{\"type\":\"partial\",\"hit_points\":[2],\"reply\":\"1 选得好，那边界外面呢？\"}");
        say("1").andExpect(jsonPath("$.data.messages[-1].content").value("1 选得好，那边界外面呢？"))
                .andExpect(jsonPath("$.data.messages[-1].source").value("MODEL"));
        fake.reply("{\"type\":\"partial\",\"hit_points\":[1],\"reply\":\"对，还差一个。\"}");
        say("0").andExpect(jsonPath("$.data.currentIdx").value(0));
        assertThat(everything()).doesNotContain(REVEAL_1);

        // 模型说 correct 但只有 1 条 → 程序不信；三条齐了才算
        fake.reply("{\"type\":\"partial\",\"hit_points\":[3],\"reply\":\"三个值齐了。\"}");
        say("2").andExpect(jsonPath("$.data.currentIdx").value(1))
                .andExpect(jsonPath("$.data.topics[0].result").value("HINT_CORRECT"))
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("为什么要测边界附近")));
        assertThat(everything()).doesNotContain(REVEAL_1);

        mockMvc.perform(get("/api/ai/logs"))
                .andExpect(jsonPath("$.data.records[0].promptVersion").value("dialogue_turn_v2"));
    }

    @Test
    void modelSaysCorrect_withOnePoint_doesNotFinish() throws Exception {
        start();
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1],\"reply\":\"很好！\"}");
        say("0").andExpect(jsonPath("$.data.currentIdx").value(0))
                .andExpect(jsonPath("$.data.topics[0].result").doesNotExist());
    }

    @Test
    void noProgress_hintsThenPointsThenTransfer_answerOnlyAfterTransfer() throws Exception {
        start();
        fake.reply(WRONG);
        for (int i = 0; i < 3; i++) {
            say("50").andExpect(jsonPath("$.data.messages[-1].source").value("MODEL"));
        }
        // L3 之后一次只给一条要点
        say("50").andExpect(jsonPath("$.data.messages[-1].content", containsString("这里告诉你一条：越过下边界的 0")))
                .andExpect(jsonPath("$.data.topics[0].transfer").value(false));
        assertThat(everything()).doesNotContain("正好在边界上的 1");

        // 照抄刚给的要点不算进展，接着给下一条
        fake.reply("{\"type\":\"partial\",\"hit_points\":[1],\"reply\":\"对。\"}");
        say("越过下边界的 0").andExpect(jsonPath("$.data.messages[-1].content", containsString("这里告诉你一条：正好在边界上的 1")));

        fake.reply(WRONG);
        say("50").andExpect(jsonPath("$.data.messages[-1].content", containsString("某图书馆")))
                .andExpect(jsonPath("$.data.topics[0].transfer").value(true));
        assertThat(everything()).doesNotContain(REVEAL_1);

        // 变式答全 → 讲解后会用，这时才显示完整讲解
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1,2,3],\"reply\":\"对。\"}");
        say("2、3、4").andExpect(jsonPath("$.data.topics[0].result").value("REVEALED_APPLIED"));
        assertThat(everything()).contains(REVEAL_1);
        assertThat(lastPrompt()).contains("某图书馆").contains("越过下边界的 2");
    }

    @Test
    void transferNotCompleteTwice_isRevealed_showsReferenceAndSummary() throws Exception {
        start();
        wrongUntilTransfer();
        say("不知道怎么选").andExpect(jsonPath("$.data.topics[0].result").doesNotExist());
        assertThat(everything()).doesNotContain(REVEAL_1);
        say("选 5").andExpect(jsonPath("$.data.topics[0].result").value("REVEALED"));
        assertThat(everything()).contains(REVEAL_1).contains("变式题的参考答案");
    }

    @Test
    void transferRefused_threeTimes_endsWithoutAnswer() throws Exception {
        start();
        wrongUntilTransfer();
        fake.reply(WANT);
        say("告诉我答案");
        say("告诉我答案");
        say("告诉我答案").andExpect(jsonPath("$.data.topics[0].result").value("REVEALED"))
                .andExpect(jsonPath("$.data.currentIdx").value(1));
        assertThat(everything()).doesNotContain(REVEAL_1).doesNotContain("变式题的参考答案");
    }

    @Test
    void neverAttempted_askingThreeTimes_skipsWithoutAnswer() throws Exception {
        start();
        fake.reply(WANT);
        say("告诉我答案").andExpect(jsonPath("$.data.messages[-1].content").value("先说说你的想法。"))
                .andExpect(jsonPath("$.data.messages[-1].source").value("MODEL"));
        stuck();
        say("直接说吧").andExpect(jsonPath("$.data.topics[0].result").value("SKIPPED_IRRELEVANT"));
        assertThat(everything()).doesNotContain(REVEAL_1);
    }

    @Test
    void irrelevant_usesModelReply_twiceSkipsWithoutAnswer() throws Exception {
        start();
        fake.reply("{\"type\":\"irrelevant\",\"hit_points\":[],\"reply\":\"心情不错嘛，回到题目吧。\"}");
        say("哈哈").andExpect(jsonPath("$.data.messages[-1].content").value("心情不错嘛，回到题目吧。"))
                .andExpect(jsonPath("$.data.messages[-1].source").value("MODEL"));
        say("嘿嘿").andExpect(jsonPath("$.data.topics[0].result").value("SKIPPED_IRRELEVANT"))
                .andExpect(jsonPath("$.data.currentIdx").value(1));
        assertThat(everything()).doesNotContain(REVEAL_1);
    }

    @Test
    void answerInsideChat_countsAsAnswer_notIrrelevant() throws Exception {
        start();
        fake.reply("{\"type\":\"irrelevant\",\"hit_points\":[1],\"reply\":\"0 是对的。\"}").then(
                "{\"type\":\"irrelevant\",\"hit_points\":[2],\"reply\":\"1 也对。\"}");
        say("哈哈应该测 0 吧");
        say("嘿嘿还有 1").andExpect(jsonPath("$.data.topics[0].result").doesNotExist())
                .andExpect(jsonPath("$.data.messages[-1].content").value("1 也对。"));
    }

    @Test
    void clarifyAndOffTopic_useModelReply_doNotRaiseLevel() throws Exception {
        start();
        fake.reply("{\"type\":\"clarify\",\"hit_points\":[],\"reply\":\"下限就是最少能买的数量。\"}");
        say("什么是下限").andExpect(jsonPath("$.data.messages[-1].content").value("下限就是最少能买的数量。"));
        fake.reply("{\"type\":\"off_topic\",\"hit_points\":[],\"reply\":\"资料里没有，我们回到题目。\"}");
        say("Java 怎么装").andExpect(jsonPath("$.data.messages[-1].content").value("资料里没有，我们回到题目。"));
        // 还是 L1：提示要求没有升级
        fake.reply(WRONG);
        say("50");
        assertThat(lastPrompt()).contains("只指出思考方向");
    }

    @Test
    void stuck_callsModel_andReplyContinuesDialogue() throws Exception {
        start();
        int before = fake.requests().size();
        fake.reply("{\"type\":\"wrong\",\"hit_points\":[1,2,3],\"reply\":\"没关系，先想小一点。\"}");
        stuck().andExpect(jsonPath("$.data.messages[-1].content").value("没关系，先想小一点。"))
                .andExpect(jsonPath("$.data.messages[-2].content").value("（我卡住了）"))
                // 卡住时模型给的要点不算数
                .andExpect(jsonPath("$.data.topics[0].result").doesNotExist());
        assertThat(fake.requests()).hasSize(before + 1);
    }

    @Test
    void modelFails_usesPrewrittenHint_andDoesNotCountAsNoProgress() throws Exception {
        start();
        fake.fail("等待模型回复超时");
        say("17").andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.messages[-1].source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("想想边界本身")));
        fake.reply("不是 JSON");
        say("17").andExpect(jsonPath("$.data.messages[-1].source").value("TEMPLATE"));
        // 两次失败都没升级：下一次真正答错才是 L1
        fake.reply(WRONG);
        say("50");
        assertThat(lastPrompt()).contains("只指出思考方向");
    }

    @Test
    void leak_replacedByPrewrittenHint_butAnsweredPointIsFine() throws Exception {
        start();
        // 回复含未答到的要点原文（标点、空格不同也算）→ 换成预写提示
        fake.reply("{\"type\":\"wrong\",\"hit_points\":[],\"reply\":\"答案是 正好在边界上的1。\"}");
        say("50").andExpect(jsonPath("$.data.messages[-1].source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.messages[-1].content", containsString("想想边界本身")));

        // 这一句刚答到的要点，回复里说出来不算泄露
        fake.reply("{\"type\":\"partial\",\"hit_points\":[2],\"reply\":\"对，正好在边界上的 1 要测。\"}");
        say("测 1").andExpect(jsonPath("$.data.messages[-1].source").value("MODEL"));
    }

    @Test
    void promptOnlyHasCurrentTopic() throws Exception {
        start();
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1,2,3],\"reply\":\"对。\"}");
        say("第一个话题的特别回答");
        fake.reply(WRONG);
        say("第二个话题");
        assertThat(lastPrompt()).doesNotContain("第一个话题的特别回答").contains("第二个话题");
    }

    @Test
    void topicWithoutTransfer_answerShownOnlyAfterOwnWords() throws Exception {
        jdbc.update("UPDATE dialogue_topic SET transfer_question = NULL, transfer_points = NULL, transfer_reveal = NULL");
        start();
        wrongUntilTransfer();
        assertThat(everything()).contains("请先用自己的话说一遍").doesNotContain(REVEAL_1);
        say("我觉得是测边界").andExpect(jsonPath("$.data.topics[0].result").value("REVEALED"));
        assertThat(everything()).contains(REVEAL_1);
    }

    @Test
    void wholeDialogue_summaryShowsNewResults() throws Exception {
        start();
        wrongUntilTransfer();
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1,2,3],\"reply\":\"对。\"}");
        say("2、3、4");
        wrongUntilTransfer();
        say("不知道");
        say("还是不知道")
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.summary.completed").value(2))
                .andExpect(jsonPath("$.data.summary.improve[0]").value("下边界取哪些值（讲解后会用）"))
                .andExpect(jsonPath("$.data.summary.improve[1]", containsString("为什么要测边界附近（讲解后仍不会）")));

        say("还在吗").andExpect(jsonPath("$.code").value(400));
    }
}
