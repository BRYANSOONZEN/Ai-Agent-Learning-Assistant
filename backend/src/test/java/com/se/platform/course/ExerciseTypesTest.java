package com.se.platform.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.AiClient;
import com.se.platform.ai.client.FakeAiClient;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.service.Grader;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.diagnosis.service.MasteryService.Attempt;
import java.util.List;
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

/**
 * 练习的 6 种题型、掌握度公式（猜测校正 + 可信度权重）、简答两次判分、老师改判、AI 出题的草稿流程。
 * 演示数据：第 1 章的练习项 2（两道 4 选 1 单选，id 1、2，答案 A、B），学生 5 号。AI 用假的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExerciseTypesTest {

    @TestConfiguration
    static class Fakes {
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
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private FakeAiClient fake;

    @BeforeEach
    void setUp() {
        fake = (FakeAiClient) aiClient;
    }

    private static ExerciseView view(String type, List<String> options, String answer, List<String> reasons,
                                     String reasonAnswer, List<String> accepts) {
        return new ExerciseView(1L, 1L, 1L, type, "concept", null, "题干", options, answer, "", List.of("", "误解B", "", ""),
                reasons, reasonAnswer, List.of("", "", "理由误解C"), accepts, null, List.of(), "manual", false, null, 2,
                0, List.of());
    }

    @Test
    void objectiveTypesAndGuess() {
        List<String> four = List.of("a", "b", "c", "d");
        ExerciseView single = view(Grader.SINGLE, four, "A", List.of(), null, List.of());
        assertThat(Grader.grade(single, "a").correct()).isTrue();
        assertThat(Grader.grade(single, "B").note()).isEqualTo("误解B");
        assertThat(Grader.guess(single)).isEqualTo(0.25);

        ExerciseView two = view(Grader.TWO_TIER, four, "A", List.of("r1", "r2", "r3"), "B", List.of());
        assertThat(Grader.grade(two, "A|B").score()).isEqualTo(1);
        // 答案对、理由错：只记对或错，算错，带上错误理由的误解
        assertThat(Grader.grade(two, "A|C").score()).isEqualTo(0);
        assertThat(Grader.grade(two, "A|C").note()).isEqualTo("理由误解C");
        assertThat(Grader.guess(two)).isEqualTo(1.0 / 12);

        ExerciseView multi = view(Grader.MULTIPLE, four, "AC", List.of(), null, List.of());
        assertThat(Grader.grade(multi, "ca").correct()).isTrue();
        assertThat(Grader.grade(multi, "A").correct()).isFalse(); // 少选也算错
        assertThat(Grader.guess(multi)).isEqualTo(1.0 / 15);

        ExerciseView judge = view(Grader.JUDGE, Grader.JUDGE_OPTIONS, "B", List.of(), null, List.of());
        assertThat(Grader.grade(judge, "B").correct()).isTrue();
        assertThat(Grader.guess(judge)).isEqualTo(0.5);

        ExerciseView fill = view(Grader.FILL, List.of(), "", List.of(), null, List.of("测试执行优化", "执行优化"));
        assertThat(Grader.grade(fill, " 执行优化。").correct()).isTrue(); // 去掉空格和标点后比
        assertThat(Grader.grade(fill, "测试执行").correct()).isFalse();
        assertThat(Grader.guess(fill)).isEqualTo(0);
    }

    @Test
    void masteryFormula() {
        // 设计文档的算例：单选对、单选对、简答 1/3（两次一致，r = 0.5）→ 0.789
        Double m = MasteryService.computeAttempts(List.of(new Attempt(1, 0.25, 1), new Attempt(1, 0.25, 1),
                new Attempt(1.0 / 3, 0, 0.5)));
        assertThat(m).isEqualTo(0.789);
        // 完全靠蒙：判断题正确率 0.5 → 掌握度 0
        assertThat(MasteryService.computeAttempts(List.of(new Attempt(1, 0.5, 1), new Attempt(0, 0.5, 1),
                new Attempt(1, 0.5, 1), new Attempt(0, 0.5, 1)))).isEqualTo(0.0);
        // 判断对、判断对、单选错 → 0.32
        assertThat(MasteryService.computeAttempts(List.of(new Attempt(1, 0.5, 1), new Attempt(1, 0.5, 1),
                new Attempt(0, 0.25, 1)))).isEqualTo(0.3197);
    }

    private long addShort() throws Exception {
        String body = """
                {"chapterId": 1, "itemId": 2, "type": "short_answer", "assessType": "scenario",
                 "stem": "订单数量 1～99，你会选哪些测试值？为什么？",
                 "reference": "取 0、1、2、98、99、100，因为错误常发生在边界附近，边界外的值也要测",
                 "keyPoints": ["边界外的值也要测", "错误常发生在边界附近"], "difficulty": 2, "knowledgePointIds": [1]}""";
        JsonNode r = json.readTree(mockMvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return r.path("data").path("id").asLong();
    }

    private String submit(long shortId, String text) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("answers",
                java.util.Map.of("1", "A", "2", "B", String.valueOf(shortId), text)));
        return mockMvc.perform(post("/api/learn/items/2/submit").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void shortAnswerAgreedThenPendingThenTeacherReview() throws Exception {
        long id = addShort();
        // 两次判分一致：答到要点 1，没答到要点 2 → 0.5 分，r = 0.5
        fake.reply("""
                {"points": [{"no": 1, "hit": true, "quote": "还要测0和100"}, {"no": 2, "hit": false, "quote": ""}]}""");
        JsonNode r = json.readTree(submit(id, "我会测1和99，还要测0和100"));
        JsonNode q = r.path("data").path("results").get(2);
        assertThat(q.path("status").asText()).isEqualTo("graded");
        assertThat(q.path("score").asDouble()).isEqualTo(0.5);
        assertThat(q.path("reliability").asDouble()).isEqualTo(0.5);
        assertThat(q.path("points").get(0).path("quote").asText()).isEqualTo("还要测0和100");

        // 原话编造（学生没写过）→ 不算答到
        fake.reply("""
                {"points": [{"no": 1, "hit": true, "quote": "边界外的值也要测"}, {"no": 2, "hit": true, "quote": "我没写过这句"}]}""");
        q = json.readTree(submit(id, "我会测1和99两个数")).path("data").path("results").get(2);
        assertThat(q.path("score").asDouble()).isEqualTo(0);

        // 两次不一致 → 等老师确认，不算进掌握度
        fake.reply("""
                {"points": [{"no": 1, "hit": true, "quote": "要测0"}, {"no": 2, "hit": false, "quote": ""}]}""")
                .then("""
                {"points": [{"no": 1, "hit": false, "quote": ""}, {"no": 2, "hit": false, "quote": ""}]}""");
        r = json.readTree(submit(id, "要测0，其他随便"));
        assertThat(r.path("data").path("pending").asInt()).isEqualTo(1);
        assertThat(r.path("data").path("results").get(2).path("status").asText()).isEqualTo("pending");

        // 老师看到待确认的排在最前，改判：要点 1 答到 → 0.5 分，r = 1
        JsonNode list = json.readTree(mockMvc.perform(get("/api/exercises/short-answers").param("itemId", "2"))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.get(0).path("status").asText()).isEqualTo("pending");
        long recordId = list.get(0).path("recordId").asLong();
        mockMvc.perform(put("/api/exercises/short-answers/" + recordId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hits\": [true, false]}"))
                .andExpect(jsonPath("$.data.status").value("graded"))
                .andExpect(jsonPath("$.data.score").value(0.5))
                .andExpect(jsonPath("$.data.reliability").value(1.0))
                .andExpect(jsonPath("$.data.reviewed").value(true));

        // 太短的答案程序直接判 0，不调模型
        int before = fake.requests().size();
        q = json.readTree(submit(id, "不知道")).path("data").path("results").get(2);
        assertThat(q.path("score").asDouble()).isEqualTo(0);
        assertThat(fake.requests().size()).isEqualTo(before);
    }

    @Test
    void generatedQuestionsAreDraftsUntilConfirmed() throws Exception {
        jdbc.update("""
                INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, misconceptions,
                  sources, edited, knowledge_point_id, created_at, updated_at)
                VALUES (1, 0, '边界测试值的选取', '边界上、外、内各取值', '重点',
                  '[{"text":"边界外的值也要测，比如 0","sources":[]},{"text":"错误常发生在边界附近","sources":[]}]',
                  '["测中间值最有代表性"]', '[]', 0, 1, NOW(), NOW())""");
        long kpId = jdbc.queryForObject("SELECT MAX(id) FROM chapter_kp", Long.class);
        fake.reply("""
                {"questions": [
                  {"type": "single_choice", "assess_type": "scenario", "key_point_no": 1, "stem": "网店每单 1～99 件，哪组测试值最好？",
                   "options": ["0、1、99、100", "50", "1、99", "10、20"], "answer": "A",
                   "option_notes": ["", "测中间值最有代表性", "只测边界上，漏了边界外", "随便取值"], "analysis": "边界上和边界外都要测", "difficulty": 2},
                  {"type": "single_choice", "assess_type": "concept", "key_point_no": 2, "stem": "缺选项的坏题", "options": ["只有一个"], "answer": "A"},
                  {"type": "fill", "assess_type": "concept", "key_point_no": 2, "stem": "错误常发生在输入范围的____附近",
                   "accepts": ["边界", "边界值"], "analysis": ""}]}""")
                .then("{\"answers\": [\"B\", \"边界\"]}");
        String body = "{\"itemId\": 2, \"kpIds\": [" + kpId + "], \"assessTypes\": [\"concept\", \"scenario\"],"
                + " \"types\": [\"single_choice\", \"fill\"], \"perKp\": 3}";
        mockMvc.perform(post("/api/exercises/generate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.calls").value(2))
                .andExpect(jsonPath("$.data.kps[0].dropped").value(1))
                .andExpect(jsonPath("$.data.kps[0].points").value("2/2"))
                .andExpect(jsonPath("$.data.kps[0].misconceptions").value("1/1"))
                .andExpect(jsonPath("$.data.kps[0].flagged").value(1)); // AI 不看答案选了 B，和答案 A 不一样

        // 草稿学生看不到；覆盖检查里也只算草稿
        mockMvc.perform(get("/api/learn/items/2/questions")).andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/chapters/1/exercise-coverage"))
                .andExpect(jsonPath("$.data[0].drafts").value(2));
        mockMvc.perform(get("/api/chapters/1/exercises").param("itemId", "2"))
                .andExpect(jsonPath("$.data[2].source").value("ai"))
                .andExpect(jsonPath("$.data[2].checkNote", org.hamcrest.Matchers.containsString("可能有歧义")));

        mockMvc.perform(post("/api/exercises/confirm").param("itemId", "2"))
                .andExpect(jsonPath("$.data.confirmed").value(2));
        mockMvc.perform(get("/api/learn/items/2/questions"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[3].type").value("fill"));
    }

    @Test
    void importPastedQuestions() throws Exception {
        fake.reply("""
                {"questions": [{"type": "judge", "kp_no": 1, "assess_type": "concept", "why": "考边界外的值", "confident": false,
                  "stem": "边界值分析只需要测边界上的值。", "options": ["正确", "错误"], "answer": "B", "analysis": ""}]}""");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/exercises/import")
                        .param("itemId", "2").param("text", "1. 边界值分析只需要测边界上的值。（ ）答案：错"))
                .andExpect(jsonPath("$.data.created").value(1));
        mockMvc.perform(get("/api/chapters/1/exercises").param("itemId", "2"))
                .andExpect(jsonPath("$.data[2].type").value("judge"))
                .andExpect(jsonPath("$.data[2].draft").value(true))
                .andExpect(jsonPath("$.data[2].checkNote", org.hamcrest.Matchers.startsWith("识别没把握")));
    }

    @Test
    void assistantAnalysesPracticeAndGivesPlan() throws Exception {
        // 学生 5 号做第 1 章的练习：第 1 题选 B（老师写了误解说明），第 2 题答对
        jdbc.update("UPDATE exercise SET assess_type = 'scenario', key_point_no = 1 WHERE id = 1");
        jdbc.update("""
                INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, misconceptions,
                  sources, edited, knowledge_point_id, created_at, updated_at)
                VALUES (1, 0, '边界测试值的选取', '', '重点',
                  '[{"text":"边界外的值也要测，比如 0","sources":["D1-T0"]},{"text":"错误常发生在边界附近","sources":[]}]',
                  '[]', '[]', 0, 1, NOW(), NOW())""");
        mockMvc.perform(post("/api/learn/items/2/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\": {\"1\": \"B\", \"2\": \"B\"}}")).andExpect(status().isOk());
        String body = mockMvc.perform(get("/api/assistant/summary")).andReturn().getResponse().getContentAsString();
        JsonNode ch = json.readTree(body).path("data").path("chapters").get(0);
        JsonNode p = null;
        for (JsonNode x : ch.path("report").path("points")) {
            if (x.path("knowledgePointId").asLong() == 1) {
                p = x;
            }
        }
        assertThat(p).isNotNull();
        assertThat(p.path("details").toString()).contains("情境应用").contains("没掌握的要点 1").contains("容易想成");
        // 方案：先看资料（老师写在练习页上的文字 D1-T0 = 第 1 项），再做题
        assertThat(p.path("plan").get(0).path("kind").asText()).isEqualTo("review");
        assertThat(p.path("plan").get(0).path("itemId").asLong()).isEqualTo(1);
        assertThat(p.path("plan").toString()).contains("practice");
    }
}
