package com.se.platform.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.se.platform.diagnosis.service.MasteryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** 学情诊断：掌握度公式、交卷后更新、学生看自己、老师看整门课 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DiagnosisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void masteryFormula() {
        assertThat(MasteryService.compute(List.of())).isNull();
        // 每道题最近一次的得分求平均，不加权、不打折
        assertThat(MasteryService.compute(List.of(1.0))).isEqualTo(1.0);
        assertThat(MasteryService.compute(List.of(0.0, 1.0, 1.0))).isEqualTo(0.6667);
        assertThat(MasteryService.level(0.59)).isEqualTo("weak");
        assertThat(MasteryService.level(0.6)).isEqualTo("medium");
        assertThat(MasteryService.level(0.8)).isEqualTo("good");
        assertThat(MasteryService.level(null)).isEqualTo("none");
    }

    @Test
    void studentSeesWeakPointsWithReasons() throws Exception {
        mockMvc.perform(get("/api/diagnosis/me"))
                .andExpect(jsonPath("$.data.studentName").value("学生01"))
                .andExpect(jsonPath("$.data.summary.total").value(2))
                .andExpect(jsonPath("$.data.summary.weak").value(1))
                .andExpect(jsonPath("$.data.summary.medium").value(0))
                .andExpect(jsonPath("$.data.weakPoints[0].name").value("边界测试值的选取"))
                .andExpect(jsonPath("$.data.weakPoints[0].recent[0]").value(false))
                .andExpect(jsonPath("$.data.weakPoints[0].recent[1]").value(true))
                .andExpect(jsonPath("$.data.weakPoints[0].firstMastery").value(0.0))
                .andExpect(jsonPath("$.data.weakPoints[0].remedyStatus").doesNotExist())
                // 第一次选了 B，老师写的误解说明
                .andExpect(jsonPath("$.data.weakPoints[0].misconception").value("把上下边界和中间值混在一起，没有取边界外侧的值"))
                .andExpect(jsonPath("$.data.weakPoints[0].practiceItemId").value(2))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].points.length()").value(2));
    }

    @Test
    void submittingUpdatesMastery() throws Exception {
        // 再交一次全对：掌握度只算第一次作答，重做不改，还是 0（薄弱）
        mockMvc.perform(post("/api/learn/items/2/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": {\"1\": \"A\", \"2\": \"B\"}}"))
                .andExpect(jsonPath("$.data.masteryChanges[?(@.knowledgePointId == 1)].before").value(0.0))
                .andExpect(jsonPath("$.data.masteryChanges[?(@.knowledgePointId == 1)].after").value(0.0))
                .andExpect(jsonPath("$.data.masteryChanges[?(@.knowledgePointId == 1)].level").value("weak"));

        // 补练（补回来）只在小助手里做，见 RemedyAndChatTest
        mockMvc.perform(get("/api/diagnosis/me"))
                .andExpect(jsonPath("$.data.summary.weak").value(1))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].points[0].firstMastery").value(0.0))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].points[0].firstLevel").value("weak"))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].points[0].remedyStatus").doesNotExist());
    }

    @Test
    void teacherSeesWholeCourse() throws Exception {
        mockMvc.perform(get("/api/diagnosis/courses/1"))
                // 种子数据：学生01～07 做过练习（学生02～07 是干预的演示数据）
                .andExpect(jsonPath("$.data.studentCount").value(7))
                .andExpect(jsonPath("$.data.points.length()").value(2))
                // 知识点 1 当前掌握度：学生01 0、02 0、03 0、04 1、05 1、06 1（补回来了）、07 0.5 → 3.5/7
                .andExpect(jsonPath("$.data.points[0].avgMastery").value(0.5))
                .andExpect(jsonPath("$.data.points[0].weakStudents").value(4))
                .andExpect(jsonPath("$.data.points[0].misconceptions[0].note").value("只取了边界和内侧，缺少越过边界的值"))
                .andExpect(jsonPath("$.data.points[0].misconceptions[0].count").value(7));

        // 别的老师的课不能看（1 号课属于李老师，5 号课不是）
        mockMvc.perform(get("/api/diagnosis/courses/5")).andExpect(jsonPath("$.code").value(403));
    }
}
