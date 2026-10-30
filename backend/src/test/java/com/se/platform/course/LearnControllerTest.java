package com.se.platform.course;

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

/** 学生学习接口：只看到已发布的内容；题目交卷前不带答案 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LearnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void onlyPublishedCoursesAndChapters() throws Exception {
        // 种子数据里 6 门课有 4 门已发布
        mockMvc.perform(get("/api/learn/courses"))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[*].status", Matchers.everyItem(Matchers.is("published"))));

        // 「软件测试」：第 1 章已发布，第 2 章是草稿，学生只看到第 1 章和它的 3 项内容
        mockMvc.perform(get("/api/learn/courses/1"))
                .andExpect(jsonPath("$.data.course.name").value("软件测试"))
                .andExpect(jsonPath("$.data.chapters.length()").value(1))
                .andExpect(jsonPath("$.data.chapters[0].items.length()").value(3))
                .andExpect(jsonPath("$.data.chapters[0].items[1].type").value("exercise"));

        // 草稿课程（4 号）学生打不开
        mockMvc.perform(get("/api/learn/courses/4")).andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void quizHidesAnswersUntilSubmitted() throws Exception {
        mockMvc.perform(get("/api/learn/items/2/questions"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data[0].options.length()").value(4));

        // 第 1 题答案 A，选 B 错，带误解说明；第 2 题答案 B，答对
        mockMvc.perform(post("/api/learn/items/2/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": {\"1\": \"B\", \"2\": \"b\"}}"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.correct").value(1))
                .andExpect(jsonPath("$.data.results[0].correct").value(false))
                .andExpect(jsonPath("$.data.results[0].answer").value("A"))
                .andExpect(jsonPath("$.data.results[0].note").value("把上下边界和中间值混在一起，没有取边界外侧的值"))
                .andExpect(jsonPath("$.data.results[1].correct").value(true))
                .andExpect(jsonPath("$.data.results[1].note").doesNotExist());

        // 不是练习的内容项不能拿来做题
        mockMvc.perform(get("/api/learn/items/1/questions")).andExpect(jsonPath("$.code").value(400));
    }
}
