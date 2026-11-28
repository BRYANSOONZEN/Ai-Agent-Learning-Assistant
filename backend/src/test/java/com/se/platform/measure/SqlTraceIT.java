package com.se.platform.measure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.se.platform.ai.client.FakeAiClient;
import com.se.platform.common.DemoIdentity;
import com.se.platform.material.service.MaterialExtractor;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 记录每个功能真实执行了哪些 SQL（给《数据库说明》用）。不进日常测试和 CI（类名 IT），手动运行：
 * <pre>mvnw test -Dtest=SqlTraceIT -Dsurefire.failIfNoSpecifiedTests=false</pre>
 * 做法：按真实的使用顺序把 35 个功能走一遍（内存库 + 演示数据；大模型、资料处理服务用假的，只为了让流程走通，
 * 不影响 SQL），每个请求前后截下 MyBatis 打印的 "==>  Preparing:" 行，写到 target/sql-trace.json，
 * 再由 tools/docs/gen_db_calls.py 生成文档。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"platform.class-analysis.min-submit-ratio=0.6", "app.upload-dir=target/sql-trace-uploads"})
class SqlTraceIT {

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        FakeAiClient fakeAiClient() {
            return new FakeAiClient();
        }

        @Bean
        @Primary
        MaterialExtractor fakeExtractor() {
            return (file, kind) -> new MaterialExtractor.Result(List.of(
                    new MaterialExtractor.Segment("P1", "pdf", 1, null, null, "边界值分析：在边界上、边界外侧、边界内侧各取值"),
                    new MaterialExtractor.Segment("P2", "pdf", 2, null, null, "错误常发生在输入范围的边界附近")), "{}");
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FakeAiClient fake;

    private final ObjectMapper json = new ObjectMapper();
    private final List<Map<String, Object>> flows = new ArrayList<>();
    private static final Pattern SQL = Pattern.compile("==>\\s+Preparing:\\s*(.*)");

    /** 走一个功能，记下这期间执行的 SQL；返回响应体 */
    private String trace(String group, String title, String actor, String method, String path,
                         MockHttpServletRequestBuilder req) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(new TeeStream(original, buf), true, StandardCharsets.UTF_8));
        String body;
        try {
            body = mvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } finally {
            System.out.flush();
            System.setOut(original);
        }
        List<String> sqls = new ArrayList<>();
        Matcher m = SQL.matcher(buf.toString(StandardCharsets.UTF_8));
        while (m.find()) {
            sqls.add(m.group(1).trim());
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("group", group);
        f.put("title", title);
        f.put("actor", actor);
        f.put("method", method);
        f.put("path", path);
        f.put("code", JsonPath.read(body, "$.code"));
        f.put("sql", sqls);
        flows.add(f);
        return body;
    }

    private static MockHttpServletRequestBuilder as(String student, MockHttpServletRequestBuilder r) {
        return student == null ? r : r.header(DemoIdentity.HEADER, student);
    }

