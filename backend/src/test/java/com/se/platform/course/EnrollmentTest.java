package com.se.platform.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.se.platform.common.DemoIdentity;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 选课：学生加入一门课后才能学；老师那边的“全班”只算选了这门课的学生。
 * 演示数据里 10 个学生（id 5～14）都已加入所有课程。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EnrollmentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void notJoined_cannotLearn_thenJoinAndLearn() throws Exception {
        jdbc.update("DELETE FROM enrollment WHERE student_id = 12 AND course_id = 1");
        mockMvc.perform(get("/api/learn/enrollments").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.data", Matchers.not(Matchers.hasItem(1))));
        // 没加入：学习页、取题都被拦
        mockMvc.perform(get("/api/learn/courses/1").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message", Matchers.containsString("加入课程")));
        mockMvc.perform(get("/api/learn/items/1/questions").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.code").value(403));
        // 加入后能学；重复加入没关系
        mockMvc.perform(post("/api/learn/courses/1/join").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/learn/courses/1/join").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/learn/courses/1").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.code").value(200));
        // 草稿课程不能加入
        mockMvc.perform(post("/api/learn/courses/4/join").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void teacherClassIsOnlyEnrolledStudents() throws Exception {
        mockMvc.perform(get("/api/courses/mine/1")).andExpect(jsonPath("$.data.studentCount").value(10));
        // 两个学生退出这门课：课程人数、截止情况的全班人数都跟着变
        jdbc.update("DELETE FROM enrollment WHERE course_id = 1 AND student_id IN (13, 14)");
        mockMvc.perform(get("/api/courses/mine/1")).andExpect(jsonPath("$.data.studentCount").value(8));
        mockMvc.perform(get("/api/teacher/deadlines"))
                .andExpect(jsonPath("$.data[*].total", Matchers.everyItem(Matchers.is(8))))
                .andExpect(jsonPath("$.data[*].missing[*].id", Matchers.not(Matchers.hasItems(13, 14))));
    }
}
