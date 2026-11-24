package com.se.platform.record;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.se.platform.common.DemoIdentity;
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
 * 视频、文档怎么算学完，以及按顺序解锁。
 * 种子数据第 1 章：内容项 1 文档 → 2 练习 → 3 AI 对话。7 号学生三项都做过；12、13 号什么都没做。
 * 视频项在测试里加在对话后面（第 4 项）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProgressTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long addVideo() {
        jdbc.update("INSERT INTO chapter_item (course_id, chapter_id, type, title, description, sort_order, "
                + "created_at, updated_at) VALUES (1, 1, 'video', '视频：边界值讲解', '', 3, NOW(), NOW())");
        return jdbc.queryForObject("SELECT MAX(id) FROM chapter_item", Long.class);
    }

    /** 测试里不能真的等：把上一次上报的时间往前拨，相当于又过了 seconds 秒 */
    private void waitSeconds(long student, long itemId, int seconds) {
        jdbc.update("UPDATE item_progress SET updated_at = TIMESTAMPADD(SECOND, ?, updated_at) "
                + "WHERE student_id = ? AND item_id = ?", -seconds, student, itemId);
    }

    private ResultActions video(long student, long itemId, String body) throws Exception {
        return mockMvc.perform(post("/api/learn/items/" + itemId + "/video-progress")
                .header(DemoIdentity.HEADER, String.valueOf(student))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reading(long student, int seconds) throws Exception {
        return mockMvc.perform(post("/api/learn/items/1/reading").header(DemoIdentity.HEADER, String.valueOf(student))
                .contentType(MediaType.APPLICATION_JSON).content("{\"seconds\": " + seconds + "}"));
    }

    private ResultActions course(long student) throws Exception {
        return mockMvc.perform(get("/api/learn/courses/1").header(DemoIdentity.HEADER, String.valueOf(student)));
    }

    @Test
    void video_mergedRanges_ninetyPercentUnlocks() throws Exception {
        long v = addVideo();

        // 12 号连对话都没做：视频还锁着
        video(12, v, "{\"duration\": 20, \"ranges\": [[0, 8]]}")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("请先完成上一项")));

        // 7 号：20 秒的视频，看了 0～8 秒：P = 8 / 20 = 40%
        video(7, v, "{\"duration\": 20, \"ranges\": [[0, 8]], \"position\": 8}")
                .andExpect(jsonPath("$.data.watchedSec").value(8.0))
                .andExpect(jsonPath("$.data.progress").value(0.4))
                .andExpect(jsonPath("$.data.completed").value(false));

        // 拖到 14 秒看到结尾：跳过的 8～14 秒不算，P = (8 + 6) / 20 = 70%
        waitSeconds(7, v, 60);
        video(7, v, "{\"duration\": 20, \"ranges\": [[14, 20]], \"position\": 20}")
                .andExpect(jsonPath("$.data.watchedSec").value(14.0))
                .andExpect(jsonPath("$.data.ranges.length()").value(2))
                .andExpect(jsonPath("$.data.completed").value(false));

        // 回去重看 3～6 秒：重复的不加，还是 70%
        waitSeconds(7, v, 60);
        video(7, v, "{\"duration\": 20, \"ranges\": [[3, 6]]}")
                .andExpect(jsonPath("$.data.progress").value(0.7));

        // 补看 8～12 秒：P = 18 / 20 = 90%，学完
        waitSeconds(7, v, 60);
        video(7, v, "{\"duration\": 20, \"ranges\": [[8, 12]]}")
                .andExpect(jsonPath("$.data.watchedSec").value(18.0))
                .andExpect(jsonPath("$.data.progress").value(0.9))
                .andExpect(jsonPath("$.data.completed").value(true));

        // 学习页：视频打勾，进度和上次的位置都带回来
        course(7).andExpect(jsonPath("$.data.doneItemIds", Matchers.hasItem((int) v)))
                .andExpect(jsonPath("$.data.progress." + v + ".lastPosition").value(20.0));
        Integer events = jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE student_id = 7 "
                + "AND event_type = 'finish_item' AND target_id = ?", Integer.class, v);
        org.junit.jupiter.api.Assertions.assertEquals(1, events);
    }

    @Test
    void video_reportFasterThanRealTime_isIgnored() throws Exception {
        long v = addVideo();
        video(7, v, "{\"duration\": 20, \"ranges\": [[0, 8]]}").andExpect(jsonPath("$.data.watchedSec").value(8.0));

        // 马上又说看完了 0～20 秒：两次上报之间没过几秒，新增 12 秒不可信，不算
        video(7, v, "{\"duration\": 20, \"ranges\": [[0, 20]]}")
                .andExpect(jsonPath("$.data.watchedSec").value(8.0))
                .andExpect(jsonPath("$.data.completed").value(false));

        // 总时长第一次定下以后不能改小
        waitSeconds(7, v, 60);
        video(7, v, "{\"duration\": 2, \"ranges\": []}")
                .andExpect(jsonPath("$.data.durationSec").value(20.0))
                .andExpect(jsonPath("$.data.progress").value(0.4));

        // 时长不对的拒绝
        video(7, v, "{\"ranges\": [[0, 1]]}").andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void document_thirtySecondsThenButton() throws Exception {
        // 12 号：文档是第 1 项，直接能读；练习、对话锁着
        course(12).andExpect(jsonPath("$.data.lockedItemIds", Matchers.contains(2, 3)));

        // 不能直接报"学完"，也不能一打开就点"我已读完"
        mockMvc.perform(post("/api/learn/events").header(DemoIdentity.HEADER, "12")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\": \"finish_item\", \"itemId\": 1}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/learn/items/1/finish-reading").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("还需 30 秒")));

        // 读了 10 秒；马上又报 10 秒：中间没过多久，只加 3 秒
        reading(12, 10).andExpect(jsonPath("$.data.readSec").value(10));
        reading(12, 10).andExpect(jsonPath("$.data.readSec").value(13));
        mockMvc.perform(post("/api/learn/items/1/finish-reading").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.message", Matchers.containsString("还需 17 秒")));

        // 又读了 20 秒：33 秒 ≥ 30，点"我已读完"，学完，练习解锁
        waitSeconds(12, 1, 20);
        reading(12, 20).andExpect(jsonPath("$.data.readSec").value(33));
        mockMvc.perform(post("/api/learn/items/1/finish-reading").header(DemoIdentity.HEADER, "12"))
                .andExpect(jsonPath("$.data.completed").value(true));
        course(12).andExpect(jsonPath("$.data.doneItemIds", Matchers.contains(1)))
                .andExpect(jsonPath("$.data.lockedItemIds", Matchers.contains(3)));
    }

    @Test
    void lockedItemsCannotBeDone() throws Exception {
        // 13 号没读文档：练习的题目、交卷、AI 对话都被拦，提示先完成哪一项
        mockMvc.perform(get("/api/learn/items/2/questions").header(DemoIdentity.HEADER, "13"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("阅读：边界值分析要点")));
        mockMvc.perform(post("/api/learn/items/2/submit").header(DemoIdentity.HEADER, "13")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\": {\"1\": \"A\"}}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/dialogue/sessions").header(DemoIdentity.HEADER, "13")
                        .param("chapterId", "1").param("itemId", "3"))
                .andExpect(jsonPath("$.code").value(400));

        // 7 号以前就做过练习和对话（老数据）：不会被锁回去
        course(7).andExpect(jsonPath("$.data.lockedItemIds.length()").value(0));
    }
}
