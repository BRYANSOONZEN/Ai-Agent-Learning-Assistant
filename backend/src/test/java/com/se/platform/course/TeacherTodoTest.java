package com.se.platform.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** 老师首页“待你确认”：知识点、AI 对话草稿、AI 出的题、简答，按课程章节列出，点了去建课页那一项 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TeacherTodoTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void nothingToConfirm_emptyList() throws Exception {
        mockMvc.perform(get("/api/teacher/todo")).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listsEverythingWaitingForTheTeacher() throws Exception {
        // 第 1 章：知识点识别完待确认（2 个）、AI 对话草稿待确认、练习里 1 道 AI 题还是草稿、1 份简答等老师确认
        jdbc.update("INSERT INTO chapter_kp_state (chapter_id, upload_done, status, message, created_at, updated_at) "
                + "VALUES (1, 1, 'draft', '识别完成', NOW(), NOW())");
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, "
                    + "misconceptions, sources, edited, created_at, updated_at) "
                    + "VALUES (1, ?, '知识点', '', '一般', '[]', '[]', '[]', 0, NOW(), NOW())", i);
        }
        jdbc.update("INSERT INTO feynman_setup (item_id, chapter_id, kp_ids, drafts, status, created_at, updated_at) "
                + "VALUES (3, 1, '1', '[]', 'draft', NOW(), NOW())");
        jdbc.update("UPDATE exercise SET draft = 1 WHERE id = (SELECT MIN(id) FROM exercise WHERE item_id = 2)");
        jdbc.update("UPDATE answer_record SET status = 'pending' WHERE id = (SELECT MIN(id) FROM answer_record WHERE item_id = 2)");

        mockMvc.perform(get("/api/teacher/todo"))
                .andExpect(jsonPath("$.data[*].kind", Matchers.contains("kp", "feynman", "exercise", "short")))
                .andExpect(jsonPath("$.data[0].count").value(2))
                .andExpect(jsonPath("$.data[0].view").value("kps"))
                .andExpect(jsonPath("$.data[1].view").value("item:3"))
                .andExpect(jsonPath("$.data[2].title", Matchers.startsWith("1 道题还是草稿")))
                .andExpect(jsonPath("$.data[3].title", Matchers.startsWith("1 份简答等你确认")))
                .andExpect(jsonPath("$.data[*].courseName", Matchers.everyItem(Matchers.is("软件测试"))));

        // 只列自己的课：课程换给别的老师后就看不到了
        jdbc.update("UPDATE course SET teacher_id = 3 WHERE id = 1");
        mockMvc.perform(get("/api/teacher/todo")).andExpect(jsonPath("$.data.length()").value(0));
    }
}
