package com.se.platform.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 老师建课：课程 → 章节 → 内容项（视频、文档、练习、AI 对话）、知识点、题目，走一遍真实接口（H2 内存库） */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CourseContentControllerTest {

    /** 上传的文件放到 JUnit 的临时目录，测试结束自动删除，不污染 backend/uploads */
    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    private long postJson(String url, String body) throws Exception {
        String json = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    @Test
    void teacherBuildsChapterEndToEnd() throws Exception {
        long courseId = postJson("/api/courses", """
                {"name": "测试课程", "description": "说明", "status": "draft"}""");
        long chapterId = postJson("/api/chapters", """
                {"courseId": %d, "title": "第 1 章 新章"}""".formatted(courseId));

        mockMvc.perform(get("/api/courses/" + courseId + "/chapters"))
                .andExpect(jsonPath("$.data[0].title").value("第 1 章 新章"))
                .andExpect(jsonPath("$.data[0].status").value("draft"));

        // 一章可以有两个视频：建两个视频项，各传一个文件
        long video1 = postJson("/api/chapter-items", """
                {"chapterId": %d, "type": "video", "title": "视频一"}""".formatted(chapterId));
        long video2 = postJson("/api/chapter-items", """
                {"chapterId": %d, "type": "video", "title": "视频二"}""".formatted(chapterId));
        MockMultipartFile mp4 = new MockMultipartFile("file", "lesson.mp4", "video/mp4", new byte[] {1, 2, 3});
        String uploaded = mockMvc.perform(multipart("/api/chapter-items/" + video1 + "/file").file(mp4))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.material.kind").value("video"))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(uploaded, "$.data.material.url");
        mockMvc.perform(get(url))
                .andExpect(content().contentType("video/mp4"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
        // 视频项不收文档
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1});
        mockMvc.perform(multipart("/api/chapter-items/" + video2 + "/file").file(pdf))
                .andExpect(jsonPath("$.code").value(400));

        // 文档项可以不传文件，直接写正文
        long doc = postJson("/api/chapter-items", """
                {"chapterId": %d, "type": "document", "title": "阅读", "description": "正文"}""".formatted(chapterId));
        long quiz = postJson("/api/chapter-items", """
                {"chapterId": %d, "type": "exercise", "title": "小测"}""".formatted(chapterId));

        // 调顺序：练习放到最前
        mockMvc.perform(put("/api/chapters/" + chapterId + "/items/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [%d, %d, %d, %d]}".formatted(quiz, video1, video2, doc)))
                .andExpect(jsonPath("$.data[0].id").value(quiz))
                .andExpect(jsonPath("$.data[2].ready").value(false))
                .andExpect(jsonPath("$.data[3].ready").value(true));

        long kpId = postJson("/api/knowledge-points", """
                {"chapterId": %d, "name": "知识点 A", "isKey": true}""".formatted(chapterId));
        postJson("/api/exercises", """
                {"chapterId": %d, "itemId": %d, "stem": "题干", "options": ["甲", "乙", "丙"], "answer": "b",
                 "optionNotes": ["误解一", "不该保存", "误解三"], "knowledgePointIds": [%d]}"""
                .formatted(chapterId, quiz, kpId));
        // 题目只能放进练习项
        mockMvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content("""
                        {"chapterId": %d, "itemId": %d, "stem": "题干", "options": ["甲", "乙"], "answer": "A",
                         "knowledgePointIds": [%d]}""".formatted(chapterId, doc, kpId)))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/chapters/" + chapterId + "/exercises").param("itemId", String.valueOf(quiz)))
                .andExpect(jsonPath("$.data[0].answer").value("B"))
                .andExpect(jsonPath("$.data[0].itemId").value(quiz))
                .andExpect(jsonPath("$.data[0].optionNotes[1]").value(""))
                .andExpect(jsonPath("$.data[0].knowledgePoints[0].name").value("知识点 A"));

        // 有题目关联的知识点不能删；有内容的章节不能删
        mockMvc.perform(delete("/api/knowledge-points/" + kpId)).andExpect(jsonPath("$.code").value(409));
        mockMvc.perform(delete("/api/chapters/" + chapterId)).andExpect(jsonPath("$.code").value(409));

        // 删掉练习项，里面的题目一起删掉
        mockMvc.perform(delete("/api/chapter-items/" + quiz)).andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/chapters/" + chapterId))
                .andExpect(jsonPath("$.data.itemCount").value(3))
                .andExpect(jsonPath("$.data.exerciseCount").value(0));
    }

    @Test
    void rejectsBadExerciseAndFileType() throws Exception {
        mockMvc.perform(post("/api/exercises").contentType(MediaType.APPLICATION_JSON).content("""
                        {"chapterId": 1, "itemId": 2, "stem": "题干", "options": ["甲", "乙"], "answer": "D",
                         "knowledgePointIds": [1]}"""))
                .andExpect(jsonPath("$.code").value(400));

        // 种子数据里 1 号内容项是第 1 章的文档项
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1});
        mockMvc.perform(multipart("/api/chapter-items/1/file").file(exe))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 当前老师是 2 号（李老师）：只看到自己的课；3 号「需求工程」是王老师的，不能进、不能改 */
    @Test
    void teacherOnlySeesAndEditsOwnCourses() throws Exception {
        mockMvc.perform(get("/api/courses/mine"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].teacherId",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(2))));

        mockMvc.perform(get("/api/courses/mine/3")).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(put("/api/courses/3").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "改别人的课"}"""))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/api/chapters").contentType(MediaType.APPLICATION_JSON).content("""
                        {"courseId": 3, "title": "加到别人的课里"}"""))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 建课时选模块：只开文档 + 题目（自动带上知识点），加视频项会被拒；空列表、没有的模块也会被拒 */
    @Test
    void courseOnlyAcceptsChosenModules() throws Exception {
        String created = mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "只有文档的课", "modules": ["exercise", "document"]}"""))
                .andExpect(jsonPath("$.data.modules[0]").value("document"))
                .andExpect(jsonPath("$.data.modules[1]").value("knowledge"))
                .andExpect(jsonPath("$.data.modules[2]").value("exercise"))
                .andReturn().getResponse().getContentAsString();
        long courseId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        long chapterId = postJson("/api/chapters", """
                {"courseId": %d, "title": "第 1 章"}""".formatted(courseId));

        mockMvc.perform(post("/api/chapter-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"chapterId": %d, "type": "video", "title": "视频"}""".formatted(chapterId)))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/chapter-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"chapterId": %d, "type": "dialogue", "title": "对话"}""".formatted(chapterId)))
                .andExpect(jsonPath("$.code").value(400));
        postJson("/api/chapter-items", """
                {"chapterId": %d, "type": "document", "title": "课件"}""".formatted(chapterId));

        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "空", "modules": []}"""))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "错", "modules": ["game"]}"""))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 一章可以有几个 AI 对话项，学生的对话只取这一项里的话题（开始对话只说开场白，不调模型） */
    @Test
    void dialogueUsesTopicsOfOneItem() throws Exception {
        mockMvc.perform(get("/api/dialogue/chapters/1").param("itemId", "3"))
                .andExpect(jsonPath("$.data.topicTitles.length()").value(2));

        long empty = postJson("/api/chapter-items", """
                {"chapterId": 1, "type": "dialogue", "title": "AI 对话：还没写话题"}""");
        mockMvc.perform(get("/api/dialogue/chapters/1").param("itemId", String.valueOf(empty)))
                .andExpect(jsonPath("$.data.topicTitles.length()").value(0));
        mockMvc.perform(post("/api/dialogue/sessions").param("chapterId", "1").param("itemId", String.valueOf(empty)))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/dialogue/sessions").param("chapterId", "1").param("itemId", "3"))
                .andExpect(jsonPath("$.data.topics.length()").value(2))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void seededChapterHasContent() throws Exception {
        mockMvc.perform(get("/api/chapters/1"))
                .andExpect(jsonPath("$.data.itemCount").value(3))
                .andExpect(jsonPath("$.data.knowledgePointCount").value(2))
                .andExpect(jsonPath("$.data.exerciseCount").value(6)) // 练习里 2 道 + 题库 4 道
                .andExpect(jsonPath("$.data.topicCount").value(2));
        mockMvc.perform(get("/api/chapters/1/items"))
                .andExpect(jsonPath("$.data[0].type").value("document"))
                .andExpect(jsonPath("$.data[0].ready").value(true))
                .andExpect(jsonPath("$.data[1].exerciseCount").value(2))
                .andExpect(jsonPath("$.data[2].topicCount").value(2));
    }
}
