package com.se.platform.record;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.se.platform.common.DemoIdentity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 截止时间。种子数据：练习（内容项 2）2 天后截止，AI 对话（内容项 3）昨天已截止、允许补做。
 * 10 个学生（5～14 号）：练习 5～11 号做过，对话 7～11 号做完过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeadlineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** 按顺序解锁：做练习之前要先学完文档（内容项 1），做对话之前要先做完练习（内容项 2） */
    private void finished(long studentId, long itemId) {
        jdbc.update("INSERT INTO learning_event (student_id, event_type, target_id, course_id, created_at) "
                + "VALUES (?, 'finish_item', ?, 1, NOW())", studentId, itemId);
    }

    @Test
    void studentSeesOnlyWhatIsNotDone() throws Exception {
        // 学生01（5 号）做过练习，没做对话：只提醒对话，已过期、可以补做
        mockMvc.perform(get("/api/learn/deadlines"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itemId").value(3))
                .andExpect(jsonPath("$.data[0].state").value("overdue"))
                .andExpect(jsonPath("$.data[0].allowLate").value(true))
                .andExpect(jsonPath("$.data[0].minutesLeft", Matchers.lessThan(0)));

        // 12 号两样都没做：练习快到期（2 天后，提醒窗口 3 天），对话已过期，按截止时间排
        mockMvc.perform(get("/api/learn/deadlines").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].itemId").value(3))
                .andExpect(jsonPath("$.data[1].itemId").value(2))
                .andExpect(jsonPath("$.data[1].state").value("soon"));
    }

    @Test
    void teacherSeesWhoHasNotDoneIt() throws Exception {
        mockMvc.perform(get("/api/teacher/deadlines"))
                .andExpect(jsonPath("$.data.length()").value(2))
                // 已过期的排前面：对话 10 人里 5 人做完，没做的是 5、6、12、13、14 号
                .andExpect(jsonPath("$.data[0].itemId").value(3))
                .andExpect(jsonPath("$.data[0].state").value("overdue"))
                .andExpect(jsonPath("$.data[0].total").value(10))
                .andExpect(jsonPath("$.data[0].done").value(5))
                .andExpect(jsonPath("$.data[0].late").value(0))
                .andExpect(jsonPath("$.data[0].missing[*].id", Matchers.contains(5, 6, 12, 13, 14)))
                // 练习：7 人做过，3 人没做
                .andExpect(jsonPath("$.data[1].itemId").value(2))
                .andExpect(jsonPath("$.data[1].done").value(7))
                .andExpect(jsonPath("$.data[1].missing.length()").value(3));
    }

    @Test
    void lateSubmissionIsCountedOrBlocked() throws Exception {
        finished(12, 1);
        finished(13, 1);
        String past = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 练习改成 1 小时前截止、允许补做：12 号补交，老师那边记 1 人迟交
        setDeadline(2, "练习：边界值小测", "exercise", past, true);
        mockMvc.perform(post("/api/learn/items/2/submit").header(DemoIdentity.HEADER, "12")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": {\"1\": \"A\", \"2\": \"B\"}}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/teacher/deadlines"))
                .andExpect(jsonPath("$.data[?(@.itemId == 2)].late").value(Matchers.contains(1)))
                .andExpect(jsonPath("$.data[?(@.itemId == 2)].done").value(Matchers.contains(8)));

        // 改成不允许补做：13 号交卷被拒；12 号（练习已交，对话已解锁）开始对话也被拒
        setDeadline(2, "练习：边界值小测", "exercise", past, false);
        mockMvc.perform(post("/api/learn/items/2/submit").header(DemoIdentity.HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": {\"1\": \"A\", \"2\": \"B\"}}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("截止时间")));
        setDeadline(3, "AI 对话：订单数量怎么测", "dialogue", past, false);
        mockMvc.perform(post("/api/dialogue/sessions").header(DemoIdentity.HEADER, "12")
                        .param("chapterId", "1").param("itemId", "3"))
                .andExpect(jsonPath("$.code").value(400));

        // 去掉截止时间：又能做了，也不再出现在提醒里
        setDeadline(3, "AI 对话：订单数量怎么测", "dialogue", null, true);
        mockMvc.perform(post("/api/dialogue/sessions").header(DemoIdentity.HEADER, "12")
                        .param("chapterId", "1").param("itemId", "3"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/learn/deadlines").header(DemoIdentity.HEADER, "13"))
                .andExpect(jsonPath("$.data[*].itemId", Matchers.contains(2)));
    }

    @Test
    void onlyExerciseAndDialogueCanHaveDeadline() throws Exception {
        // 阅读（内容项 1）不能设截止时间
        mockMvc.perform(put("/api/chapter-items/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "阅读", "dueAt": "2026-12-01 23:59:00"}"""))
                .andExpect(jsonPath("$.code").value(400));
        // 练习可以；秒数抹掉，返回给前端
        mockMvc.perform(put("/api/chapter-items/2").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "练习", "dueAt": "2026-12-01 23:59:30", "allowLate": false}"""))
                .andExpect(jsonPath("$.data.dueAt").value("2026-12-01 23:59:00"))
                .andExpect(jsonPath("$.data.allowLate").value(false));
    }

    private void setDeadline(long itemId, String title, String type, String dueAt, boolean allowLate)
            throws Exception {
        String due = dueAt == null ? "null" : "\"" + dueAt + "\"";
        mockMvc.perform(put("/api/chapter-items/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"%s\", \"dueAt\": %s, \"allowLate\": %s}".formatted(title, due, allowLate)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.type").value(type));
    }
}
