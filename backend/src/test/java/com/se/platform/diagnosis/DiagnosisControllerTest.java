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
        // 少于 3 次要打折：做对 1 道只有 0.3333
        assertThat(MasteryService.compute(List.of(true))).isEqualTo(0.3333);
        // 先错后对：(0.8×0 + 1×1) / 1.8 × 2/3
        assertThat(MasteryService.compute(List.of(false, true))).isEqualTo(0.3704);
        // 越近的权重越大：最后错一次比最先错一次低
        assertThat(MasteryService.compute(List.of(true, true, false)))
                .isLessThan(MasteryService.compute(List.of(false, true, true)));
        // 只看最近 10 次：很久以前的错不再影响
        List<Boolean> old = new java.util.ArrayList<>(List.of(false, false, false));
        old.addAll(java.util.Collections.nCopies(10, true));
        assertThat(MasteryService.compute(old)).isEqualTo(1.0);
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
                .andExpect(jsonPath("$.data.summary.medium").value(1))
                .andExpect(jsonPath("$.data.weakPoints[0].name").value("边界测试值的选取"))
                .andExpect(jsonPath("$.data.weakPoints[0].recent[0]").value(false))
                .andExpect(jsonPath("$.data.weakPoints[0].recent[1]").value(true))
                // 第一次选了 B，老师写的误解说明
                .andExpect(jsonPath("$.data.weakPoints[0].misconception").value("把上下边界和中间值混在一起，没有取边界外侧的值"))
                .andExpect(jsonPath("$.data.weakPoints[0].practiceItemId").value(2))
                .andExpect(jsonPath("$.data.courses[0].chapters[0].points.length()").value(2));
    }

    @Test
    void submittingUpdatesMastery() throws Exception {
        // 再全对一次：知识点 1 从 0.3704（薄弱）升到 (0.64×0 + 0.8 + 1) / 2.44 = 0.7377（一般）
        mockMvc.perform(post("/api/learn/items/2/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": {\"1\": \"A\", \"2\": \"B\"}}"))
                .andExpect(jsonPath("$.data.masteryChanges[?(@.knowledgePointId == 1)].before").value(0.3704))
                .andExpect(jsonPath("$.data.masteryChanges[?(@.knowledgePointId == 1)].after").value(0.7377))
                .andExpect(jsonPath("$.data.masteryChanges[?(@.knowledgePointId == 1)].level").value("medium"));

        mockMvc.perform(get("/api/diagnosis/me")).andExpect(jsonPath("$.data.summary.weak").value(0));
    }

    @Test
    void teacherSeesWholeCourse() throws Exception {
        mockMvc.perform(get("/api/diagnosis/courses/1"))
                .andExpect(jsonPath("$.data.studentCount").value(1))
                .andExpect(jsonPath("$.data.points.length()").value(2))
                .andExpect(jsonPath("$.data.points[0].avgMastery").value(0.3704))
                .andExpect(jsonPath("$.data.points[0].weakStudents").value(1))
                .andExpect(jsonPath("$.data.points[0].misconceptions[0].count").value(1));

        // 别的老师的课不能看（1 号课属于李老师，5 号课不是）
        mockMvc.perform(get("/api/diagnosis/courses/5")).andExpect(jsonPath("$.code").value(403));
    }
}
