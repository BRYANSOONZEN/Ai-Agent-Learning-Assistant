package com.se.platform.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.AiClient;
import com.se.platform.ai.client.FakeAiClient;
import com.se.platform.material.service.MaterialExtractor;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 资料 → 知识点。第 1 章种子数据：内容项 1 文档（只有老师写的文字）、2 练习、3 AI 对话。
 * 测试里加一个视频项，给文档传一个 PDF；转文字用假的处理服务，识别用假的 AI（都不联网、不花钱）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MaterialKpTest {

    /** 假的资料处理服务：PDF 给第 4 页 + 第 13 页的图，视频给两段；failNext 让下一次失败 */
    static class FakeExtractor implements MaterialExtractor {
        String failNext;

        @Override
        public Result extract(Path file, String kind) {
            if (failNext != null) {
                String m = failNext;
                failNext = null;
                throw new ExtractException(m);
            }
            if ("pdf".equals(kind)) {
                return new Result(List.of(
                        new Segment("P4", "pdf", 4, null, null, "边界值分析：在边界上、边界外侧、边界内侧各取值"),
                        new Segment("P13-图1", "image", 13, null, null, "流程图：不一致 → 失败根因分析")), "{}");
            }
            return new Result(List.of(
                    new Segment("01", "video", null, BigDecimal.ZERO, new BigDecimal("41.3"), "这一节讲边界值分吸"),
                    new Segment("02", "video", null, new BigDecimal("41.3"), new BigDecimal("90"), "错误常在边界附近")), "{}");
        }
    }

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        FakeAiClient fakeAiClient() {
            return new FakeAiClient();
        }

        @Bean
        @Primary
        FakeExtractor fakeExtractor() {
            return new FakeExtractor();
        }

        /** 不真的调 Office：把原文件复制一份当作转好的 PDF */
        @Bean
        @Primary
        com.se.platform.material.service.OfficeToPdf fakeOfficeToPdf() {
            return new com.se.platform.material.service.OfficeToPdf() {
                @Override
                public Path convert(Path source) {
                    try {
                        Path pdf = pdfPath(source);
                        java.nio.file.Files.copy(source, pdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return pdf;
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
        }
    }

    /** 模型的回复：2 个有出处的知识点（第 1 个写了 4 条要点，只留前 3 条）；还有一个编出来的出处 D9-P1（要去掉），一个没有任何有效出处的知识点（要去掉） */
    static final String KPS = """
            {"knowledge_points": [
              {"name": "边界测试值的选取", "summary": "边界上、外、内各取值", "importance": "重点",
               "key_points": [{"text": "边界外的值也要测", "sources": ["D1-P4", "D9-P1"]},
                              {"text": "错误常在边界附近", "sources": ["V1-02"]},
                              {"text": "边界上的值也要测", "sources": ["D1-P4"]},
                              {"text": "第 4 条要截掉", "sources": ["D1-P4"]}],
               "misconceptions": ["测中间值最有代表性"], "sources": ["D1-P4", "V1-01"]},
              {"name": "失败后的处理", "summary": "不一致时先做根因分析", "importance": "一般",
               "key_points": [{"text": "不一致要做失败根因分析", "sources": ["D1-P13-图1"]}],
               "misconceptions": [], "sources": ["D1-P13-图1"]},
              {"name": "编出来的知识点", "summary": "", "importance": "一般",
               "key_points": [{"text": "资料里没有", "sources": ["X1"]}], "misconceptions": [], "sources": []}
            ]}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiClient aiClient;
    @Autowired
    private FakeExtractor extractor;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private FakeAiClient fake;

    @BeforeEach
    void setUp() {
        fake = (FakeAiClient) aiClient;
        fake.reply(KPS);
    }

    private long addVideo() throws Exception {
        String body = mockMvc.perform(post("/api/chapter-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterId\": 1, \"type\": \"video\", \"title\": \"视频：边界值讲解\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("id").asLong();
    }

    private ResultActions upload(long itemId, String name) throws Exception {
        return mockMvc.perform(multipart("/api/chapter-items/" + itemId + "/file")
                .file(new MockMultipartFile("file", name, "application/octet-stream", new byte[] {1, 2, 3})));
    }

    private ResultActions status() throws Exception {
        return mockMvc.perform(get("/api/chapters/1/kp-status"));
    }

    @Test
    void uploadThenUploadDone_recognizesWithCheckedSources() throws Exception {
        // 识别前先纠正视频的同音错字：模型给两条，第 2 条课件里没有“那一节”，程序不用
        fake.reply("{\"fixes\": [{\"from\": \"边界值分吸\", \"to\": \"边界值分析\"}, {\"from\": \"这一节\", \"to\": \"那一节\"}]}")
                .then(KPS);
        long video = addVideo();
        upload(1, "课件.pdf").andExpect(jsonPath("$.code").value(200));
        upload(video, "讲解.mp4").andExpect(jsonPath("$.code").value(200));

        // 上传后马上转好了文字；没点“上传完毕”，不识别
        status().andExpect(jsonPath("$.data.status").value("none"))
                .andExpect(jsonPath("$.data.processed").value(2))
                .andExpect(jsonPath("$.data.materials[?(@.itemId == 1)].status").value(Matchers.contains("done")))
                .andExpect(jsonPath("$.data.kps.length()").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(0, fake.requests().size());

        // 点“上传完毕”：资料都好了，马上识别
        mockMvc.perform(post("/api/chapters/1/kp/upload-done"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.kps.length()").value(2))
                .andExpect(jsonPath("$.data.kps[0].name").value("边界测试值的选取"))
                // 编出来的出处 D9-P1 去掉了
                .andExpect(jsonPath("$.data.kps[0].keyPoints[0].sources", Matchers.contains("D1-P4")))
                // 每个知识点最多 3 条要点
                .andExpect(jsonPath("$.data.kps[0].keyPoints.length()").value(3))
                .andExpect(jsonPath("$.data.kps[1].sources", Matchers.contains("D1-P13-图1")));

        // 发给模型的资料带编号：老师写的文字 D1-T0、PDF 第 4 页 D1-P4、图 D1-P13-图1、视频 V1-01
        String fixPrompt = fake.requests().get(0).messages().get(0).content();
        org.junit.jupiter.api.Assertions.assertTrue(fixPrompt.contains("边界值分析：在边界上") && fixPrompt.contains("边界值分吸"));
        org.junit.jupiter.api.Assertions.assertEquals("这一节讲边界值分析", jdbc.queryForObject(
                "SELECT content FROM material_text WHERE seg_key = '01'", String.class));
        org.junit.jupiter.api.Assertions.assertEquals("这一节讲边界值分吸", jdbc.queryForObject(
                "SELECT original FROM material_text WHERE seg_key = '01'", String.class));
        String prompt = fake.requests().get(1).messages().get(0).content();
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("[D1-T0 "));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("[D1-P4 "));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("[D1-P13-图1 "));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("[V1-02 视频《视频：边界值讲解》00:41～01:30]"));

        // 知识点地图：两列资料；第 1 个知识点在文档第 4 页、视频 00:00 和 00:41
        JsonNode map = json.readTree(mockMvc.perform(get("/api/chapters/1/kp-map"))
                .andReturn().getResponse().getContentAsString()).path("data");
        org.junit.jupiter.api.Assertions.assertEquals(2, map.path("columns").size());
        JsonNode spots = map.path("rows").get(0).path("spots");
        org.junit.jupiter.api.Assertions.assertEquals(4, spots.path("1").get(0).path("page").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(41, spots.path(String.valueOf(video)).get(1).path("startSec").asInt());
    }

    @Test
    void pptIsConvertedToPdf_thenProcessedAsPdf_andStudentsSeeThePdf() throws Exception {
        upload(1, "课件.pptx").andExpect(jsonPath("$.code").value(200));
        // 按 PDF 处理：第 4 页、第 13 页的图都转出了文字
        status().andExpect(jsonPath("$.data.materials[?(@.itemId == 1)].status").value(Matchers.contains("done")));
        org.junit.jupiter.api.Assertions.assertEquals(2,
                jdbc.queryForObject("SELECT COUNT(*) FROM material_text WHERE item_id = 1", Integer.class));
        // 内容项带上转好的 PDF 地址，取这个地址返回 PDF
        String pdfUrl = json.readTree(mockMvc.perform(get("/api/chapters/1/items"))
                .andReturn().getResponse().getContentAsString()).path("data").get(0).path("material").path("pdfUrl").asText();
        org.junit.jupiter.api.Assertions.assertTrue(pdfUrl.endsWith("/file?pdf=true"));
        mockMvc.perform(get(pdfUrl)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().contentType("application/pdf"));
    }

    @Test
    void uploadDoneBeforeProcessing_failedMaterialDoesNotBlock() throws Exception {
        long video = addVideo();
        // 视频处理失败：原因写给老师看；文档仍然处理好
        extractor.failNext = "视频没有声音，取不出讲解内容";
        upload(video, "讲解.mp4");
        upload(1, "课件.pdf");
        status().andExpect(jsonPath("$.data.materials[?(@.itemId == " + video + ")].message")
                .value(Matchers.contains("视频没有声音，取不出讲解内容")));

        // 不支持的格式（单独一张图片）：说明要转成 PDF；PPT、Word 现在能转（见上一个测试）
        upload(1, "板书.png");
        status().andExpect(jsonPath("$.data.materials[?(@.itemId == 1)].message",
                Matchers.contains(Matchers.containsString("请转成 PDF"))));
    }

    @Test
    void teacherEditsKeptOnRerun_confirmSyncsAndStudentSeesOnlyConfirmed() throws Exception {
        long video = addVideo();
        upload(1, "课件.pdf");
        upload(video, "讲解.mp4");
        mockMvc.perform(post("/api/chapters/1/kp/upload-done"));
        JsonNode kps = json.readTree(status().andReturn().getResponse().getContentAsString()).path("data").path("kps");
        long first = kps.get(0).path("id").asLong();

        // 学生：还没确认，看不到
        mockMvc.perform(get("/api/learn/chapters/1/kp-map")).andExpect(jsonPath("$.data.rows.length()").value(0));

        // 老师改第 1 个、删第 2 个、加 1 个
        mockMvc.perform(put("/api/chapter-kps/" + first).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "边界测试值的选取（老师改）", "summary": "上、外、内", "importance": "重点",
                         "keyPoints": [{"text": "边界外的值", "sources": ["D1-P4"]}], "misconceptions": []}"""))
                .andExpect(jsonPath("$.data.edited").value(true));
        // 老师加到 4 条要点：不收
        mockMvc.perform(put("/api/chapter-kps/" + first).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "边界测试值的选取（老师改）", "importance": "重点", "misconceptions": [],
                         "keyPoints": [{"text": "一"}, {"text": "二"}, {"text": "三"}, {"text": "四"}]}"""))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("最多 3 条要点")));
        mockMvc.perform(delete("/api/chapter-kps/" + kps.get(1).path("id").asLong()));
        mockMvc.perform(post("/api/chapters/1/kps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"等价类划分\", \"keyPoints\": [], \"misconceptions\": []}"));

        // 重新识别：老师改过的（第 1 个、新加的）保留，AI 同名的不再重复；删掉的第 2 个又被识别出来
        mockMvc.perform(post("/api/chapters/1/kp/rerun"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.kps[*].name", Matchers.contains(
                        "边界测试值的选取（老师改）", "失败后的处理", "等价类划分")));

        // 确认：同步到 knowledge_point；学生看得到
        mockMvc.perform(post("/api/chapters/1/kp/confirm")).andExpect(jsonPath("$.data.status").value("confirmed"));
        Integer synced = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_point WHERE chapter_id = 1 AND name IN "
                + "('边界测试值的选取（老师改）', '失败后的处理', '等价类划分') AND deleted = 0", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(3, synced);
        mockMvc.perform(get("/api/learn/chapters/1/kp-map")).andExpect(jsonPath("$.data.rows.length()").value(3));

        // 又上传了新资料：提示老师传齐后再点上传完毕；学生仍然看到上次确认的
        upload(video, "讲解2.mp4");
        status().andExpect(jsonPath("$.data.status").value("none"))
                .andExpect(jsonPath("$.data.uploadDone").value(false))
                .andExpect(jsonPath("$.data.message", Matchers.containsString("资料有更新")));
        mockMvc.perform(get("/api/learn/chapters/1/kp-map")).andExpect(jsonPath("$.data.rows.length()").value(3));
    }

    @Test
    void badAiReply_failsWithReason() throws Exception {
        upload(1, "课件.pdf");
        fake.reply("这不是 JSON");
        mockMvc.perform(post("/api/chapters/1/kp/upload-done"))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message", Matchers.containsString("重新识别")));
        // 回复不是 JSON 会重试一次
        org.junit.jupiter.api.Assertions.assertEquals(2, fake.requests().size());
    }
}
