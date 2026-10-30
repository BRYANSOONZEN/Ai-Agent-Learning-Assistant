package com.se.platform.recommend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** 学习推荐：按薄弱知识点挑题、在推荐页做题会更新掌握度、做对的和换掉的不再推 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RecommendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void recommendsForWeakPoint() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(jsonPath("$.data.weakCount").value(1))
                .andExpect(jsonPath("$.data.points.length()").value(1))
                .andExpect(jsonPath("$.data.points[0].name").value("边界测试值的选取"))
                .andExpect(jsonPath("$.data.points[0].misconception").isNotEmpty())
                .andExpect(jsonPath("$.data.points[0].review[0].title").value("阅读：边界值分析要点"))
                // 种子题库：3 道没做过的题，难度和知识点一样的在前；题目不带答案
                .andExpect(jsonPath("$.data.points[0].exercises.length()").value(3))
                .andExpect(jsonPath("$.data.points[0].exercises[*].exerciseId", Matchers.contains(4, 5, 3)))
                .andExpect(jsonPath("$.data.points[0].exercises[0].reason").value("还没做过"))
                .andExpect(jsonPath("$.data.points[0].exercises[0].answer").doesNotExist());
    }

    @Test
    void practiceUpdatesMasteryAndList() throws Exception {
        mockMvc.perform(get("/api/recommendations"));

        // 第 5 题做错：记进学习记录、掌握度下降，留在推荐里，理由变成「上次做错了」
        mockMvc.perform(post("/api/recommendations/practice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"5\":\"A\"}}"))
                .andExpect(jsonPath("$.data.correct").value(0))
                .andExpect(jsonPath("$.data.results[0].note").isNotEmpty())
                .andExpect(jsonPath("$.data.masteryChanges[0].before").value(0.3704))
                .andExpect(jsonPath("$.data.masteryChanges[0].after").value(Matchers.lessThan(0.3704)));
        // 第 4 题做对：掌握度回升，这道题标为已完成
        mockMvc.perform(post("/api/recommendations/practice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"4\":\"C\"}}"))
                .andExpect(jsonPath("$.data.correct").value(1))
                .andExpect(jsonPath("$.data.masteryChanges[0].after").value(Matchers.greaterThan(0.3704)));

        String body = mockMvc.perform(get("/api/recommendations"))
                .andExpect(jsonPath("$.data.doneCount").value(1))
                .andExpect(jsonPath("$.data.points[0].exercises[0].exerciseId").value(5))
                .andExpect(jsonPath("$.data.points[0].exercises[0].reason").value("上次做错了"))
                .andExpect(jsonPath("$.data.points[0].exercises[*].exerciseId", Matchers.not(Matchers.hasItem(4))))
                .andReturn().getResponse().getContentAsString();

        // 换掉第 3 题，以后不再推
        java.util.List<Integer> ids = JsonPath.read(body,
                "$.data.points[0].exercises[?(@.exerciseId == 3)].recommendationId");
        Integer recId = ids.get(0);
        mockMvc.perform(post("/api/recommendations/" + recId + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"dismissed\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(jsonPath("$.data.points[0].exercises[*].exerciseId", Matchers.not(Matchers.hasItem(3))));

        // 在推荐页的做题记录出现在学习记录里
        mockMvc.perform(get("/api/learn/records"))
                .andExpect(jsonPath("$.data.quizzes[0].title").value("推荐练习"));
    }

    @Test
    void cannotPracticeOtherExercises() throws Exception {
        mockMvc.perform(get("/api/recommendations"));
        // 第 2 题不在推荐里（所属知识点不薄弱）
        mockMvc.perform(post("/api/recommendations/practice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"2\":\"B\"}}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/recommendations/1/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"whatever\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
