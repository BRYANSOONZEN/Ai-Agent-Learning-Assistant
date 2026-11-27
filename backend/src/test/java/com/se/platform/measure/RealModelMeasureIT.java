package com.se.platform.measure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.se.platform.common.DemoIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 用真实大模型实测“老师干预的补充讲解 + 新题”和“小助手拓展题”的用时和费用。
 * <b>会花钱（每次约几分钱），不进日常测试和 CI</b>：类名以 IT 结尾，mvnw test 不会跑。手动运行：
 * <pre>mvnw test -Dtest=RealModelMeasureIT -Dsurefire.failIfNoSpecifiedTests=false</pre>
 * 用内存库和演示数据，不动开发库；API key 从 application-local.yml 读。结果写到 docs/评测/真实模型实测-干预与拓展.md。
 * 场景和 ClassAnalysisTest 一样：第 1 章练习截止，知识点 1 · 概念辨析 7 人里 5 人错 → 一张卡片。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "platform.class-analysis.min-submit-ratio=0.6")
class RealModelMeasureIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private String body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void measure() throws Exception {
        jdbc.update("""
                INSERT INTO chapter_kp (chapter_id, sort_order, name, summary, importance, key_points, misconceptions,
                  sources, edited, knowledge_point_id, created_at, updated_at)
                VALUES (1, 0, '边界测试值的选取', '边界上、外、内各取值', '重点',
                  '[{"text":"边界外的值也要测，比如 0","sources":["D1-T0"]},{"text":"边界上、边界内侧各取一个值","sources":["D1-T0"]}]',
                  '["测中间值最有代表性"]', '[]', 0, 1, NOW(), NOW())""");
        StringBuilder md = new StringBuilder("# 真实模型实测：老师干预的补充讲解 + 新题、小助手拓展题\n\n")
                .append("> 由 `backend/src/test/java/com/se/platform/measure/RealModelMeasureIT.java` 生成（内存库 + 演示数据，模型 deepseek-flash）。\n")
                .append("> 价格按 application.yml：输入每百万 token 2.2 元，输出 8.7 元。\n\n");

        // ---------- 老师干预：处理卡片（起草讲解 + 出 3 道新题 + 不看答案做一遍） ----------
        int runs = 2;
        md.append("## 一、老师干预：补充讲解 + 3 道新题\n\n| 次 | 结果 | 调用 | 用时（秒） | 输入 token | 输出 token | 费用（元） |\n|---|---|---|---|---|---|---|\n");
        for (int i = 1; i <= runs; i++) {
            jdbc.update("DELETE FROM class_push");
            jdbc.update("UPDATE class_issue SET status = 'open'");
            jdbc.update("UPDATE chapter_item SET due_at = TIMESTAMPADD(HOUR, -1, NOW()) WHERE id = 2");
            int issueId = JsonPath.read(body(mockMvc.perform(get("/api/class-analysis").param("courseId", "1"))),
                    "$.data.items[0].groups[0].issues[0].id");
            long before = maxLog();
            String push = body(mockMvc.perform(post("/api/class-pushes").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"issueIds\": [" + issueId + "], \"aiExplanation\": true}")));
            Object n = JsonPath.read(push, "$.data.questions.length()");
            md.append(row(i, "讲解 " + ((String) JsonPath.read(push, "$.data.explanation")).length() + " 字，新题 " + n + " 道", before));
        }

        // ---------- 小助手拓展：已掌握的知识点，按考查类型出 3 道更难的题 ----------
        // 学生04（id 8）第一次练习全对 → 知识点 1 绿色
        mockMvc.perform(post("/api/learn/events").header(DemoIdentity.HEADER, "8").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"finish_item\", \"itemId\": 3}"));
        md.append("\n## 二、小助手拓展题（一组 3 道，更难）\n\n| 次 | 考查类型 | 调用 | 用时（秒） | 输入 token | 输出 token | 费用（元） |\n|---|---|---|---|---|---|---|\n");
        int i = 1;
        for (String assess : List.of("scenario", "data")) {
            long before = maxLog();
            String r = body(mockMvc.perform(post("/api/assistant/extend/1/start").header(DemoIdentity.HEADER, "8")
                    .param("assessType", assess)));
            Object n = JsonPath.read(r, "$.data.questions.length()");
            md.append(row(i++, assess + "，" + n + " 道", before));
        }
        Path out = Path.of("..", "docs", "评测", "真实模型实测-干预与拓展.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);
    }

    private long maxLog() {
        Long m = jdbc.queryForObject("SELECT MAX(id) FROM ai_call_log", Long.class);
        return m == null ? 0 : m;
    }

    /** 这一步之后新增的模型调用合计 */
    private String row(int i, String what, long afterId) {
        List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT task, COALESCE(latency_ms, 0) AS latency_ms, COALESCE(input_tokens, 0) AS input_tokens, COALESCE(output_tokens, 0) AS output_tokens, COALESCE(cost_cny, 0) AS cost FROM ai_call_log WHERE id > ?", afterId);
        long ms = 0, in = 0, outT = 0;
        double cost = 0;
        StringBuilder tasks = new StringBuilder();
        for (Map<String, Object> l : logs) {
            ms += ((Number) l.get("latency_ms")).longValue();
            in += ((Number) l.get("input_tokens")).longValue();
            outT += ((Number) l.get("output_tokens")).longValue();
            cost += ((Number) l.get("cost")).doubleValue();
            tasks.append(tasks.isEmpty() ? "" : " + ").append(l.get("task"));
        }
        return String.format("| %d | %s | %s | %.1f | %d | %d | %.4f |%n", i, what, tasks, ms / 1000.0, in, outT, cost);
    }
}
