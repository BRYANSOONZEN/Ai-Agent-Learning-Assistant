package com.se.platform.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** 学生画像：全部从学习记录和掌握度现算，每个维度和标签都有依据 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void profileComesFromRecords() throws Exception {
        mockMvc.perform(get("/api/profiles/me"))
                .andExpect(jsonPath("$.data.studentName").value("学生01"))
                // 种子数据：近 3 天学了 24 分钟，答对 3 / 4，1 个薄弱知识点
                .andExpect(jsonPath("$.data.stats.minutes").value(24))
                .andExpect(jsonPath("$.data.stats.activeDays14").value(Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.stats.accuracy").value(0.75))
                .andExpect(jsonPath("$.data.stats.weakPoints").value(1))
                .andExpect(jsonPath("$.data.days.length()").value(14))
                .andExpect(jsonPath("$.data.quizTrend.length()").value(2))
                .andExpect(jsonPath("$.data.quizTrend[0].correct").value(1))
                // 维度：完成 2 / 3 项；做错后重做 1 组且提高了；没有对话数据的维度留空
                .andExpect(jsonPath("$.data.dimensions[?(@.key == 'progress')].value").value(0.6667))
                .andExpect(jsonPath("$.data.dimensions[?(@.key == 'correction')].value").value(1.0))
                .andExpect(jsonPath("$.data.dimensions[?(@.key == 'independence')].value").value(Matchers.contains(Matchers.nullValue())))
                // 标签都带依据
                .andExpect(jsonPath("$.data.tags[*].name", Matchers.hasItems("错了会回头订正", "薄弱：边界测试值的选取")))
                .andExpect(jsonPath("$.data.tags[*].reason", Matchers.everyItem(Matchers.not(Matchers.emptyOrNullString()))));
    }
}
