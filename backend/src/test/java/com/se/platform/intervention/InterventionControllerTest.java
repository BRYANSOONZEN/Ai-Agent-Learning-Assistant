package com.se.platform.intervention;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 干预 A、B（docs/计划-干预A和B.md）。演示数据：学生03（id 7）→ A，学生04（8）→ B，学生07（11）→ AB；
 * 学生01、02、05、06 不报警。学生端用请求头 X-Demo-Student-Id 切换演示学生。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InterventionControllerTest {

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
    private FakeAiClient fake;

    private ResultActions board() throws Exception {
        return mockMvc.perform(get("/api/interventions/board").param("courseId", "1"));
    }

    private static MockHttpServletRequestBuilder as(long studentId, MockHttpServletRequestBuilder req) {
        return req.header(DemoIdentity.HEADER, String.valueOf(studentId));
    }

    /** 某个学生那条预警的系统建议 id */
    private int suggestionOf(String body, int studentId) {
        List<Integer> ids = JsonPath.read(body, "$.data.alerts[?(@.studentId == " + studentId + ")].suggestion.id");
        return ids.get(0);
    }

    private void approve(int interventionId) throws Exception {
        mockMvc.perform(post("/api/interventions/" + interventionId + "/review")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("approved"));
    }

    @Test
    void scanFindsABAndAB_onlyForTheRightStudents() throws Exception {
        board().andExpect(jsonPath("$.data.counts.open").value(3))
                // 高级在前：学生03（A，掌握度 0.22 < 0.4）、学生07（AB），然后学生04（B）
                .andExpect(jsonPath("$.data.alerts[*].studentId", Matchers.contains(7, 11, 8)))
                .andExpect(jsonPath("$.data.alerts[*].type", Matchers.contains("A", "AB", "B")))
                .andExpect(jsonPath("$.data.alerts[*].level", Matchers.contains("high", "high", "medium")))
                .andExpect(jsonPath("$.data.alerts[0].studentName").value("学生03"))
                .andExpect(jsonPath("$.data.alerts[0].knowledgePointName").value("边界测试值的选取"))
                // A：两层证据、主要误解，建议先挑含这个误解选项的题
                .andExpect(jsonPath("$.data.alerts[0].evidence.concept").value("good"))
                .andExpect(jsonPath("$.data.alerts[0].evidence.application").value("bad"))
                .andExpect(jsonPath("$.data.alerts[0].evidence.mastery").value(0.2168))
                .andExpect(jsonPath("$.data.alerts[0].evidence.misconception").value("只取了边界和内侧，缺少越过边界的值"))
                .andExpect(jsonPath("$.data.alerts[0].evidence.recentAnswers.length()").value(4))
                .andExpect(jsonPath("$.data.alerts[0].suggestion.type").value("PRACTICE"))
                .andExpect(jsonPath("$.data.alerts[0].suggestion.status").value("pending"))
                .andExpect(jsonPath("$.data.alerts[0].suggestion.exercises[*].exerciseId", Matchers.contains(1, 3, 5)))
                .andExpect(jsonPath("$.data.alerts[0].suggestion.note", Matchers.containsString("缺少越过边界的值")))
                // AB：两次错因不同，留言用通用模板；先复习 + 对话，再练习
                .andExpect(jsonPath("$.data.alerts[1].evidence.misconception").doesNotExist())
                .andExpect(jsonPath("$.data.alerts[1].suggestion.type").value("REVIEW_PRACTICE"))
                .andExpect(jsonPath("$.data.alerts[1].suggestion.items[0].title").value("阅读：边界值分析要点"))
                .andExpect(jsonPath("$.data.alerts[1].suggestion.dialogue.itemId").value(3))
                // B：概念层看到被系统讲解的话题
                .andExpect(jsonPath("$.data.alerts[2].evidence.concept").value("bad"))
                .andExpect(jsonPath("$.data.alerts[2].evidence.application").value("good"))
                .andExpect(jsonPath("$.data.alerts[2].evidence.topics[0].title").value("下边界取哪些值"))
                .andExpect(jsonPath("$.data.alerts[2].evidence.topics[0].result").value("REVEALED"))
                .andExpect(jsonPath("$.data.alerts[2].suggestion.type").value("REVIEW"))
                .andExpect(jsonPath("$.data.alerts[2].suggestion.exercises.length()").value(0));

        // 再扫一次：没有新数据，不重复
        board().andExpect(jsonPath("$.data.counts.open").value(3));
    }

    @Test
    void practiceA_assignedThenDone_masteryUp() throws Exception {
        String body = board().andReturn().getResponse().getContentAsString();
        int id = suggestionOf(body, 7);
        approve(id);

        // 学生03 在"老师布置"里看到 3 道题；系统推荐里不再重复出现这几道
        mockMvc.perform(as(7, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running.length()").value(1))
                .andExpect(jsonPath("$.data.running[0].note", Matchers.containsString("缺少越过边界的值")))
                .andExpect(jsonPath("$.data.running[0].exercises[*].exerciseId", Matchers.contains(1, 3, 5)))
                .andExpect(jsonPath("$.data.running[0].exercises[0].recommendationId").isNumber())
                .andExpect(jsonPath("$.data.running[0].exercises[0].answered").value(false));
        // 老师在干预这个知识点：系统推荐先让出来，按"老师布置"做
        mockMvc.perform(as(7, get("/api/recommendations")))
                .andExpect(jsonPath("$.data.weakCount").value(1))
                .andExpect(jsonPath("$.data.points.length()").value(0));

        // 在推荐页做这 3 道（复用推荐的做题接口），全对 → 掌握度上升
        mockMvc.perform(as(7, post("/api/recommendations/practice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"1\":\"A\",\"3\":\"A\",\"5\":\"B\"}}"))
                .andExpect(jsonPath("$.data.correct").value(3));

        board().andExpect(jsonPath("$.data.counts.open").value(2))
                .andExpect(jsonPath("$.data.counts.done").value(1))
                .andExpect(jsonPath("$.data.counts.effective").value(1))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].status").value("done"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].beforeValue").value("0.22"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].afterValue").value("0.70"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].effective").value(true));
        mockMvc.perform(as(7, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running.length()").value(0))
                .andExpect(jsonPath("$.data.done.length()").value(1));
    }

    @Test
    void reviewB_needsReadingAndNewDialogue() throws Exception {
        int id = suggestionOf(board().andReturn().getResponse().getContentAsString(), 8);
        approve(id);

        // 只看了课件还不算完成
        mockMvc.perform(as(8, post("/api/learn/events")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"view_item\",\"itemId\":1,\"durationSec\":120}"));
        mockMvc.perform(as(8, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running[0].items[0].viewed").value(true))
                .andExpect(jsonPath("$.data.running[0].dialogue.finished").value(false));

        // 重做对话：两个话题都自己答出
        String started = mockMvc.perform(as(8, post("/api/dialogue/sessions")).param("chapterId", "1")
                .param("itemId", "3")).andReturn().getResponse().getContentAsString();
        int sessionId = JsonPath.read(started, "$.data.sessionId");
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1,2,3],\"reply\":\"对。\"}");
        say(8, sessionId, "0、1、2");
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1,2,3],\"reply\":\"对。\"}");
        say(8, sessionId, "边界条件容易写错，只有边界附近能发现，中间值发现不了")
                .andExpect(jsonPath("$.data.status").value("FINISHED"));

        board().andExpect(jsonPath("$.data.interventions[?(@.studentId == 8)].status").value("done"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 8)].beforeValue").value("bad"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 8)].afterValue").value("good"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 8)].effective").value(true));
    }

    @Test
    void reviewPracticeAB_exercisesOnlyAfterStep1() throws Exception {
        int id = suggestionOf(board().andReturn().getResponse().getContentAsString(), 11);
        approve(id);

        // 第 1 步：学生只看到复习和对话，看不到练习
        mockMvc.perform(as(11, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running[0].step").value(1))
                .andExpect(jsonPath("$.data.running[0].exercises.length()").value(0))
                .andExpect(jsonPath("$.data.running[0].items.length()").value(1));
        mockMvc.perform(as(11, get("/api/recommendations")))
                .andExpect(jsonPath("$.data.points.length()").value(0));

        mockMvc.perform(as(11, post("/api/learn/events")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"view_item\",\"itemId\":1,\"durationSec\":60}"));
        String started = mockMvc.perform(as(11, post("/api/dialogue/sessions")).param("chapterId", "1")
                .param("itemId", "3")).andReturn().getResponse().getContentAsString();
        int sessionId = JsonPath.read(started, "$.data.sessionId");
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1,2,3],\"reply\":\"对。\"}");
        say(11, sessionId, "0、1、2");
        fake.reply("{\"type\":\"correct\",\"hit_points\":[1,2,3],\"reply\":\"对。\"}");
        say(11, sessionId, "边界条件容易写错");

        // 第 2 步：练习出现在"老师布置"里
        mockMvc.perform(as(11, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running[0].step").value(2))
                .andExpect(jsonPath("$.data.running[0].exercises.length()").value(3));
        mockMvc.perform(as(11, post("/api/recommendations/practice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"1\":\"B\",\"3\":\"A\",\"5\":\"B\"}}"))
                .andExpect(jsonPath("$.data.correct").value(2));

        board().andExpect(jsonPath("$.data.interventions[?(@.studentId == 11)].status").value("done"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 11)].beforeValue").value("0.33"));
    }

    @Test
    void rejectAndIgnore_closeAlert_noRepeatWithoutNewData() throws Exception {
        String body = board().andReturn().getResponse().getContentAsString();
        // 驳回学生03 的建议 → 预警关闭
        mockMvc.perform(post("/api/interventions/" + suggestionOf(body, 7) + "/review")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": false}"))
                .andExpect(jsonPath("$.data.status").value("rejected"));
        // 忽略学生04 的预警
        List<Integer> alertIds = JsonPath.read(body, "$.data.alerts[?(@.studentId == 8)].id");
        mockMvc.perform(put("/api/alerts/" + alertIds.get(0)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ignored\"}"))
                .andExpect(jsonPath("$.code").value(200));

        // 没有新数据，再扫描不会冒出来
        board().andExpect(jsonPath("$.data.counts.open").value(1))
                .andExpect(jsonPath("$.data.alerts[0].studentId").value(11));

        // 已审核过的不能再审
        mockMvc.perform(post("/api/interventions/" + suggestionOf(body, 7) + "/review")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void teacherCanChangeExercisesAndNote_butOnlyThisPointsExercises() throws Exception {
        int id = suggestionOf(board().andReturn().getResponse().getContentAsString(), 7);
        // 第 2 题属于知识点 2，不能布置
        mockMvc.perform(post("/api/interventions/" + id + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\": true, \"exerciseIds\": [2]}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/interventions/" + id + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\": true, \"exerciseIds\": [3], \"note\": \"先把第 3 题做对\"}"))
                .andExpect(jsonPath("$.data.exercises[*].exerciseId", Matchers.contains(3)))
                .andExpect(jsonPath("$.data.note").value("先把第 3 题做对"));
    }

    private ResultActions say(long studentId, int sessionId, String text) throws Exception {
        return mockMvc.perform(as(studentId, post("/api/dialogue/sessions/" + sessionId + "/messages"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"" + text + "\"}"));
    }
}
