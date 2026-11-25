package com.se.platform.feynman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.service.ChapterService;
import com.se.platform.feynman.dto.FeynmanDtos.Candidate;
import com.se.platform.feynman.dto.FeynmanDtos.SetupView;
import com.se.platform.feynman.entity.FeynmanSession;
import com.se.platform.feynman.entity.FeynmanSetup;
import com.se.platform.feynman.mapper.FeynmanSessionMapper;
import com.se.platform.feynman.mapper.FeynmanSetupMapper;
import com.se.platform.material.dto.KpDtos.KeyPoint;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.mapper.ChapterKpStateMapper;
import com.se.platform.material.entity.ChapterKpState;
import com.se.platform.material.service.KpRecognizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 老师端：选知识点（最多 2 个）→ 大模型起草费曼对话 → 老师确认。
 * 草稿内容：场景、小林开场白、带着的误解、每条要点的判断标准和三级提示、讲不出时指向的资料、考一考小林。
 * 程序检查：每条要点都有草稿、三级提示和判断标准齐全、每句不超过 80 字、提示没有照抄要点原文（检查结果放在草稿里给老师看）。
 * 原型和第一次实测见 tools/feynman/（第 6 章第 3 个知识点：16 秒、约 0.03 元）。
 */
@Service
public class FeynmanDraftService {

    /** 一次对话最多几个知识点（组里定的：每个 5～8 分钟，2 个约 10～15 分钟） */
    public static final int MAX_KPS = 2;
    static final TypeReference<List<KeyPoint>> POINTS = new TypeReference<>() {
    };

    private final ChapterItemMapper itemMapper;
    private final CourseMapper courseMapper;
    private final ChapterService chapterService;
    private final ChapterKpMapper kpMapper;
    private final FeynmanSetupMapper setupMapper;
    private final ChapterKpStateMapper stateMapper;
    private final FeynmanSessionMapper sessionMapper;
    private final KpRecognizer recognizer;
    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate template = PromptTemplate.load("feynman_draft_v1");

    public FeynmanDraftService(ChapterItemMapper itemMapper, CourseMapper courseMapper, ChapterService chapterService,
                               ChapterKpMapper kpMapper, FeynmanSetupMapper setupMapper,
                               ChapterKpStateMapper stateMapper, FeynmanSessionMapper sessionMapper,
                               KpRecognizer recognizer,
                               AiCallLogService callLog, ObjectMapper json) {
        this.itemMapper = itemMapper;
        this.courseMapper = courseMapper;
        this.chapterService = chapterService;
        this.kpMapper = kpMapper;
        this.setupMapper = setupMapper;
        this.stateMapper = stateMapper;
        this.sessionMapper = sessionMapper;
        this.recognizer = recognizer;
        this.callLog = callLog;
        this.json = json;
    }

    public SetupView view(Long itemId) {
        ChapterItem item = requireDialogue(itemId);
        FeynmanSetup s = find(itemId);
        List<Candidate> candidates = kpMapper.selectList(new LambdaQueryWrapper<ChapterKp>()
                        .eq(ChapterKp::getChapterId, item.getChapterId()).orderByAsc(ChapterKp::getSortOrder))
                .stream().map(k -> new Candidate(k.getId(), k.getName(), k.getImportance())).toList();
        String kpStatus = kpStatus(item.getChapterId());
        if (s == null) {
            return new SetupView(itemId, "none", List.of(), List.of(), candidates, MAX_KPS, kpStatus, List.of());
        }
        List<JsonNode> d = drafts(s);
        return new SetupView(itemId, s.getStatus(), ids(s.getKpIds()), d, candidates, MAX_KPS, kpStatus, stale(d));
    }

    /** 这一章知识点识别的状态：none / waiting / recognizing / draft / confirmed / failed */
    String kpStatus(Long chapterId) {
        ChapterKpState st = stateMapper.selectOne(new LambdaQueryWrapper<ChapterKpState>()
                .eq(ChapterKpState::getChapterId, chapterId));
        return st == null ? ChapterKpState.NONE : st.getStatus();
    }

