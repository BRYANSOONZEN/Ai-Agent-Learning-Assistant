package com.se.platform.feynman;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.AiClient;
import com.se.platform.ai.client.FakeAiClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
 * 费曼式对话。第 1 章的 AI 对话项（内容项 3），学生 5 号已经学完前两项（对话已解锁）。
 * 知识点用测试里插入的一个（2 条要点）；AI 用假的，按顺序返回预设的回复。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FeynmanTest {

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        FakeAiClient fakeAiClient() {
            return new FakeAiClient();
        }
    }

    static final String DRAFT = """
            {"scene": {"type": "同学请教", "name": "阿哲", "setting": "阿哲要测网店的购买数量", "persona": "实习生", "opening": "每单买 1～99 件，我该测哪些数？"},
             "misconception_probe": {"misconception": "测中间值最有代表性", "line": "那我只测 50 不就行了？"},
             "key_points": [
               {"no": 1, "understood_if": "说出要测边界外的 0", "partial_if": "只说测边界", "L1": "还有吗？", "L2": "有人下单 0 件呢？", "L3": "比 1 小的数要不要测？", "pointer": "课件第 4 页"},
               {"no": 2, "understood_if": "说出错误常在边界附近", "partial_if": "只说边界重要", "L1": "为什么挑这些？", "L2": "程序员把 ≥1 写成 >1，测 50 能发现吗？", "L3": "错误一般出在哪里？", "pointer": "课件第 5 页"}],
             "recap": "复述"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiClient aiClient;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private FakeAiClient fake;
    private long kpId;

    @BeforeEach
    void setUp() {
        fake = (FakeAiClient) aiClient;
        jdbc.update("""
                INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, misconceptions,
                  sources, edited, created_at, updated_at)
                VALUES (1, 0, '边界测试值的选取', '边界上、外、内各取值', '重点',
                  '[{"text":"边界外的值也要测，比如 0","sources":[]},{"text":"错误常发生在边界附近","sources":[]}]',
                  '["测中间值最有代表性"]', '[]', 0, NOW(), NOW())""");
        kpId = jdbc.queryForObject("SELECT MAX(id) FROM chapter_kp", Long.class);
        confirmKps();
    }

    /** 第 1 章的知识点标成老师已确认（生成对话草稿的前提） */
    private void confirmKps() {
        jdbc.update("DELETE FROM chapter_kp_state WHERE chapter_id = 1");
        jdbc.update("""
                INSERT INTO chapter_kp_state (chapter_id, upload_done, status, message, created_at, updated_at)
                VALUES (1, 1, 'confirmed', '已确认', NOW(), NOW())""");
    }

    private String turn(String kind, boolean attempt, String points, boolean fixed, String ack) {
        return turn(kind, attempt, points, fixed, ack, "");
    }

    private String turn(String kind, boolean attempt, String points, boolean fixed, String ack, String followup) {
        return "{\"kind\":\"" + kind + "\",\"attempt\":" + attempt + ",\"points\":" + points
                + ",\"misconception_fixed\":" + fixed + ",\"ack\":\"" + ack + "\",\"followup\":\"" + followup + "\"}";
    }

    private ResultActions say(long session, String text) throws Exception {
        return mockMvc.perform(post("/api/learn/feynman/" + session + "/reply").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("text", text))));
    }

    private String lastAi(ResultActions r) throws Exception {
        JsonNode msgs = json.readTree(r.andReturn().getResponse().getContentAsString()).path("data").path("messages");
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if ("ai".equals(msgs.get(i).path("role").asText())) {
                return msgs.get(i).path("content").asText();
            }
        }
        return "";
    }

    @Test
    void teacherDraftsAtMostTwo_thenConfirms() throws Exception {
        fake.reply(DRAFT);
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpIds\": [" + kpId + ", " + kpId + ", " + kpId + "]}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("最多 2 个")));

        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpIds\": [" + kpId + "]}"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.drafts[0].draft.scene.opening").value("每单买 1～99 件，我该测哪些数？"))
                // 程序检查：草稿齐全；L3“比 1 小的数要不要测？”没有照抄要点原文
                .andExpect(jsonPath("$.data.drafts[0].checks[0].ok").value(true))
                .andExpect(jsonPath("$.data.drafts[0].checks[3].ok").value(true));

        // 没确认之前学生用不了
        mockMvc.perform(get("/api/learn/items/3/feynman")).andExpect(jsonPath("$.data.enabled").value(false));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm")).andExpect(jsonPath("$.data.status").value("confirmed"));
        mockMvc.perform(get("/api/learn/items/3/feynman")).andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.names[0]").value("边界测试值的选取"));
    }

    @Test
    void wholeConversation_hintsByLevel_followupAndSupplement() throws Exception {
        fake.reply(DRAFT);
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kpIds\": [" + kpId + "]}"));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm"));

        JsonNode s = json.readTree(mockMvc.perform(post("/api/learn/items/3/feynman/start"))
                .andReturn().getResponse().getContentAsString()).path("data");
        long sid = s.path("id").asLong();
        Assertions.assertEquals("阿哲", s.path("kps").get(0).path("peer").asText());   // 名字来自草稿
        Assertions.assertEquals("每单买 1～99 件，我该测哪些数？", s.path("messages").get(2).path("content").asText());

        // 第 1 句：讲清了要点 1（原话核对得上）→ 自己讲清；短反应里的问句和“对，”被去掉；接着问要点 2 的 L1
        fake.reply(turn("explain", true, "[{\"no\":1,\"status\":\"clear\",\"quote\":\"要测0，因为0应该被拒绝\"}]", false,
                "对，原来如此。那 1 呢？"));
        ResultActions r = say(sid, "要测0，因为0应该被拒绝");
        Assertions.assertEquals("原来如此。 为什么挑这些？", lastAi(r));

        // 第 2 句：不知道 → 讲了两句以后，带着常见误解问一次
        fake.reply(turn("stuck", false, "[]", false, "没关系"));
        Assertions.assertTrue(lastAi(say(sid, "不知道")).endsWith("那我只测 50 不就行了？"));

        // 第 3 句：指出了误解；要点 2 讲到一部分（有进展、没讲清）→ 用模型的针对性追问，不原样重复 L1
        fake.reply(turn("explain", true, "[{\"no\":2,\"status\":\"partial\",\"quote\":\"边界比较重要\"}]", true, "哦",
                "为什么边界比中间更重要？"));
        Assertions.assertTrue(lastAi(say(sid, "测50没用，边界比较重要")).endsWith("为什么边界比中间更重要？"));

        // 第 4 句：模型说讲清了要点 2，但摘的不是学生原话 → 不算；同一级没进展第 1 次 → 还在这一级，换个说法再问（不升级）
        fake.reply(turn("explain", true, "[{\"no\":2,\"status\":\"clear\",\"quote\":\"错误常发生在边界附近\"}]", false, "嗯"));
        Assertions.assertTrue(lastAi(say(sid, "可能发现不了吧")).endsWith("你再想想：为什么挑这些？"));

        // 第 5 句：同一级连续 2 次没进展 → 升到 L2
        fake.reply(turn("explain", true, "[]", false, "好"));
        Assertions.assertTrue(lastAi(say(sid, "出在代码里？")).endsWith("程序员把 ≥1 写成 >1，测 50 能发现吗？"));
        // 第 6 句：L2 第 1 次没进展 → 用模型的针对性追问换个角度
        fake.reply(turn("explain", true, "[]", false, "好", "如果只测 50，>1 写错了会暴露吗？"));
        Assertions.assertTrue(lastAi(say(sid, "不太确定")).endsWith("如果只测 50，>1 写错了会暴露吗？"));
        // 第 7 句：L3；第 8 句：L3 再问一次；第 9 句：不给答案，指出资料
        fake.reply(turn("explain", true, "[]", false, "好"));
        Assertions.assertTrue(lastAi(say(sid, "应该不会")).endsWith("错误一般出在哪里？"));
        Assertions.assertTrue(lastAi(say(sid, "代码里吧")).endsWith("你再想想：错误一般出在哪里？"));
        Assertions.assertTrue(lastAi(say(sid, "不知道")).contains("课件第 5 页"));

        // 第 10 句：满 10 句自动收尾：总评、复述；学生只看到对话总结（知识点和全部要点），学习情况记在后台
        fake.reply(turn("explain", true, "[]", false, "明白"))
                .then("{\"points\":[{\"no\":1,\"status\":\"clear\",\"quote\":\"要测0\"},{\"no\":2,\"status\":\"partial\",\"quote\":\"边界比较重要\"}],"
                        + "\"attempted\":true,\"misconception_fixed\":true,\"recap\":\"我记住啦：测下限要测 0\"}");
        r = say(sid, "我再想想")
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.kps[0].points[0].how").value("self"))
                .andExpect(jsonPath("$.data.kps[0].points[1].how").value("supplemented"))
                .andExpect(jsonPath("$.data.kps[0].misconceptionFixed").value(true));
        String all = r.andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(all.contains("我理解一下：测下限要测 0"));        // 去掉了重复的“我记住啦”
        Assertions.assertTrue(all.contains("对话总结：这次讲了 1 个知识点"));
        Assertions.assertTrue(all.contains("1. 边界外的值也要测，比如 0"));     // 要点原文（老师确认的）
        Assertions.assertTrue(all.contains("2. 错误常发生在边界附近"));
        // 没讲清的要点提醒去哪里复习；讲清的不提醒
        Assertions.assertTrue(all.contains("这一条这次还没讲清，可以复习：课件第 5 页"));
        Assertions.assertFalse(all.contains("可以复习：课件第 4 页"));
        Assertions.assertFalse(all.contains("小结："));                         // 学习情况不给学生看
        Assertions.assertFalse(all.contains("我刚去翻了资料"));
        Assertions.assertFalse(all.contains("建议接下来先看"));
        String state = jdbc.queryForObject("SELECT state FROM feynman_session WHERE id = ?", String.class, sid);
        Assertions.assertTrue(state.contains("要点 2：讲了一部分，阿哲补充了"));   // 记在后台
        Assertions.assertTrue(state.contains("课件第 5 页"));
        say(sid, "还能说吗").andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/learn/courses/1")).andExpect(jsonPath("$.data.doneItemIds", Matchers.hasItem(3)));

        // 老师重新生成草稿：没聊完的那一次作废，学生再打开从新草稿开始（已完成的不受影响）
        long open = json.readTree(mockMvc.perform(post("/api/learn/items/3/feynman/start").param("again", "true"))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        fake.reply(DRAFT);
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kpIds\": [" + kpId + "]}"));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm"));
        Assertions.assertEquals("expired", jdbc.queryForObject("SELECT status FROM feynman_session WHERE id = ?", String.class, open));
        Assertions.assertEquals("done", jdbc.queryForObject("SELECT status FROM feynman_session WHERE id = ?", String.class, sid));

        // 再打开：显示上一次的对话和总结；点“再讲一次”才开始新的，上一次保留
        mockMvc.perform(post("/api/learn/items/3/feynman/start"))
                .andExpect(jsonPath("$.data.id").value(sid))
                .andExpect(jsonPath("$.data.status").value("done"));
        long again = json.readTree(mockMvc.perform(post("/api/learn/items/3/feynman/start").param("again", "true"))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        Assertions.assertNotEquals(sid, again);
        mockMvc.perform(get("/api/learn/feynman/" + sid)).andExpect(jsonPath("$.data.status").value("done"));
        mockMvc.perform(post("/api/learn/items/3/feynman/start"))
                .andExpect(jsonPath("$.data.id").value(again))       // 没讲完的新一次优先
                .andExpect(jsonPath("$.data.status").value("open"));
    }

    @Test
    void tenTurnsEndsAutomatically_noAttemptNoSupplement() throws Exception {
        fake.reply(DRAFT);
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kpIds\": [" + kpId + "]}"));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm"));
        long sid = json.readTree(mockMvc.perform(post("/api/learn/items/3/feynman/start"))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();

        // 一直闲聊：每一级问 2 次才升级，10 句用完自动收尾；学生看到的总结一样是知识点和全部要点
        fake.reply(turn("chat", false, "[]", false, "哈哈"));
        String last = "";
        for (int i = 0; i < 10; i++) {
            String body = say(sid, "哈哈哈").andReturn().getResponse().getContentAsString();
            if (body.contains("\"status\":\"done\"")) {
                last = body;
                break;
            }
        }
        Assertions.assertFalse(last.isEmpty(), "10 句以内应该自动收尾");
        Assertions.assertFalse(last.contains("我刚去翻了资料"));
        Assertions.assertTrue(last.contains("对话总结：这次讲了 1 个知识点"));
        Assertions.assertTrue(last.contains("2. 错误常发生在边界附近"));
        String state = jdbc.queryForObject("SELECT state FROM feynman_session WHERE id = ?", String.class, sid);
        Assertions.assertTrue(state.contains("\"attempted\":false"));
    }

    @Test
    void draftNeedsConfirmedKps_andStaleDraftCannotBeConfirmed() throws Exception {
        // 知识点还没确认：不能生成
        jdbc.update("UPDATE chapter_kp_state SET status = 'draft' WHERE chapter_id = 1");
        fake.reply(DRAFT);
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpIds\": [" + kpId + "]}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("知识点还没确认")));
        mockMvc.perform(get("/api/chapter-items/3/feynman")).andExpect(jsonPath("$.data.kpStatus").value("draft"));

        // 确认后可以生成，草稿和知识点一致
        confirmKps();
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpIds\": [" + kpId + "]}"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.stale.length()").value(0));

        // 老师之后改了要点：提示草稿过时，不能确认
        jdbc.update("UPDATE chapter_kp SET key_points = ? WHERE id = ?",
                "[{\"text\":\"边界外的值也要测，比如 0 和 100\",\"sources\":[]},{\"text\":\"错误常发生在边界附近\",\"sources\":[]}]", kpId);
        mockMvc.perform(get("/api/chapter-items/3/feynman"))
                .andExpect(jsonPath("$.data.stale[0]").value("边界测试值的选取：要点改过"));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("请重新生成草稿")));

        // 重新生成后恢复正常
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpIds\": [" + kpId + "]}"))
                .andExpect(jsonPath("$.data.stale.length()").value(0));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm")).andExpect(jsonPath("$.data.status").value("confirmed"));
    }

    @Test
    void judgeFailure_notCountedAsAttemptOrTurn() throws Exception {
        fake.reply(DRAFT);
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kpIds\": [" + kpId + "]}"));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm"));
        long sid = json.readTree(mockMvc.perform(post("/api/learn/items/3/feynman/start"))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();

        fake.fail("timeout");
        ResultActions r = say(sid, "要测0吧")
                .andExpect(jsonPath("$.data.kps[0].turns").value(0));
        Assertions.assertEquals("抱歉，我刚才没听清，你能再说一遍吗？", lastAi(r));
        Long attempts = jdbc.queryForObject("SELECT COUNT(*) FROM feynman_session WHERE id = ? AND state LIKE '%\"attempts\":0%'",
                Long.class, sid);
        Assertions.assertEquals(1L, attempts);

        // 下一句正常判断：从 L1 开始，没有因为失败的那句升级
        fake.reply(turn("explain", true, "[]", false, "嗯"));
        Assertions.assertTrue(lastAi(say(sid, "要测0吧")).endsWith("还有吗？"));
    }

    @Test
    void teacherEditsDraft_checksRerun_needsReconfirm() throws Exception {
        fake.reply(DRAFT);
        mockMvc.perform(post("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kpIds\": [" + kpId + "]}"));
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm")).andExpect(jsonPath("$.data.status").value("confirmed"));

        // 改开场白和要点 1 的 L3；L3 照抄了要点原文“边界外的值也要测” → 程序检查标红；改了之后变回待确认
        String body = "{\"kpId\": " + kpId + ", \"draft\": {\"scene\": {\"opening\": \"我在测网店下单数量，1～99 件，该怎么挑测试值？\"},"
                + "\"key_points\": [{\"no\": 1, \"L3\": \"边界外的值也要测吗？\"}], \"recap\": \"不能改这个\"}}";
        mockMvc.perform(put("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.drafts[0].draft.scene.opening").value("我在测网店下单数量，1～99 件，该怎么挑测试值？"))
                .andExpect(jsonPath("$.data.drafts[0].draft.key_points[0].L3").value("边界外的值也要测吗？"))
                .andExpect(jsonPath("$.data.drafts[0].draft.key_points[0].L1").value("还有吗？"))
                .andExpect(jsonPath("$.data.drafts[0].draft.recap").value("复述"))
                .andExpect(jsonPath("$.data.drafts[0].edited").value(true))
                .andExpect(jsonPath("$.data.drafts[0].checks[3].ok").value(false));
        // 待确认时学生用不了
        mockMvc.perform(get("/api/learn/items/3/feynman")).andExpect(jsonPath("$.data.enabled").value(false));

        // 空的、太长的不收
        mockMvc.perform(put("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpId\": " + kpId + ", \"draft\": {\"key_points\": [{\"no\": 2, \"L1\": \"  \"}]}}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("不能为空")));
        mockMvc.perform(put("/api/chapter-items/3/feynman/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kpId\": " + kpId + ", \"draft\": {\"scene\": {\"opening\": \"" + "很".repeat(81) + "\"}}}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("不能超过 80 字")));

        // 再确认后学生用的是改过的开场白
        mockMvc.perform(post("/api/chapter-items/3/feynman/confirm"));
        String start = mockMvc.perform(post("/api/learn/items/3/feynman/start")).andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(start.contains("我在测网店下单数量，1～99 件，该怎么挑测试值？"));
    }

    @Test
    void speakerPrefixStripped() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode d = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(
                "{\"scene\":{\"opening\":\"小柯说：你好\"},\"misconception_probe\":{\"line\":\"小柯：只测 50？\"},"
                + "\"key_points\":[{\"no\":1,\"L1\":\"还有吗？\",\"L2\":\"小柯说：我给了AI一句话\",\"L3\":\"他问：错误在哪？\"}]}");
        com.se.platform.feynman.service.FeynmanDraftService.stripSpeaker(d, "小柯");
        Assertions.assertEquals("你好", d.path("scene").path("opening").asText());
        Assertions.assertEquals("只测 50？", d.path("misconception_probe").path("line").asText());
        Assertions.assertEquals("还有吗？", d.path("key_points").get(0).path("L1").asText());
        Assertions.assertEquals("我给了AI一句话", d.path("key_points").get(0).path("L2").asText());
        Assertions.assertEquals("错误在哪？", d.path("key_points").get(0).path("L3").asText());
    }

    @Test
    void ackQuestionsAndLeaksRemoved() throws Exception {
        // 实测出现过的：不带问号的问句也要去掉
        Assertions.assertEquals("AI自动写用例", com.se.platform.feynman.service.FeynmanEngine.cleanAck("AI自动写用例，那它写之前会看些什么呢"));
        Assertions.assertEquals("哦，自愈这个我懂了。", com.se.platform.feynman.service.FeynmanEngine.cleanAck("哦，自愈这个我懂了。那还有别的环节吗？"));

        // 泄露：追问把还没讲清的要点 2 的答案（错误常发生在边界附近）说出来了
        JsonNode d = json.readTree("{\"keyPoints\":[\"边界外的值也要测，比如 0\",\"错误常发生在边界附近\"],"
                + "\"draft\":" + DRAFT + "}");
        com.se.platform.feynman.service.FeynmanEngine.Kp k = new com.se.platform.feynman.service.FeynmanEngine.Kp();
        k.name = "边界测试值的选取";
        k.points.add(new com.se.platform.feynman.service.FeynmanEngine.Point());
        k.points.add(new com.se.platform.feynman.service.FeynmanEngine.Point());
        Assertions.assertTrue(com.se.platform.feynman.service.FeynmanEngine.leaks("是不是因为错误常发生在边界附近？", k, d, "要测 0"));
        Assertions.assertFalse(com.se.platform.feynman.service.FeynmanEngine.leaks("为什么边界比中间更重要？", k, d, "要测 0"));
        // 学生自己说过的不算泄露
        Assertions.assertFalse(com.se.platform.feynman.service.FeynmanEngine.leaks("你说错误常发生在边界附近，为什么？", k, d,
                "我觉得错误常发生在边界附近"));
    }
}
