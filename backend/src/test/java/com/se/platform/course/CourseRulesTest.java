package com.se.platform.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 课程和章节的几条后端规则（docs/模块说明/02-课程内容.md 第九节原来写的“还没做”）：
 * 每页最多 50 条、老师只看自己的课、课程名不重复、新项排在最后、发布前后端再检查一遍。
 * 演示数据：李老师（id 2）有课程 1、2、4；课程 3 是王老师的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CourseRulesTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String body) throws Exception {
        return mockMvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long id(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    @Test
    void pageSizeIsCappedAt50() throws Exception {
        for (int i = 0; i < 60; i++) {
            jdbc.update("INSERT INTO course (name, description, teacher_id, status, created_at, updated_at) "
                    + "VALUES (?, '', 2, 'draft', NOW(), NOW())", "批量课程 " + i);
        }
        mockMvc.perform(get("/api/courses/mine").param("size", "1000"))
                .andExpect(jsonPath("$.data.records.length()").value(50))
                .andExpect(jsonPath("$.data.total").value(63));
    }

    @Test
    void teacherOnlySeesOwnCourses() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(jsonPath("$.data.records[*].id", Matchers.containsInAnyOrder(1, 2, 4)));
        mockMvc.perform(get("/api/courses/3")).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/courses/1")).andExpect(jsonPath("$.data.name").value("软件测试"));
    }

    @Test
    void courseNameMustBeUniqueForTheSameTeacher() throws Exception {
        send(post("/api/courses"), "{\"name\": \"软件测试\"}")
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message", Matchers.containsString("已经有一门叫「软件测试」")));
        // 别的老师的课名可以一样（需求工程是王老师的）
        send(post("/api/courses"), "{\"name\": \"需求工程\"}").andExpect(jsonPath("$.code").value(200));
        // 改名改成自己另一门课的名字也不行；名字不变照常保存
        send(put("/api/courses/2"), "{\"name\": \"软件测试\"}").andExpect(jsonPath("$.code").value(409));
        send(put("/api/courses/2"), "{\"name\": \"软件工程导论\", \"description\": \"新简介\"}")
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void newItemGoesLastEvenAfterDeletingOne() throws Exception {
        long ch = id(send(post("/api/chapters"), "{\"courseId\": 2, \"title\": \"新章\"}"));
        long a = id(send(post("/api/chapter-items"), "{\"chapterId\": %d, \"type\": \"document\", \"title\": \"A\"}".formatted(ch)));
        send(post("/api/chapter-items"), "{\"chapterId\": %d, \"type\": \"document\", \"title\": \"B\"}".formatted(ch));
        send(post("/api/chapter-items"), "{\"chapterId\": %d, \"type\": \"document\", \"title\": \"C\"}".formatted(ch));
        mockMvc.perform(delete("/api/chapter-items/" + a)).andExpect(jsonPath("$.code").value(200));
        send(post("/api/chapter-items"), "{\"chapterId\": %d, \"type\": \"document\", \"title\": \"D\"}".formatted(ch));
        mockMvc.perform(get("/api/chapters/" + ch + "/items"))
                .andExpect(jsonPath("$.data[*].title", Matchers.contains("B", "C", "D")));
    }

    @Test
    void publishIsCheckedOnTheBackendToo() throws Exception {
        long ch = id(send(post("/api/chapters"), "{\"courseId\": 2, \"title\": \"新章\"}"));
        String publish = "{\"courseId\": 2, \"title\": \"新章\", \"intro\": %s, \"status\": \"published\"}";
        send(put("/api/chapters/" + ch), publish.formatted("null"))
                .andExpect(jsonPath("$.message").value("发布前请先写章节介绍"));
        send(put("/api/chapters/" + ch), publish.formatted("\"介绍\""))
                .andExpect(jsonPath("$.message").value("发布前至少要有一项内容"));
        send(post("/api/chapter-items"), "{\"chapterId\": %d, \"type\": \"document\", \"title\": \"空文档\"}".formatted(ch));
        send(put("/api/chapters/" + ch), publish.formatted("\"介绍\""))
                .andExpect(jsonPath("$.message").value("这些内容还不完整，不能发布：空文档"));
        // 文档写上正文就完整了，可以发布
        long item = JsonPath.<Number>read(mockMvc.perform(get("/api/chapters/" + ch + "/items"))
                .andReturn().getResponse().getContentAsString(), "$.data[0].id").longValue();
        send(put("/api/chapter-items/" + item), "{\"title\": \"空文档\", \"description\": \"正文\"}")
                .andExpect(jsonPath("$.code").value(200));
        send(put("/api/chapters/" + ch), publish.formatted("\"介绍\"")).andExpect(jsonPath("$.data.status").value("published"));
        // 已发布的章节改标题、介绍不再卡这 3 条
        send(put("/api/chapters/" + ch), "{\"courseId\": 2, \"title\": \"改名\", \"intro\": \"介绍\", \"status\": \"published\"}")
                .andExpect(jsonPath("$.code").value(200));
    }
}