    /**
     * 草稿里存的是生成那一刻的要点和误解（补充给学生的标准答案就是它）。
     * 老师之后在“知识点”里改了，草稿不会跟着变：这里逐个比较，列出改过或删掉的知识点。
     */
    List<String> stale(List<JsonNode> drafts) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : drafts) {
            String name = n.path("name").asText();
            ChapterKp kp = kpMapper.selectById(n.path("kpId").asLong());
            if (kp == null) {
                out.add(name + "：知识点已删除");
                continue;
            }
            try {
                List<String> points = json.readValue(kp.getKeyPoints(), POINTS).stream().map(KeyPoint::text).toList();
                List<String> mis = json.readValue(kp.getMisconceptions(), new TypeReference<List<String>>() {
                });
                List<String> what = new ArrayList<>();
                if (!points.equals(json.convertValue(n.path("keyPoints"), new TypeReference<List<String>>() {
                }))) {
                    what.add("要点");
                }
                if (!mis.equals(json.convertValue(n.path("misconceptions"), new TypeReference<List<String>>() {
                }))) {
                    what.add("常见误解");
                }
                if (!what.isEmpty()) {
                    out.add(kp.getName() + "：" + String.join("、", what) + "改过");
                }
            } catch (Exception e) {
                out.add(name + "：读不出知识点内容");
            }
        }
        return out;
    }

    /** 生成（或重新生成）草稿：每个知识点调一次模型 */
    public SetupView draft(Long itemId, List<Long> kpIds) {
        ChapterItem item = requireDialogue(itemId);
        if (kpIds == null || kpIds.isEmpty()) {
            throw BusinessException.badRequest("请至少选 1 个知识点");
        }
        if (kpIds.size() > MAX_KPS) {
            throw BusinessException.badRequest("一次对话最多 " + MAX_KPS + " 个知识点，选最难理解的");
        }
        if (!ChapterKpState.CONFIRMED.equals(kpStatus(item.getChapterId()))) {
            // 要点是对话里判断学生、最后补充给学生的标准答案，必须老师先确认过
            throw BusinessException.badRequest("这一章的知识点还没确认（或改过之后还没再确认）：先到“知识点”里确认，再生成对话草稿");
        }
        Course course = courseMapper.selectById(item.getCourseId());
        Map<String, String> pointer = pointers(item.getChapterId());
        Map<String, String> texts = recognizer.segments(item.getChapterId()).stream()
                .collect(Collectors.toMap(KpRecognizer.Seg::key, s -> s.text().getContent(), (a, b) -> a));
        ArrayNode out = json.createArrayNode();
        List<String> usedScenes = new ArrayList<>();
        List<String> usedNames = new ArrayList<>();
        for (Long kpId : kpIds) {
            ChapterKp kp = kpMapper.selectById(kpId);
            if (kp == null || !kp.getChapterId().equals(item.getChapterId())) {
                throw BusinessException.badRequest("知识点不属于这一章");
            }
            out.add(draftOne(course.getName(), kp, pointer, texts, usedScenes, usedNames));
        }
        FeynmanSetup s = find(itemId);
        if (s == null) {
            s = new FeynmanSetup();
            s.setItemId(itemId);
            s.setChapterId(item.getChapterId());
        }
        s.setKpIds(kpIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        s.setDrafts(out.toString());
        s.setStatus(FeynmanSetup.DRAFT);
        if (s.getId() == null) {
            setupMapper.insert(s);
        } else {
            setupMapper.updateById(s);
        }
        // 草稿整份换了（知识点、同学、场景、要点都可能不同）：没聊完的旧对话作废，学生再打开从新草稿开始；记录保留
        FeynmanSession expired = new FeynmanSession();
        expired.setStatus(FeynmanSession.EXPIRED);
        sessionMapper.update(expired, new LambdaQueryWrapper<FeynmanSession>()
                .eq(FeynmanSession::getItemId, itemId).eq(FeynmanSession::getStatus, FeynmanSession.OPEN));
        return view(itemId);
    }

    /** 老师能改的字段，和每个字段的长度上限 */
    private static final Map<String, Integer> SCENE_FIELDS = Map.of("name", 6, "persona", 120, "setting", 200, "opening", 80);
    private static final Map<String, Integer> POINT_FIELDS = Map.of("understood_if", 300, "partial_if", 300,
            "L1", 80, "L2", 80, "L3", 80, "pointer", 300);

    /**
     * 老师改草稿里的文字。只改能改的字段，改完重新跑程序检查；已确认的草稿改了之后变回“待确认”，再确认一次学生才用新的。
     * 要点原文、选的误解不在这里改（要点去“知识点”里改，误解换一条就重新生成）。
     */
    public SetupView edit(Long itemId, Long kpId, JsonNode patch) {
        requireDialogue(itemId);
        FeynmanSetup s = find(itemId);
        if (s == null) {
            throw BusinessException.badRequest("还没有草稿");
        }
        if (patch == null || !patch.isObject()) {
            throw BusinessException.badRequest("没有要改的内容");
        }
        List<JsonNode> all = drafts(s);
        ObjectNode node = (ObjectNode) all.stream().filter(n -> n.path("kpId").asLong() == (kpId == null ? -1 : kpId))
                .findFirst().orElseThrow(() -> BusinessException.badRequest("这个知识点不在草稿里"));
        ObjectNode d = (ObjectNode) node.path("draft");
        copy(patch.path("scene"), (ObjectNode) d.path("scene"), SCENE_FIELDS, "场景");
        String line = patch.path("misconception_probe").path("line").asText(null);
        if (line != null) {
            ((ObjectNode) d.path("misconception_probe")).put("line", text(line, 80, "带着误解说的话"));
        }
        for (JsonNode p : patch.path("key_points")) {
            int no = p.path("no").asInt();
            for (JsonNode kp : d.path("key_points")) {
                if (kp.path("no").asInt() == no) {
                    copy(p, (ObjectNode) kp, POINT_FIELDS, "要点 " + no);
                }
            }
        }
        List<KeyPoint> points = new ArrayList<>();
        node.path("keyPoints").forEach(t -> points.add(new KeyPoint(t.asText(), List.of())));
        node.set("checks", json.valueToTree(check(d, points)));
        node.put("edited", true);
        s.setDrafts(json.valueToTree(all).toString());
        s.setStatus(FeynmanSetup.DRAFT);   // 改了就要再确认一次
        setupMapper.updateById(s);
        return view(itemId);
    }

    private void copy(JsonNode from, ObjectNode to, Map<String, Integer> fields, String where) {
        fields.forEach((f, max) -> {
            if (from.has(f)) {
                to.put(f, text(from.path(f).asText(), max, where + "的" + LABELS.getOrDefault(f, f)));
            }
        });
    }

    private static final Map<String, String> LABELS = Map.ofEntries(Map.entry("name", "名字"),
            Map.entry("persona", "身份"), Map.entry("setting", "情境"), Map.entry("opening", "开场白"),
            Map.entry("understood_if", "讲清标准"), Map.entry("partial_if", "不完整标准"),
            Map.entry("pointer", "资料位置"));

    private static String text(String v, int max, String what) {
        String t = v == null ? "" : v.strip();
        if (t.isEmpty()) {
            throw BusinessException.badRequest(what + "不能为空");
        }
        if (t.length() > max) {
            throw BusinessException.badRequest(what + "不能超过 " + max + " 字");
        }
        return t;
    }

    public SetupView confirm(Long itemId) {
        requireDialogue(itemId);
        FeynmanSetup s = find(itemId);
        if (s == null) {
            throw BusinessException.badRequest("还没有草稿");
        }
        List<String> stale = stale(drafts(s));
        if (!stale.isEmpty()) {
            throw BusinessException.badRequest("草稿生成之后知识点改过了（" + String.join("；", stale) + "），请重新生成草稿");
        }
        s.setStatus(FeynmanSetup.CONFIRMED);
        setupMapper.updateById(s);
        return view(itemId);
    }

    private ObjectNode draftOne(String course, ChapterKp kp, Map<String, String> pointer, Map<String, String> texts,
                                List<String> usedScenes, List<String> usedNames) {
        try {
            List<KeyPoint> points = json.readValue(kp.getKeyPoints(), POINTS);
            List<String> mis = json.readValue(kp.getMisconceptions(), new TypeReference<List<String>>() {
            });
            StringBuilder p = new StringBuilder();
            StringBuilder material = new StringBuilder();
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                KeyPoint kpnt = points.get(i);
                p.append(i + 1).append(". ").append(kpnt.text()).append("（资料位置：")
                        .append(kpnt.sources().stream().map(pointer::get).filter(x -> x != null).distinct()
                                .collect(Collectors.joining("、"))).append("）\n");
                for (String src : kpnt.sources()) {
                    if (!seen.contains(src) && texts.containsKey(src)) {
                        seen.add(src);
                        material.append('[').append(pointer.getOrDefault(src, src)).append("] ")
                                .append(texts.get(src).replaceAll("\\s+", " ")).append('\n');
                    }
                }
            }
            String prompt = template.render(Map.of("course", course, "name", kp.getName(),
                    "summary", kp.getSummary() == null ? "" : kp.getSummary(), "points", p.toString(),
                    "misconceptions", mis.stream().map(m -> "- " + m).collect(Collectors.joining("\n")),
                    "material", material.toString(),
                    "used_scenes", usedScenes.isEmpty() ? "无" : String.join("；", usedScenes),
                    "used_names", usedNames.isEmpty() ? "无" : String.join("、", usedNames)));
            ChatRequest req = new ChatRequest(List.of(ChatMessage.user(prompt)), 0.5, 8192, true);
            JsonNode d = null;
            for (int attempt = 0; attempt < 2 && d == null; attempt++) {
                try {
                    d = json.readTree(callLog.call(AiCallLogService.TASK_FEYNMAN_DRAFT, template.name(), req)
                            .result().content());
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    d = null;
                }
            }
            if (d == null || !d.has("scene") || !d.path("key_points").isArray()) {
                throw BusinessException.badRequest("AI 起草失败，请再点一次“生成对话草稿”");
            }
            stripSpeaker((ObjectNode) d, peerName(d));
            usedScenes.add(d.path("scene").path("setting").asText());
            usedNames.add(peerName(d));
            ObjectNode node = json.createObjectNode();
            node.put("kpId", kp.getId());
            node.put("name", kp.getName());
            node.set("keyPoints", json.valueToTree(points.stream().map(KeyPoint::text).toList()));
            node.set("pointers", json.valueToTree(points.stream().map(x -> x.sources().stream().map(pointer::get)
                    .filter(v -> v != null).distinct().collect(Collectors.joining("、"))).toList()));
            node.set("misconceptions", json.valueToTree(mis));
            node.set("draft", d);
            node.set("checks", json.valueToTree(check(d, points)));
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.badRequest("AI 起草失败：" + e.getMessage());
        }
    }

    /** 这些句子由程序直接发给学生，开头不该带“小柯说：”（实测 L2 出现过） */
    public static void stripSpeaker(ObjectNode d, String name) {
        java.util.function.UnaryOperator<String> clean = t -> t == null ? null
                : t.replaceFirst("^\\s*(" + Pattern.quote(name) + "|他|同学)\\s*(说|问)?\\s*[：:]\\s*", "");
        if (d.path("scene").isObject()) {
            ObjectNode sc = (ObjectNode) d.path("scene");
            sc.put("opening", clean.apply(sc.path("opening").asText()));
        }
        if (d.path("misconception_probe").isObject()) {
            ObjectNode mp = (ObjectNode) d.path("misconception_probe");
            mp.put("line", clean.apply(mp.path("line").asText()));
        }
        for (JsonNode p : d.path("key_points")) {
            if (p.isObject()) {
                for (String f : List.of("L1", "L2", "L3")) {
                    ((ObjectNode) p).put(f, clean.apply(p.path(f).asText()));
                }
            }
        }
    }

    /** AI 同学的名字：草稿里大模型起的；旧草稿没有名字就叫“小林” */
    public static String peerName(JsonNode draft) {
        String n = draft.path("scene").path("name").asText("").strip();
        return n.isEmpty() || n.length() > 6 ? "小林" : n;
    }

    /** 程序检查，结果给老师看 */
    List<Map<String, Object>> check(JsonNode d, List<KeyPoint> points) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Integer> got = new ArrayList<>();
        d.path("key_points").forEach(p -> got.add(p.path("no").asInt()));
        boolean all = got.size() == points.size();
        out.add(Map.of("name", "每条要点都有草稿", "ok", all, "detail", got.size() + "/" + points.size()));
        List<Integer> missing = new ArrayList<>();
        d.path("key_points").forEach(p -> {
            for (String f : List.of("L1", "L2", "L3", "understood_if", "partial_if", "pointer")) {
                if (p.path(f).asText("").isBlank()) {
                    missing.add(p.path("no").asInt());
                    return;
                }
            }
        });
        out.add(Map.of("name", "三级提示和判断标准都写了", "ok", missing.isEmpty(),
                "detail", missing.isEmpty() ? "齐全" : "缺：要点 " + missing));
        List<String> lines = new ArrayList<>(List.of(d.path("scene").path("opening").asText(),
                d.path("misconception_probe").path("line").asText()));
        d.path("key_points").forEach(p -> List.of("L1", "L2", "L3").forEach(f -> lines.add(p.path(f).asText())));
        long tooLong = lines.stream().filter(l -> l.length() > 80).count();
        out.add(Map.of("name", "每句不超过 80 字", "ok", tooLong == 0, "detail", "超长 " + tooLong + " 句"));
        List<String> leaks = new ArrayList<>();
        Pattern words = Pattern.compile("[\\u4e00-\\u9fff]{4,}");
        d.path("key_points").forEach(p -> {
            int no = p.path("no").asInt();
            if (no < 1 || no > points.size()) {
                return;
            }
            Matcher m = words.matcher(points.get(no - 1).text());
            List<String> terms = new ArrayList<>();
            while (m.find()) {
                terms.add(m.group());
            }
            for (String f : List.of("L1", "L2", "L3")) {
                terms.stream().filter(t -> p.path(f).asText().contains(t))
                        .forEach(t -> leaks.add("要点" + no + " " + f + "：" + t));
            }
        });
        out.add(Map.of("name", "提示没有照抄要点原文", "ok", leaks.isEmpty(),
                "detail", leaks.isEmpty() ? "无" : String.join("；", leaks)));
        return out;
    }

    /** 出处编号 → 给人看的位置：课件第 7 页 / 视频《6-1》的 03:09 */
    public Map<String, String> pointers(Long chapterId) {
        Map<String, String> out = new LinkedHashMap<>();
        for (KpRecognizer.Seg s : recognizer.segments(chapterId)) {
            String title = s.item().getTitle();
            String where = switch (s.text().getKind()) {
                case "video" -> "视频《" + title + "》的 " + KpRecognizer.mmss(s.text().getStartSec());
                case "pdf", "image" -> "《" + title + "》第 " + s.text().getPage() + " 页";
                default -> "《" + title + "》";
            };
            out.put(s.key(), where);
        }
        return out;
    }

    public FeynmanSetup find(Long itemId) {
        return setupMapper.selectOne(new LambdaQueryWrapper<FeynmanSetup>().eq(FeynmanSetup::getItemId, itemId));
    }

    public List<JsonNode> drafts(FeynmanSetup s) {
        try {
            List<JsonNode> out = new ArrayList<>();
            json.readTree(s.getDrafts()).forEach(out::add);
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    static List<Long> ids(String text) {
        return text == null || text.isBlank() ? List.of()
                : Arrays.stream(text.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    private ChapterItem requireDialogue(Long itemId) {
        ChapterItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BusinessException.notFound("内容");
        }
        chapterService.requireOwned(item.getChapterId());
        if (!"dialogue".equals(item.getType())) {
            throw BusinessException.badRequest("这一项不是 AI 对话");
        }
        return item;
    }
}
