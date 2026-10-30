package com.se.platform.record;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** 学习记录：交卷、看内容、学完一项都会记下来，学习记录页能看到 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedRecordsShowUp() throws Exception {
        // 种子数据：两次交卷（第一次错 1 题），学完阅读和练习两项，共 24 分钟
        mockMvc.perform(get("/api/learn/records"))
                .andExpect(jsonPath("$.data.summary.quizAttempts").value(2))
                .andExpect(jsonPath("$.data.summary.questionsAnswered").value(4))
                .andExpect(jsonPath("$.data.summary.correctAnswers").value(3))
                .andExpect(jsonPath("$.data.summary.itemsFinished").value(2))
                .andExpect(jsonPath("$.data.summary.minutes").value(24))
                .andExpect(jsonPath("$.data.quizzes[0].correct").value(2))
                .andExpect(jsonPath("$.data.quizzes[1].wrong[0].chosen").value("B"))
                .andExpect(jsonPath("$.data.quizzes[1].wrong[0].answer").value("A"))
                .andExpect(jsonPath("$.data.courses[0].courseName").value("软件测试"))
                .andExpect(jsonPath("$.data.courses[0].done").value(2))
                .andExpect(jsonPath("$.data.courses[0].total").value(3));

        // 学习页打开时，学完的项会打勾
        mockMvc.perform(get("/api/learn/courses/1"))
                .andExpect(jsonPath("$.data.doneItemIds", Matchers.containsInAnyOrder(1, 2)));
    }

    @Test
    void submittingAndStudyingAreRecorded() throws Exception {
        mockMvc.perform(post("/api/learn/items/2/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\": {\"1\": \"C\"}}"));
        mockMvc.perform(post("/api/learn/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"view_item\", \"itemId\": 3, \"durationSec\": 90}"));
        mockMvc.perform(post("/api/learn/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"finish_item\", \"itemId\": 3}"));

        // 最新一次交卷：第 1 题选 C 错、第 2 题没答也算错
        mockMvc.perform(get("/api/learn/records"))
                .andExpect(jsonPath("$.data.summary.quizAttempts").value(3))
                .andExpect(jsonPath("$.data.quizzes[0].correct").value(0))
                .andExpect(jsonPath("$.data.quizzes[0].wrong.length()").value(2))
                .andExpect(jsonPath("$.data.quizzes[0].wrong[1].chosen").doesNotExist())
                .andExpect(jsonPath("$.data.courses[0].done").value(3))
                .andExpect(jsonPath("$.data.timeline[0].kind").value(Matchers.oneOf("finish", "study", "quiz")));

        // 不认识的行为类型拒绝
        mockMvc.perform(post("/api/learn/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"hack\", \"itemId\": 3}"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
