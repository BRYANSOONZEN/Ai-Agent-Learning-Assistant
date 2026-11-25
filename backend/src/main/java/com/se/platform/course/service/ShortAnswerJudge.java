package com.se.platform.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.PointResult;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

/**
 * 简答题判分（设计见 docs/模块说明/练习与掌握度-设计.md 第四节）：
 * <ol>
 *   <li>程序先判：没写、少于 5 个字 → 0 分；和参考答案几乎一样 → 1 分。这两种不调大模型，可信度 r = 1</li>
 *   <li>其他：大模型独立判两次，每次逐条要点判“答到没有”，答到的要摘学生原话；程序核对原话确实在答案里，找不到算没答到</li>
 *   <li>两次每条要点都一样 → 采用，得分 = 答到的要点数 / 要点总数，r = 0.5（只记录，掌握度不再用它）</li>
 *   <li>有一条不一样，或者调用失败 → pending，等老师确认，确认前不算进掌握度</li>
 * </ol>
 */
@Service
public class ShortAnswerJudge {

    /** 大模型两次判分一致时的可信度权重（测出准确率后换成实测的一致率） */
    public static final double MODEL_RELIABILITY = 0.5;
    static final int MIN_CHARS = 5;

    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate template = PromptTemplate.load("short_judge_v1");

    public ShortAnswerJudge(AiCallLogService callLog, ObjectMapper json) {
        this.callLog = callLog;
        this.json = json;
    }

    public QuestionResult judge(ExerciseView e, String raw) {
        String answer = raw == null ? "" : raw.strip();
        List<String> points = e.keyPoints();
        String norm = Grader.normalize(answer);
        if (norm.length() < MIN_CHARS) {
            return result(e, answer, all(points, false, "答案太短"), 0, 1, Grader.GRADED);
        }
        if (norm.equals(Grader.normalize(e.reference()))) {
            return result(e, answer, all(points, true, "和参考答案一致"), 1, 1, Grader.GRADED);
        }
        String prompt = template.render(Map.of("stem", e.stem(), "reference", e.reference(),
                "points", numbered(points), "answer", answer));
        CompletableFuture<List<Hit>> first = CompletableFuture.supplyAsync(() -> once(prompt, points.size(), norm));
        CompletableFuture<List<Hit>> second = CompletableFuture.supplyAsync(() -> once(prompt, points.size(), norm));
        List<Hit> a = first.join();
        List<Hit> b = second.join();
        boolean agreed = a != null && b != null;
        int hits = 0;
        List<PointResult> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Hit ha = a == null ? null : a.get(i);
            Hit hb = b == null ? null : b.get(i);
            boolean same = ha != null && hb != null && ha.hit() == hb.hit();
            agreed &= same;
            if (same && ha.hit()) {
                hits++;
            }
            String quote = ha != null && !ha.quote().isEmpty() ? ha.quote() : hb != null ? hb.quote() : "";
            out.add(new PointResult(i + 1, points.get(i), same ? ha.hit() : null, quote, same));
        }
        if (!agreed) {
            return result(e, answer, out, 0, MODEL_RELIABILITY, Grader.PENDING);
        }
        return result(e, answer, out, (double) hits / points.size(), MODEL_RELIABILITY, Grader.GRADED);
    }

    /** 大模型对一条要点的判断：答到没有，学生原话（核对通过才保留） */
    record Hit(boolean hit, String quote) {
    }

    /** 判一次；格式不对、调用失败返回 null */
    private List<Hit> once(String prompt, int n, String normAnswer) {
        try {
            ChatRequest req = new ChatRequest(List.of(ChatMessage.user(prompt)), 0.2, 1024, true);
            JsonNode d = json.readTree(callLog.call(AiCallLogService.TASK_SHORT_JUDGE, template.name(), req)
                    .result().content());
            JsonNode arr = d.path("points");
            if (!arr.isArray() || arr.size() != n) {
                return null;
            }
            List<Hit> out = new ArrayList<>();
            for (JsonNode p : arr) {
                boolean hit = p.path("hit").asBoolean(false);
                String quote = p.path("quote").asText("").strip();
                // 原话必须真的在学生答案里（去掉标点空格后比），找不到就算没答到
                boolean found = !quote.isEmpty() && normAnswer.contains(Grader.normalize(quote));
                out.add(new Hit(hit && found, found ? quote : ""));
            }
            return out;
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<PointResult> all(List<String> points, boolean hit, String quote) {
        List<PointResult> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            out.add(new PointResult(i + 1, points.get(i), hit, quote, true));
        }
        return out;
    }

    private static String numbered(List<String> points) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            sb.append(i + 1).append(". ").append(points.get(i)).append('\n');
        }
        return sb.toString();
    }

    private static QuestionResult result(ExerciseView e, String answer, List<PointResult> points, double score,
                                         double r, String status) {
        double s = Math.round(score * 10000) / 10000.0;
        return new QuestionResult(e.id(), Grader.SHORT, e.stem(), List.of(), answer, "", Grader.GRADED.equals(status)
                && s >= 1, e.analysis(), null, s, 0, r, status, List.of(), null, null, List.of(), e.reference(), points);
    }

    /** 老师改判：每条要点答到没有 → 得分，可信度 r = 1 */
    public static double scoreOf(List<Boolean> hits) {
        if (hits.isEmpty()) {
            return 0;
        }
        return Math.round(hits.stream().filter(Boolean.TRUE::equals).count() * 10000.0 / hits.size()) / 10000.0;
    }

}
