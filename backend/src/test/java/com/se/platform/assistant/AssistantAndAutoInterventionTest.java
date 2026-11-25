package com.se.platform.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import com.se.platform.ai.client.FakeAiClient;
import com.se.platform.common.DemoIdentity;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 个人学习助手 + 干预新方向（docs/计划-个人学习助手.md）。默认配置：A、B、AB 都自动执行，老师事后可以撤回。
 * 演示数据（第 1 章「边界值分析」，学习项 1 阅读、2 练习、3 AI 对话；知识点 1「边界测试值的选取」、2「边界错误的成因」）：
 * 学生03（id 7）→ A，学生04（8）→ B，学生07（11）→ AB；学生01（5）完成了学习项 1、2。
 * 知识点 1 有 7 人练过，其中 4 人薄弱（5、6、7、11）→ 全班预警。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AssistantAndAutoInterventionTest {

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

    private static MockHttpServletRequestBuilder as(long studentId, MockHttpServletRequestBuilder req) {
        return req.header(DemoIdentity.HEADER, String.valueOf(studentId));
    }

    private ResultActions board() throws Exception {
        return mockMvc.perform(get("/api/interventions/board").param("courseId", "1"));
    }

    private ResultActions summary(long studentId) throws Exception {
        return mockMvc.perform(as(studentId, get("/api/assistant/summary")));
    }

    private void finishItem(long studentId, int itemId) throws Exception {
        if (itemId == 1) {
            // 内容项 1 是文档：学完要读满 30 秒再点"我已读完"（ProgressTest 测这条），这里直接记一条学完
            jdbc.update("INSERT INTO learning_event (student_id, event_type, target_id, course_id, created_at) "
                    + "VALUES (?, 'finish_item', 1, 1, NOW())", studentId);
            return;
        }
        mockMvc.perform(as(studentId, post("/api/learn/events")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"finish_item\",\"itemId\":" + itemId + "}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ------------------------------------------------------------------ 干预：老师端

    @Test
    void aBAndAbAllRunAutomatically() throws Exception {
        board().andExpect(jsonPath("$.data.counts.open").value(0))
                .andExpect(jsonPath("$.data.counts.running").value(3))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 11)].type").value("REVIEW_PRACTICE"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 11)].auto").value(true))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 11)].step").value(1))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].type").value("PRACTICE"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].auto").value(true))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].beforeValue").value("0.00"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 8)].type").value("REVIEW"))
                .andExpect(jsonPath("$.data.interventions[?(@.studentId == 8)].status").value("approved"));
        // A 的题已经在学生03 的推荐表里，标为老师布置
        Integer assigned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommendation WHERE student_id = 7 AND source = 'teacher'", Integer.class);
        assertThat(assigned).isPositive();
    }

    @Test
    void studentGetsAutoInterventionWithoutTeacherOpeningBoard() throws Exception {
        mockMvc.perform(as(7, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running.length()").value(1))
                .andExpect(jsonPath("$.data.running[0].knowledgePointName").value("边界测试值的选取"))
                .andExpect(jsonPath("$.data.running[0].note", Matchers.containsString("缺少越过边界的值")));
        // AB 也自动：学生07 自己打开就收到，先复习（第 1 步），练习要到第 2 步才显示
        mockMvc.perform(as(11, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running.length()").value(1))
                .andExpect(jsonPath("$.data.running[0].type").value("REVIEW_PRACTICE"))
                .andExpect(jsonPath("$.data.running[0].exercises.length()").value(0));
    }

    @Test
    void teacherRevokes_studentNoLongerSeesIt_noRepeatWithoutNewData() throws Exception {
        String body = board().andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data.interventions[?(@.studentId == 7)].id");
        mockMvc.perform(post("/api/interventions/" + ids.get(0) + "/revoke"))
                .andExpect(jsonPath("$.data.status").value("revoked"));
        mockMvc.perform(as(7, get("/api/interventions/mine")))
                .andExpect(jsonPath("$.data.running.length()").value(0));
        // 没做对的题交回系统推荐
        Integer teacherRecs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommendation WHERE student_id = 7 AND source = 'teacher' AND status = 'new'",
                Integer.class);
        assertThat(teacherRecs).isZero();
        // 撤回后没有新数据，不会马上又自动建一条
        board().andExpect(jsonPath("$.data.interventions[?(@.studentId == 7)].status", Matchers.contains("revoked")));
        // 只能撤回执行中的
        mockMvc.perform(post("/api/interventions/" + ids.get(0) + "/revoke"))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void classAlert_whenTooManyStudentsWeakOnAPoint() throws Exception {
        board().andExpect(jsonPath("$.data.classAlerts.length()").value(1))
                .andExpect(jsonPath("$.data.classAlerts[0].name").value("边界测试值的选取"))
                .andExpect(jsonPath("$.data.classAlerts[0].weakStudents").value(4))
                .andExpect(jsonPath("$.data.classAlerts[0].practicedStudents").value(7))
                .andExpect(jsonPath("$.data.classAlerts[0].ratio").value(0.57))
                .andExpect(jsonPath("$.data.classAlerts[0].misconception").value("只取了边界和内侧，缺少越过边界的值"));
        // 侧边栏数字 = 全班预警 1（个人预警都已自动执行，没有待审核的）
        mockMvc.perform(get("/api/interventions/open-count")).andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void boardAndOpenCountAtTheSameTime_doNotCreateAlertsTwice() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> a = () -> board().andReturn().getResponse().getStatus();
            Callable<Integer> b = () -> mockMvc.perform(get("/api/interventions/open-count"))
                    .andReturn().getResponse().getStatus();
            List<Future<Integer>> results = pool.invokeAll(List.of(a, b, a, b));
            for (Future<Integer> f : results) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }
        Integer alerts = jdbc.queryForObject("SELECT COUNT(*) FROM alert", Integer.class);
        Integer interventions = jdbc.queryForObject("SELECT COUNT(*) FROM intervention", Integer.class);
        assertThat(alerts).isEqualTo(3);
        assertThat(interventions).isEqualTo(3);
    }

    // ------------------------------------------------------------------ 个人学习助手

    @Test
    void noStartedChapter_emptySummary() throws Exception {
        summary(9).andExpect(jsonPath("$.data.chapters.length()").value(0))
                .andExpect(jsonPath("$.data.focusChapterId").doesNotExist());
    }

    @Test
    void halfwayThroughChapter_showsNextItem_andPracticeReport() throws Exception {
        // 还没学完，但已经做过这一章的练习：先根据练习给一份分析（每个知识点做了几道、还要做几道）
        summary(5).andExpect(jsonPath("$.data.chapters.length()").value(1))
                .andExpect(jsonPath("$.data.chapters[0].done").value(2))
                .andExpect(jsonPath("$.data.chapters[0].total").value(3))
                .andExpect(jsonPath("$.data.chapters[0].completed").value(false))
                .andExpect(jsonPath("$.data.chapters[0].nextItemId").value(3))
                .andExpect(jsonPath("$.data.chapters[0].report.headline", Matchers.containsString("根据你目前的练习")))
                // 只看练习：知识点 1 第一次错了（红）→ 补练题（这里没有识别资料，所以没有“看资料”这一步）
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].plan[0].kind").value("remedy"))
                .andExpect(jsonPath("$.data.focusChapterId").value(1));
    }

    @Test
    void finishedChapter_personalReportWithBothLayersAndMisconception() throws Exception {
        finishItem(7, 1);
        finishItem(7, 2);
        summary(7).andExpect(jsonPath("$.data.chapters[0].completed").value(false));
        finishItem(7, 3);
        finishItem(7, 3);   // 重复完成不重复计
        summary(7).andExpect(jsonPath("$.data.chapters[0].completed").value(true))
                .andExpect(jsonPath("$.data.chapters[0].done").value(3))
                .andExpect(jsonPath("$.data.chapters[0].dialogueItemId").value(3))
                .andExpect(jsonPath("$.data.chapters[0].report.good").value(0))
                .andExpect(jsonPath("$.data.chapters[0].report.weak").value(1))
                .andExpect(jsonPath("$.data.chapters[0].report.headline", Matchers.containsString("1 个还没掌握，需要补")))
                // 知识点 1：只看练习，第一次 0、没补回来（红），反复同一个误解 → 看资料、做补练题
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].verdict").value("red"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].level").value("weak"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].firstMastery").value(0.0))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].action").value("remedy"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].misconception")
                        .value("只取了边界和内侧，缺少越过边界的值"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].text",
                        Matchers.containsString("第一次练习 0%")))
                // 知识点 2：没做过练习
                .andExpect(jsonPath("$.data.chapters[0].report.points[1].verdict").value("none"));
    }

    @Test
    void finishedButNoData_saysSoAndSuggestsPractice() throws Exception {
        // 学生08（12）：没有任何作答和对话，只把三项点完
        for (int item = 1; item <= 3; item++) {
            finishItem(12, item);
        }
        summary(12).andExpect(jsonPath("$.data.chapters[0].completed").value(true))
                .andExpect(jsonPath("$.data.chapters[0].report.headline",
                        Matchers.containsString("都还没有足够的数据判断")))
                .andExpect(jsonPath("$.data.chapters[0].report.headline",
                        Matchers.not(Matchers.containsString("没有明显薄弱"))))
                .andExpect(jsonPath("$.data.chapters[0].report.points[*].verdict", Matchers.contains("none", "none")))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].text").value("还没做过这个知识点的练习。"));
    }

    @Test
    void practiceGood_noRemedy_evenIfDialogueWeak() throws Exception {
        // 学生04：练习第一次就全对，对话里没讲出来。AI 对话不打分（2026-11-25），只看练习 → 绿，不用补
        for (int item = 1; item <= 3; item++) {
            finishItem(8, item);
        }
        summary(8).andExpect(jsonPath("$.data.chapters[0].report.points[0].verdict").value("green"))
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].action").doesNotExist())
                .andExpect(jsonPath("$.data.chapters[0].report.points[0].text").value("第一次练习就掌握了。"));
    }

    @Test
    void summaryNeverContainsAnswersOrOtherStudents() throws Exception {
        for (int item = 1; item <= 3; item++) {
            finishItem(7, item);
        }
        String body = summary(7).andReturn().getResponse().getContentAsString();
        // 题目标准答案、话题要点、别的学生
        assertThat(body).doesNotContain("\"answer\"").doesNotContain("keyPoints").doesNotContain("key_points")
                .doesNotContain("学生04").doesNotContain("学生03");
    }
}
