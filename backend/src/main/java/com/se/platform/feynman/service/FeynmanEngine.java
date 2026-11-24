package com.se.platform.feynman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.common.BusinessException;
import com.se.platform.common.CurrentUser;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.service.ChapterItemService;
import com.se.platform.feynman.dto.FeynmanDtos.Availability;
import com.se.platform.feynman.dto.FeynmanDtos.KpView;
import com.se.platform.feynman.dto.FeynmanDtos.Message;
import com.se.platform.feynman.dto.FeynmanDtos.PointView;
import com.se.platform.feynman.dto.FeynmanDtos.SessionView;
import com.se.platform.feynman.entity.FeynmanMessage;
import com.se.platform.feynman.entity.FeynmanSession;
import com.se.platform.feynman.entity.FeynmanSetup;
import com.se.platform.feynman.mapper.FeynmanMessageMapper;
import com.se.platform.feynman.mapper.FeynmanSessionMapper;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.LearningEventMapper;
import com.se.platform.record.service.CompletionService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 费曼式对话本身：学生把知识点讲给“小林”听。规则见 docs/模块说明/设计方案-费曼式AI对话.md 第十二节。
 *
 * <p><b>每一句</b>：大模型判断（这句在讲解 / 提问 / 卡住 / 闲聊；有没有认真尝试；每条要点讲清了没有，必须摘录学生原话，
 * 程序核对原话真的存在），再写一句小林的短反应；接下来问什么由程序决定：
 * 讲错的要点优先，否则第一条没讲清的；这一句没有新进展才升一级提示（L1 → L2 → L3 → 指出资料），每条要点各算各的。
 *
 * <p><b>结束</b>（要点都讲清了，或学生说满 10 句，自动）：大模型读完整段对话总评 → 小林复述讲清的部分 →
 * 没讲清的：学生认真尝试过就补充（用老师确认的要点原文，不让模型自己编）并推荐复习；几乎没尝试就只推荐复习 →
 * 小结（只给学生看）→ 下一个知识点，或者结束。全部结束后下一项解锁。（原来最后还有“考一考”，补充之后再考没有意义，已去掉）
 * <p>不打分，只给诊断：每条要点是自己讲清、第几级提示后讲清、补充后才知道，还是没有尝试。
 */
@Slf4j
@Service
public class FeynmanEngine {

    /** 每个知识点学生最多说几句，满了自动结束 */
    public static final int MAX_TURNS = 10;
    /** 至少认真尝试几次，才会补充没讲清的要点 */
    public static final int MIN_ATTEMPTS = 3;

    private final FeynmanDraftService drafts;
    private final FeynmanSessionMapper sessionMapper;
    private final FeynmanMessageMapper messageMapper;
    private final ChapterItemMapper itemMapper;
    private final CompletionService completionService;
    private final LearningEventMapper eventMapper;
    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate turnTemplate = PromptTemplate.load("feynman_turn_v1");
    private final PromptTemplate finalTemplate = PromptTemplate.load("feynman_final_v1");

    public FeynmanEngine(FeynmanDraftService drafts, FeynmanSessionMapper sessionMapper,
                         FeynmanMessageMapper messageMapper, ChapterItemMapper itemMapper,
                         CompletionService completionService, LearningEventMapper eventMapper,
                         AiCallLogService callLog, ObjectMapper json) {
        this.drafts = drafts;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.itemMapper = itemMapper;
        this.completionService = completionService;
        this.eventMapper = eventMapper;
        this.callLog = callLog;
        this.json = json;
    }

    // ------------------------------------------------------------------ 进度（存在 feynman_session.state）

    /** 一条要点的进度。status：none / partial / wrong / clear；how：self / hint1～3 / pointer / supplemented */
    public static class Point {
        public String status = "none";
        public int level;
        public String how;
        public String quote;
        public boolean exhausted;
    }

    /** 一个知识点的进度。phase：talk 讲解中 / check 等学生判断小林 / done */
    public static class Kp {
        public long kpId;
        public String name;
        public String peer = "小林";   // AI 同学的名字，草稿里按场景起的
        public String phase = "talk";
        public int turns;
        public int attempts;
        public List<Point> points = new ArrayList<>();
        public Integer target;
        public boolean misconAsked;
        public boolean misconFixed;
        public boolean attempted;
        public String review;       // 推荐复习哪里
    }