    private static MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder r, String body) {
        return r.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void traceAllFlows() throws Exception {
        String demo = demoPassword();
        String T = "老师（李老师）";
        String S = "学生（学生08）";

        // ---------------- 账号 ----------------
        trace("账号", "登录", "任何人", "POST", "/api/auth/login",
                jsonBody(post("/api/auth/login"), "{\"username\": \"2023008\", \"password\": \"" + demo + "\"}"));
        trace("账号", "演示账号一键登录的列表", "任何人", "GET", "/api/auth/demo-accounts", get("/api/auth/demo-accounts"));

        // ---------------- 老师建课 ----------------
        trace("老师建课", "我的课程列表", T, "GET", "/api/courses/mine", get("/api/courses/mine"));
        trace("老师建课", "打开建课页：课程大纲", T, "GET", "/api/courses/{id}/chapters", get("/api/courses/2/chapters"));
        String ch = trace("老师建课", "新建章节", T, "POST", "/api/chapters",
                jsonBody(post("/api/chapters"), "{\"courseId\": 2, \"title\": \"边界值分析\"}"));
        long chapterId = ((Number) JsonPath.read(ch, "$.data.id")).longValue();
        String it = trace("老师建课", "新建内容项（文档）", T, "POST", "/api/chapter-items",
                jsonBody(post("/api/chapter-items"), "{\"chapterId\": " + chapterId + ", \"type\": \"document\", \"title\": \"课件\"}"));
        long itemId = ((Number) JsonPath.read(it, "$.data.id")).longValue();
        trace("① 上传资料", "上传课件（马上排队转文字；测试里同步处理完）", T, "POST", "/api/chapter-items/{id}/file",
                multipart("/api/chapter-items/" + itemId + "/file")
                        .file(new MockMultipartFile("file", "课件.pdf", "application/pdf", new byte[] {1, 2, 3})));
        trace("① 上传资料", "查看转文字进度", T, "GET", "/api/chapters/{id}/kp-status", get("/api/chapters/" + chapterId + "/kp-status"));

        fake.reply("""
                {"knowledge_points": [{"name": "边界测试值的选取", "summary": "边界上、外、内各取值", "importance": "重点",
                  "key_points": [{"text": "边界外的值也要测", "sources": ["D1-P1"]}, {"text": "错误常在边界附近", "sources": ["D1-P2"]}],
                  "misconceptions": ["测中间值最有代表性"], "sources": ["D1-P1", "D1-P2"]}]}""");
        trace("② 识别知识点", "老师点“上传完毕”→ 识别知识点", T, "POST", "/api/chapters/{id}/kp/upload-done",
                post("/api/chapters/" + chapterId + "/kp/upload-done"));
        trace("② 识别知识点", "老师确认知识点", T, "POST", "/api/chapters/{id}/kp/confirm", post("/api/chapters/" + chapterId + "/kp/confirm"));
        trace("② 识别知识点", "知识点地图（老师）", T, "GET", "/api/chapters/{id}/kp-map", get("/api/chapters/" + chapterId + "/kp-map"));

        // 第 1 章（演示数据）：给 AI 对话、出题准备一个已确认的知识点（不记录）
        jdbc.update("""
                INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, misconceptions,
                  sources, edited, knowledge_point_id, created_at, updated_at)
                VALUES (1, 0, '边界测试值的选取', '边界上、外、内各取值', '重点',
                  '[{"text":"边界外的值也要测，比如 0","sources":[]},{"text":"错误常发生在边界附近","sources":[]}]',
                  '["测中间值最有代表性"]', '[]', 0, 1, NOW(), NOW())""");
        long kpId = jdbc.queryForObject("SELECT MAX(id) FROM chapter_kp", Long.class);
        jdbc.update("DELETE FROM chapter_kp_state WHERE chapter_id = 1");
        jdbc.update("INSERT INTO chapter_kp_state (chapter_id, upload_done, status, message, confirmed_at, created_at, updated_at) "
                + "VALUES (1, 1, 'confirmed', '已确认', NOW(), NOW(), NOW())");

        fake.reply("""
                {"scene": {"type": "同学请教", "name": "阿哲", "setting": "阿哲要测网店的购买数量", "persona": "实习生", "opening": "每单买 1～99 件，我该测哪些数？"},
                 "misconception_probe": {"misconception": "测中间值最有代表性", "line": "那我只测 50 不就行了？"},
                 "key_points": [
                   {"no": 1, "understood_if": "说出要测边界外的 0", "partial_if": "只说测边界", "L1": "还有吗？", "L2": "有人下单 0 件呢？", "L3": "比 1 小的数要不要测？", "pointer": "课件第 4 页"},
                   {"no": 2, "understood_if": "说出错误常在边界附近", "partial_if": "只说边界重要", "L1": "为什么挑这些？", "L2": "程序员把 ≥1 写成 >1，测 50 能发现吗？", "L3": "错误一般出在哪里？", "pointer": "课件第 5 页"}],
                 "recap": "复述"}""");
        trace("③ AI 对话（老师）", "生成 AI 对话草稿", T, "POST", "/api/chapter-items/{id}/feynman/draft",
                jsonBody(post("/api/chapter-items/3/feynman/draft"), "{\"kpIds\": [" + kpId + "]}"));
        trace("③ AI 对话（老师）", "确认草稿", T, "POST", "/api/chapter-items/{id}/feynman/confirm", post("/api/chapter-items/3/feynman/confirm"));

        fake.reply("""
                {"questions": [
                  {"type": "single_choice", "assess_type": "scenario", "key_point_no": 1, "stem": "网店每单 1～99 件，哪组测试值最好？",
                   "options": ["0、1、99、100", "50", "1、99", "10、20"], "answer": "A",
                   "option_notes": ["", "测中间值最有代表性", "只测边界上，漏了边界外", "随便取值"], "analysis": "边界上和边界外都要测", "difficulty": 2}]}""")
                .then("{\"answers\": [\"A\"]}");
        trace("④ 出题", "AI 出题（存成草稿）", T, "POST", "/api/exercises/generate",
                jsonBody(post("/api/exercises/generate"), "{\"itemId\": 2, \"kpIds\": [" + kpId + "], \"assessTypes\": [\"scenario\"],"
                        + " \"types\": [\"single_choice\"], \"perKp\": 1}"));
        trace("④ 出题", "练习的题目列表（老师）", T, "GET", "/api/chapters/{id}/exercises", get("/api/chapters/1/exercises").param("itemId", "2"));
        trace("④ 出题", "覆盖检查", T, "GET", "/api/chapters/{id}/exercise-coverage", get("/api/chapters/1/exercise-coverage"));

        // ---------------- 学生学习 ----------------
        jdbc.update("DELETE FROM enrollment WHERE student_id = 12 AND course_id = 1");
        trace("学生学习", "课程列表", S, "GET", "/api/learn/courses", as("12", get("/api/learn/courses")));
        trace("学生学习", "加入课程（选课）", S, "POST", "/api/learn/courses/{id}/join", as("12", post("/api/learn/courses/1/join")));
        trace("学生学习", "打开学习页（大纲、学完了哪些、哪些锁着、进度）", S, "GET", "/api/learn/courses/{id}", as("12", get("/api/learn/courses/1")));
        trace("学生学习", "知识点地图（学生，带自己的颜色）", S, "GET", "/api/learn/chapters/{id}/kp-map", as("12", get("/api/learn/chapters/1/kp-map")));
        trace("学生学习", "读文档：上报阅读秒数", S, "POST", "/api/learn/items/{id}/reading",
                as("12", jsonBody(post("/api/learn/items/1/reading"), "{\"seconds\": 10}")));
        jdbc.update("UPDATE item_progress SET read_sec = 40, updated_at = TIMESTAMPADD(SECOND, -60, NOW()) WHERE student_id = 12 AND item_id = 1");
        trace("学生学习", "读文档：点“我已读完”", S, "POST", "/api/learn/items/{id}/finish-reading", as("12", post("/api/learn/items/1/finish-reading")));

        String start = trace("③ AI 对话（学生）", "开始对话", S, "POST", "/api/learn/items/{id}/feynman/start",
                as("12", post("/api/learn/items/3/feynman/start")));
        long sid = ((Number) JsonPath.read(start, "$.data.id")).longValue();
        fake.reply("{\"kind\":\"explain\",\"attempt\":true,\"points\":[{\"no\":1,\"status\":\"clear\",\"quote\":\"要测0\"},"
                + "{\"no\":2,\"status\":\"none\",\"quote\":\"\"}],\"misconception_fixed\":false,\"ack\":\"原来如此\",\"followup\":\"\"}");
        trace("③ AI 对话（学生）", "说一句（大模型判断 + 程序决定下一步）", S, "POST", "/api/learn/feynman/{sessionId}/reply",
                as("12", jsonBody(post("/api/learn/feynman/" + sid + "/reply"), json.writeValueAsString(Map.of("text", "我觉得要测0和1")))));
        jdbc.update("INSERT INTO learning_event (student_id, event_type, target_id, course_id, created_at) VALUES (12, 'finish_item', 3, 1, NOW())");

        trace("⑥ 做练习", "取题（不带答案）", S, "GET", "/api/learn/items/{id}/questions", as("12", get("/api/learn/items/2/questions")));
        trace("⑥ 做练习", "交卷（判分、存作答、重算掌握度）", S, "POST", "/api/learn/items/{id}/submit",
                as("12", jsonBody(post("/api/learn/items/2/submit"), "{\"answers\": {\"1\": \"C\", \"2\": \"B\"}}")));
        trace("⑥ 做练习", "学情诊断（学生）", S, "GET", "/api/diagnosis/me", as("12", get("/api/diagnosis/me")));
        trace("⑧ 学生画像", "课程画像（每章画像 + 四维）", S, "GET", "/api/profiles/me/courses/{id}", as("12", get("/api/profiles/me/courses/1")));
        trace("学生学习", "学习记录", S, "GET", "/api/learn/records", as("12", get("/api/learn/records")));
        trace("学生学习", "快到期的任务（首页、消息铃铛）", S, "GET", "/api/learn/deadlines", as("12", get("/api/learn/deadlines")));

        // ---------------- 小助手 ----------------
        trace("⑦ 小助手", "学习情况（每个知识点的结论和下一步）", "学生（学生01）", "GET", "/api/assistant/summary", as("5", get("/api/assistant/summary")));
        fake.reply("""
                {"questions": [
                  {"type": "single_choice", "assess_type": "scenario", "key_point_no": 1, "stem": "停车 1～24 小时，测上限选哪组？",
                   "options": ["23、24、25", "1、12、24", "24、48", "22、23、24"], "answer": "A",
                   "option_notes": ["", "混在一起", "太远", "只取了边界和内侧，缺少越过边界的值"], "analysis": "上边界内外都要测"},
                  {"type": "judge", "assess_type": "concept", "key_point_no": 1, "stem": "边界外的值也要测。",
                   "options": ["正确", "错误"], "answer": "A", "option_notes": ["", "以为只测边界内"], "analysis": "要测"}]}""")
                .then("{\"answers\": [\"A\"]}");
        trace("⑦ 小助手", "开始补练（按自己的错题当场出题）", "学生（学生01）", "POST", "/api/assistant/remedy/{kpId}/start",
                as("5", post("/api/assistant/remedy/1/start")));
        fake.reply("边界外的值也要测，因为错误常发生在边界附近。");
        trace("⑦ 小助手", "聊天问一句", S, "POST", "/api/assistant/chat",
                as("12", jsonBody(post("/api/assistant/chat"), "{\"chapterId\": 1, \"message\": \"边界值要怎么取？\"}")));

        // ---------------- 老师看学情、干预 ----------------
        jdbc.update("UPDATE chapter_item SET due_at = TIMESTAMPADD(HOUR, -1, NOW()) WHERE id = 2");
        trace("⑤ 老师干预", "全班分析（练习截止后）", T, "GET", "/api/class-analysis", get("/api/class-analysis").param("courseId", "1"));
        trace("⑤ 老师干预", "侧边栏待处理数", T, "GET", "/api/class-analysis/open-count", get("/api/class-analysis/open-count"));
        trace("老师学情", "截止情况（谁没做、谁迟交）", T, "GET", "/api/teacher/deadlines", get("/api/teacher/deadlines"));
        trace("老师学情", "首页“待你确认”", T, "GET", "/api/teacher/todo", get("/api/teacher/todo"));
        trace("老师学情", "全班诊断", T, "GET", "/api/diagnosis/courses/{id}", get("/api/diagnosis/courses/1"));
        trace("⑥ 做练习", "简答待确认列表（老师）", T, "GET", "/api/exercises/short-answers", get("/api/exercises/short-answers").param("itemId", "2"));

        // ---------------- 管理员 ----------------
        trace("管理员", "用户列表", "管理员", "GET", "/api/admin/users", get("/api/admin/users"));
        trace("管理员", "AI 调用记录", "管理员", "GET", "/api/ai/logs", get("/api/ai/logs"));

        Path out = Path.of("target", "sql-trace.json");
        Files.writeString(out, json.writerWithDefaultPrettyPrinter().writeValueAsString(flows), StandardCharsets.UTF_8);
        System.out.println("SQL 记录写到 " + out.toAbsolutePath() + "，共 " + flows.size() + " 个功能");
    }

    private static String demoPassword() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "data.sql"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("密码都是\\s*(\\S+?)（").matcher(sql);
        return m.find() ? m.group(1) : "";
    }

    /** 同时写到原来的控制台和缓冲区 */
    static class TeeStream extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        TeeStream(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) throws java.io.IOException {
            a.write(c);
            b.write(c);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws java.io.IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }
    }

}