    public static class State {
        public int idx;
        public List<Kp> kps = new ArrayList<>();
    }

    // ------------------------------------------------------------------ 对外

    public Availability availability(Long itemId) {
        FeynmanSetup s = drafts.find(itemId);
        if (s == null || !FeynmanSetup.CONFIRMED.equals(s.getStatus())) {
            return new Availability(false, 0, List.of());
        }
        List<JsonNode> d = drafts.drafts(s);
        return new Availability(true, d.size(), d.stream().map(n -> n.path("name").asText()).toList());
    }

    /** 开始，或接着上次没聊完的 */
    public SessionView start(Long itemId) {
        long student = CurrentUser.studentId();
        ChapterItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BusinessException.notFound("内容");
        }
        ChapterItemService.requireOpen(item, LocalDateTime.now());
        completionService.requireUnlocked(student, item);
        FeynmanSession open = sessionMapper.selectOne(new LambdaQueryWrapper<FeynmanSession>()
                .eq(FeynmanSession::getStudentId, student).eq(FeynmanSession::getItemId, itemId)
                .eq(FeynmanSession::getStatus, FeynmanSession.OPEN).last("LIMIT 1"));
        if (open != null) {
            return view(open);
        }
        FeynmanSetup setup = drafts.find(itemId);
        if (setup == null || !FeynmanSetup.CONFIRMED.equals(setup.getStatus())) {
            throw BusinessException.badRequest("老师还没有准备好这个对话");
        }
        List<JsonNode> d = drafts.drafts(setup);
        State st = new State();
        for (JsonNode n : d) {
            Kp k = new Kp();
            k.kpId = n.path("kpId").asLong();
            k.name = n.path("name").asText();
            k.peer = FeynmanDraftService.peerName(n.path("draft"));
            n.path("keyPoints").forEach(x -> k.points.add(new Point()));
            st.kps.add(k);
        }
        FeynmanSession s = new FeynmanSession();
        s.setStudentId(student);
        s.setItemId(itemId);
        s.setStatus(FeynmanSession.OPEN);
        save(s, st);
        say(s, 0, FeynmanMessage.SYSTEM, "这次请你把 " + st.kps.size() + " 个知识点讲给一位还没学过的同学听："
                + String.join("、", d.stream().map(n -> "「" + n.path("name").asText() + "」").toList())
                + "。讲不清的地方，他会一直追问；不用担心说错，这里不打分。", null, null);
        opening(s, st, d.get(0), 0);
        return view(s);
    }

    /** 学生说了一句 */
    public SessionView reply(Long sessionId, String text) {
        FeynmanSession s = requireOwn(sessionId);
        State st = read(s);
        Kp k = st.kps.get(st.idx);
        if (FeynmanSession.DONE.equals(s.getStatus()) || !"talk".equals(k.phase)) {
            throw BusinessException.badRequest("这次对话已经结束了");
        }
        if (text == null || text.isBlank()) {
            throw BusinessException.badRequest("先说点什么吧");
        }
        text = text.strip();
        if (text.length() > 500) {
            text = text.substring(0, 500);
        }
        JsonNode d = drafts.drafts(drafts.find(s.getItemId())).get(st.idx);
        Long msgId = say(s, st.idx, FeynmanMessage.STUDENT, text, null, null);
        k.turns++;

        JsonNode j = judge(s, st, k, d, text);
        FeynmanMessage m = messageMapper.selectById(msgId);
        m.setJudgment(j.toString());
        messageMapper.updateById(m);

        String kind = j.path("kind").asText("explain");
        if (j.path("attempt").asBoolean(false)) {
            k.attempts++;
        }
        if (j.path("misconception_fixed").asBoolean(false)) {
            k.misconFixed = true;
        }
        String said = studentText(s.getId(), st.idx);
        boolean progress = apply(k, j.path("points"), said);

        String ack = cleanAck(j.path("ack").asText(""));
        Integer before = k.target;
        boolean allClear = k.points.stream().allMatch(p -> "clear".equals(p.status));
        if (allClear || k.turns >= MAX_TURNS) {
            if (!ack.isEmpty()) {
                say(s, st.idx, FeynmanMessage.AI, ack, null, null);
            }
            finish(s, st, k, d);
            return view(s);
        }
        String next;
        if ("question".equals(kind)) {
            String ptr = pointer(d, Math.max(0, target(k)));
            next = "这个我也不确定，你可以看看" + (ptr.isEmpty() ? "资料" : ptr) + "，看完接着给我讲讲？";
        } else if (!k.misconAsked && k.turns >= 2 && !k.misconFixed) {
            k.misconAsked = true;   // 讲了两句以后，小林带着常见误解问一次：看学生能不能指出错在哪
            next = d.path("draft").path("misconception_probe").path("line").asText();
        } else if (progress && before != null && target(k) == before
                && !j.path("followup").asText("").isBlank()) {
            // 这条要点有进展但还没讲清：按学生刚说的，追问还缺的部分（不原样重复同一级提示），级别不变
            next = j.path("followup").asText().strip();
        } else {
            next = nextQuestion(k, d, progress);
            if (next == null) {   // 每条没讲清的要点都引导到头了
                finish(s, st, k, d);
                return view(s);
            }
        }
        say(s, st.idx, FeynmanMessage.AI, ack.isEmpty() ? next : ack + " " + next, null, null);
        save(s, st);
        return view(s);
    }

    public SessionView get(Long sessionId) {
        return view(requireOwn(sessionId));
    }

    // ------------------------------------------------------------------ 规则

    /** 用模型的判断更新要点状态；原话核对不上的不算。返回这一句有没有新进展 */
    boolean apply(Kp k, JsonNode points, String said) {
        boolean progress = false;
        String norm = normalize(said);
        for (JsonNode p : points) {
            int no = p.path("no").asInt();
            if (no < 1 || no > k.points.size()) {
                continue;
            }
            Point pt = k.points.get(no - 1);
            String status = p.path("status").asText("none");
            String quote = p.path("quote").asText("");
            if (!"none".equals(status) && (normalize(quote).length() < 2 || !norm.contains(normalize(quote)))) {
                continue;   // 模型说讲到了，但摘的不是学生原话：不算
            }
            if (rank(status) > rank(pt.status)) {
                if ("clear".equals(status) && pt.how == null) {
                    pt.how = pt.level == 0 ? "self" : pt.level <= 3 ? "hint" + pt.level : "pointer";
                }
                pt.status = status;
                pt.quote = quote;
                progress = true;
            } else if ("wrong".equals(status) && !"clear".equals(pt.status)) {
                pt.status = "wrong";
            }
        }
        return progress;
    }

    private static int rank(String status) {
        return switch (status) {
            case "clear" -> 3;
            case "partial" -> 2;
            case "wrong" -> 1;
            default -> 0;
        };
    }

    /** 第一条该追问的要点：讲错的优先，然后是第一条没讲清、还没引导到头的；-1 = 没有 */
    static int target(Kp k) {
        for (int i = 0; i < k.points.size(); i++) {
            if ("wrong".equals(k.points.get(i).status) && !k.points.get(i).exhausted) {
                return i;
            }
        }
        for (int i = 0; i < k.points.size(); i++) {
            Point p = k.points.get(i);
            if (!"clear".equals(p.status) && !p.exhausted) {
                return i;
            }
        }
        return -1;
    }

    /** 下一句追问：有进展就留在当前级别（第一次问这条要点从 L1 开始），没进展升一级；L3 之后指出资料 */
    String nextQuestion(Kp k, JsonNode d, boolean progress) {
        int t = target(k);
        if (t < 0) {
            return null;
        }
        Point p = k.points.get(t);
        boolean sameTarget = k.target != null && k.target == t;
        if (p.level == 0 || sameTarget) {   // 同一条要点再问一次就升一级，不原样重复
            p.level++;
        }
        k.target = t;
        JsonNode kp = keyPoint(d, t + 1);
        if (p.level <= 3) {
            return kp.path("L" + p.level).asText();
        }
        p.exhausted = true;   // 第 4 次：不给答案，指出资料；这条要点不再追问
        String pointer = pointer(d, t);
        pointer = pointer.isEmpty() ? "资料里" : pointer;
        String more = nextQuestionAfterPointer(k, d);
        return "这一块" + pointer + "讲了，你回头可以看看。" + (more == null ? "" : more);
    }

    private String nextQuestionAfterPointer(Kp k, JsonNode d) {
        int t = target(k);
        if (t < 0) {
            return null;
        }
        Point p = k.points.get(t);
        p.level = Math.max(p.level, 1);
        k.target = t;
        return "我们先说另一块：" + keyPoint(d, t + 1).path("L" + p.level).asText();
    }

    /** 第 i 条要点讲不出时指向哪里：老师确认过的草稿里写的；没有就用知识点的出处 */
    static String pointer(JsonNode d, int i) {
        String p = keyPoint(d, i + 1).path("pointer").asText("");
        return p.isBlank() ? d.path("pointers").path(i).asText("") : p;
    }

    private static JsonNode keyPoint(JsonNode d, int no) {
        for (JsonNode p : d.path("draft").path("key_points")) {
            if (p.path("no").asInt() == no) {
                return p;
            }
        }
        return d.path("draft").path("key_points").path(no - 1);
    }

    /** 收尾：总评 → 复述 → 补充没讲清的（认真尝试过才补）→ 建议复习 → 小结 → 下一个知识点或结束 */
    void finish(FeynmanSession s, State st, Kp k, JsonNode d) {
        JsonNode f = finalReview(s, st, k, d);
        if (f != null) {
            apply(k, f.path("points"), studentText(s.getId(), st.idx));
            k.attempted = f.path("attempted").asBoolean(k.attempts >= MIN_ATTEMPTS);
            if (f.path("misconception_fixed").asBoolean(false)) {
                k.misconFixed = true;
            }
            String recap = f.path("recap").asText("").strip().replaceFirst("^(我理解一下|我记住啦|我记住了|我理解的是)[：:，, ]*", "");
            if (!recap.isEmpty()) {
                say(s, st.idx, FeynmanMessage.AI, "我理解一下：" + recap, null, null);
            }
        } else {
            k.attempted = k.attempts >= MIN_ATTEMPTS;
        }
        k.attempted = k.attempted && k.attempts >= MIN_ATTEMPTS;
        java.util.LinkedHashSet<String> pointers = new java.util.LinkedHashSet<>();
        List<String> supplements = new ArrayList<>();
        for (int i = 0; i < k.points.size(); i++) {
            Point p = k.points.get(i);
            if ("clear".equals(p.status)) {
                continue;
            }
            for (String one : pointer(d, i).split("、")) {   // 多条要点指向同一页、同一段时只列一次
                if (!one.isBlank()) {
                    pointers.add(one.strip());
                }
            }
            if (k.attempted) {
                p.how = "supplemented";
                supplements.add(d.path("keyPoints").path(i).asText());
            }
        }
        if (!supplements.isEmpty()) {
            StringBuilder b = new StringBuilder("我刚去翻了资料，原来还有你没讲到的：");
            for (int i = 0; i < supplements.size(); i++) {
                b.append("\n").append(i + 1).append(". ").append(supplements.get(i));
            }
            say(s, st.idx, FeynmanMessage.AI, b.toString(), null, null);
        }
        if (!pointers.isEmpty()) {
            k.review = String.join("、", pointers);
            say(s, st.idx, FeynmanMessage.SYSTEM, (k.attempted ? "" : "这几块你还没怎么讲，")
                    + "建议接下来先看：" + k.review, null, null);
        }
        k.phase = "done";
        say(s, st.idx, FeynmanMessage.SYSTEM, summary(k, d), null, null);
        if (st.idx + 1 < st.kps.size()) {
            st.idx++;
            opening(s, st, drafts.drafts(drafts.find(s.getItemId())).get(st.idx), st.idx);
        } else {
            s.setStatus(FeynmanSession.DONE);
            say(s, st.idx, FeynmanMessage.SYSTEM, "对话完成了，下一项已经解锁。", null, null);
            finishItem(s);
        }
        save(s, st);
    }

    /** 短反应只是“嗯，我在听”的作用：去掉里面的问句和开头的对错评判 */
    static String cleanAck(String ack) {
        String a = ack == null ? "" : ack.strip();
        a = a.replaceAll("[^。！!？?]*[？?]", "").strip();   // 带问号的句子去掉：一次只问一个问题，问题由程序决定
        a = a.replaceFirst("^(对的?|没错|不对|很好|是的)[，,。！! ]*", "").strip();
        return a;
    }

    String summary(Kp k, JsonNode d) {
        StringBuilder b = new StringBuilder("「" + k.name + "」小结：");
        for (int i = 0; i < k.points.size(); i++) {
            Point p = k.points.get(i);
            b.append("\n要点 ").append(i + 1).append("：").append(label(p, k.peer));
        }
        b.append("\n常见误解：").append(k.misconFixed ? "你指出了它错在哪里" : "这次没有讨论到");
        if (k.review != null) {
            b.append("\n建议复习：").append(k.review);
        }
        return b.toString();
    }

    static String label(Point p, String peer) {
        if ("clear".equals(p.status)) {
            return switch (p.how == null ? "self" : p.how) {
                case "self" -> "自己讲清楚了";
                case "hint1" -> "追问后讲清楚了";
                case "hint2" -> "第 2 级提示后讲清楚了";
                case "hint3" -> "第 3 级提示后讲清楚了";
                default -> "看资料后讲清楚了";
            };
        }
        if ("supplemented".equals(p.how)) {
            return "partial".equals(p.status) ? "讲了一部分，" + peer + "补充了" : peer + "补充后才知道";
        }
        return "partial".equals(p.status) ? "讲了一部分" : "还没讲到";
    }

    // ------------------------------------------------------------------ 调模型

    JsonNode judge(FeynmanSession s, State st, Kp k, JsonNode d, String text) {
        int t = target(k);
        String prompt = turnTemplate.render(Map.of("peer", k.peer, "target", t < 0 ? "（无）" : String.valueOf(t + 1),
                "name", k.name, "points", pointsText(d),
                "misconception", d.path("draft").path("misconception_probe").path("misconception").asText(),
                "status", statusText(k), "recent", recent(s.getId(), st.idx, 12), "student", text));
        try {
            AiCallLogService.Logged r = callLog.call(AiCallLogService.TASK_FEYNMAN_TURN, turnTemplate.name(),
                    new ChatRequest(List.of(ChatMessage.user(prompt)), 0.2, 4096, true));
            return json.readTree(r.result().content());
        } catch (Exception e) {
            log.warn("费曼对话判断失败", e);
            return json.createObjectNode().put("kind", "explain").put("attempt", true).put("ack", "嗯，我在听。");
        }
    }

    JsonNode finalReview(FeynmanSession s, State st, Kp k, JsonNode d) {
        String prompt = finalTemplate.render(Map.of("peer", k.peer, "name", k.name, "points", pointsText(d),
                "misconception", d.path("draft").path("misconception_probe").path("misconception").asText(),
                "dialogue", recent(s.getId(), st.idx, 200)));
        try {
            AiCallLogService.Logged r = callLog.call(AiCallLogService.TASK_FEYNMAN_FINAL, finalTemplate.name(),
                    new ChatRequest(List.of(ChatMessage.user(prompt)), 0.2, 4096, true));
            return json.readTree(r.result().content());
        } catch (Exception e) {
            log.warn("费曼对话总评失败", e);
            return null;
        }
    }

    private static String pointsText(JsonNode d) {
        StringBuilder b = new StringBuilder();
        JsonNode texts = d.path("keyPoints");
        for (int i = 0; i < texts.size(); i++) {
            JsonNode kp = keyPoint(d, i + 1);
            b.append(i + 1).append(". ").append(texts.get(i).asText()).append("\n   讲清楚：")
                    .append(kp.path("understood_if").asText()).append("\n   不完整：")
                    .append(kp.path("partial_if").asText()).append('\n');
        }
        return b.toString();
    }

    private static String statusText(Kp k) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < k.points.size(); i++) {
            parts.add("要点" + (i + 1) + "=" + k.points.get(i).status);
        }
        return String.join("，", parts);
    }

    private String recent(Long sessionId, int kpIdx, int limit) {
        List<FeynmanMessage> all = messages(sessionId).stream().filter(m -> m.getKpIdx() == kpIdx
                && !FeynmanMessage.SYSTEM.equals(m.getRole())).toList();
        List<FeynmanMessage> tail = all.subList(Math.max(0, all.size() - limit), all.size());
        StringBuilder b = new StringBuilder();
        for (FeynmanMessage m : tail) {
            b.append(FeynmanMessage.STUDENT.equals(m.getRole()) ? "学生：" : "同学：").append(m.getContent()).append('\n');
        }
        return b.toString();
    }

    private String studentText(Long sessionId, int kpIdx) {
        StringBuilder b = new StringBuilder();
        messages(sessionId).stream().filter(m -> m.getKpIdx() == kpIdx && FeynmanMessage.STUDENT.equals(m.getRole()))
                .forEach(m -> b.append(m.getContent()).append(' '));
        return b.toString();
    }

    /** 比较原话时忽略空白和标点 */
    static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\p{Punct}，。、；：！？“”‘’（）《》…—]", "");
    }

    // ------------------------------------------------------------------ 内部

    private void opening(FeynmanSession s, State st, JsonNode d, int idx) {
        JsonNode scene = d.path("draft").path("scene");
        say(s, idx, FeynmanMessage.SYSTEM, "知识点 " + (idx + 1) + "/" + st.kps.size() + "：「" + d.path("name").asText()
                + "」\n" + st.kps.get(idx).peer + "：" + scene.path("persona").asText("还没学过这一章的同学")
                + "\n场景：" + scene.path("setting").asText(), null, null);
        say(s, idx, FeynmanMessage.AI, scene.path("opening").asText(), null, null);
        save(s, st);
    }

    private void finishItem(FeynmanSession s) {
        ChapterItem item = itemMapper.selectById(s.getItemId());
        LearningEvent e = new LearningEvent();
        e.setStudentId(s.getStudentId());
        e.setEventType(LearningEvent.FINISH_ITEM);
        e.setTargetId(item.getId());
        e.setCourseId(item.getCourseId());
        eventMapper.insert(e);
    }

    private Long say(FeynmanSession s, int kpIdx, String role, String content, String judgment, Long callId) {
        FeynmanMessage m = new FeynmanMessage();
        m.setSessionId(s.getId());
        m.setKpIdx(kpIdx);
        m.setRole(role);
        m.setContent(content);
        m.setJudgment(judgment);
        m.setAiCallId(callId);
        messageMapper.insert(m);
        return m.getId();
    }

    private List<FeynmanMessage> messages(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<FeynmanMessage>()
                .eq(FeynmanMessage::getSessionId, sessionId).orderByAsc(FeynmanMessage::getId));
    }

    private FeynmanSession requireOwn(Long id) {
        FeynmanSession s = sessionMapper.selectById(id);
        if (s == null || !s.getStudentId().equals(CurrentUser.studentId())) {
            throw BusinessException.notFound("对话");
        }
        return s;
    }

    private State read(FeynmanSession s) {
        try {
            return json.readValue(s.getState(), State.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void save(FeynmanSession s, State st) {
        try {
            s.setState(json.writeValueAsString(st));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        s.setUpdatedAt(LocalDateTime.now());
        if (s.getId() == null) {
            sessionMapper.insert(s);
        } else {
            sessionMapper.updateById(s);
        }
    }

    private SessionView view(FeynmanSession s) {
        State st = read(s);
        List<KpView> kps = st.kps.stream().map(k -> new KpView(k.name, k.peer, k.phase, k.turns, MAX_TURNS,
                (int) k.points.stream().filter(p -> "clear".equals(p.status)).count(), k.points.size(),
                k.points.stream().map(p -> new PointView(k.points.indexOf(p) + 1, p.status, p.how)).toList(),
                "done".equals(k.phase) ? k.misconFixed : null, k.review)).toList();
        List<Message> msgs = messages(s.getId()).stream()
                .map(m -> new Message(m.getRole(), m.getContent(), m.getKpIdx())).toList();
        return new SessionView(s.getId(), s.getStatus(), st.idx, kps, msgs);
    }
}
