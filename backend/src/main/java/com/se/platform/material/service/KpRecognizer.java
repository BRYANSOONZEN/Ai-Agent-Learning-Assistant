package com.se.platform.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.entity.ChapterKpState;
import com.se.platform.material.entity.MaterialText;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.mapper.ChapterKpStateMapper;
import com.se.platform.material.mapper.MaterialTextMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 把一章所有资料转出来的文字交给大模型，识别知识点，存成草稿（chapter_kp），等老师确认。
 *
 * <p>编号：按章节里的顺序，文档是 D1、D2……，视频是 V1、V2……；每段文字的编号 = 资料编号 + 段编号，
 * 例如 D1-P4（第 1 份文档第 4 页）、D1-P13-图1、V2-03。模型给的出处只能是这些编号，程序会核对，编出来的去掉。
 *
 * <p>重新识别：老师改过的知识点（edited）保留，名称相近的新结果不再重复加；其他的换成新结果。
 * 验证记录（准确率、稳定性、费用）见 tools/material_kp/验证结果/第6章-记录.md。
 */
@Slf4j
@Service
public class KpRecognizer {

    /** 一章的资料超过这么多字就不一次交给模型（第 6 章课件 + 图片 + 视频约 2.5 万字） */
    static final int MAX_MATERIAL_CHARS = 60000;
    /** 每个知识点最多几条要点 */
    public static final int MAX_KEY_POINTS = 3;
    /** 8192 时实测会被截断（模型的“思考”也算输出） */
    static final int MAX_TOKENS = 16384;
    static final double SAME_NAME = 0.4;

    private final ChapterItemMapper itemMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final MaterialTextMapper textMapper;
    private final ChapterKpMapper kpMapper;
    private final ChapterKpStateMapper stateMapper;
    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate template = PromptTemplate.load("kp_extract_v1");

    public KpRecognizer(ChapterItemMapper itemMapper, ChapterMapper chapterMapper, CourseMapper courseMapper,
                        MaterialTextMapper textMapper, ChapterKpMapper kpMapper, ChapterKpStateMapper stateMapper,
                        AiCallLogService callLog, ObjectMapper json) {
        this.itemMapper = itemMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.textMapper = textMapper;
        this.kpMapper = kpMapper;
        this.stateMapper = stateMapper;
        this.callLog = callLog;
        this.json = json;
    }

    /** 一段文字在整章里的编号和位置 */
    public record Seg(String key, ChapterItem item, MaterialText text, String label) {
    }

    /** 这一章的全部资料文字，按章节顺序编号。老师写在页面上的文字（description）也算一段（D1-T0） */
    public List<Seg> segments(Long chapterId) {
        List<ChapterItem> items = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                .eq(ChapterItem::getChapterId, chapterId)
                .in(ChapterItem::getType, "document", "video")
                .orderByAsc(ChapterItem::getSortOrder).orderByAsc(ChapterItem::getId));
        Map<Long, List<MaterialText>> byItem = new LinkedHashMap<>();
        textMapper.selectList(new LambdaQueryWrapper<MaterialText>().eq(MaterialText::getChapterId, chapterId)
                        .orderByAsc(MaterialText::getSortOrder))
                .forEach(t -> byItem.computeIfAbsent(t.getItemId(), k -> new ArrayList<>()).add(t));
        List<Seg> out = new ArrayList<>();
        int d = 0;
        int v = 0;
        for (ChapterItem it : items) {
            boolean video = "video".equals(it.getType());
            String prefix = video ? "V" + (++v) : "D" + (++d);
            if (!video && it.getDescription() != null && !it.getDescription().isBlank()) {
                MaterialText t = new MaterialText();
                t.setItemId(it.getId());
                t.setKind("text");
                t.setSegKey("T0");
                t.setContent(it.getDescription().strip());
                out.add(new Seg(prefix + "-T0", it, t, "《" + it.getTitle() + "》老师写的文字"));
            }
            for (MaterialText t : byItem.getOrDefault(it.getId(), List.of())) {
                out.add(new Seg(prefix + "-" + t.getSegKey(), it, t, label(it, t)));
            }
        }
        return out;
    }

    static String label(ChapterItem it, MaterialText t) {
        return switch (t.getKind()) {
            case "video" -> "视频《" + it.getTitle() + "》" + mmss(t.getStartSec()) + "～" + mmss(t.getEndSec());
            case "image" -> "《" + it.getTitle() + "》第 " + t.getPage() + " 页的图";
            case "pdf" -> "《" + it.getTitle() + "》第 " + t.getPage() + " 页";
            default -> "《" + it.getTitle() + "》";
        };
    }

    public static String mmss(java.math.BigDecimal sec) {
        int s = sec == null ? 0 : sec.intValue();
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** 识别并存草稿；失败时状态变成 failed，原因写在 message */
    public void recognize(Long chapterId) {
        ChapterKpState state = stateMapper.selectOne(new LambdaQueryWrapper<ChapterKpState>()
                .eq(ChapterKpState::getChapterId, chapterId));
        try {
            List<Seg> segs = segments(chapterId);
            if (segs.isEmpty()) {
                fail(state, "这一章还没有可以识别的资料（视频、PDF 或写在文档里的文字）");
                return;
            }
            StringBuilder material = new StringBuilder();
            for (Seg s : segs) {
                material.append('[').append(s.key()).append(' ').append(s.label()).append("] ")
                        .append(s.text().getContent().replaceAll("\\s+", " ")).append('\n');
            }
            if (material.length() > MAX_MATERIAL_CHARS) {
                fail(state, "资料太长（" + material.length() + " 字，上限 " + MAX_MATERIAL_CHARS + "），请把这一章拆成两章");
                return;
            }
            Chapter chapter = chapterMapper.selectById(chapterId);
            Course course = courseMapper.selectById(chapter.getCourseId());
            String prompt = template.render(Map.of("course", course.getName(), "chapter", String.valueOf(
                    chapter.getSortOrder() == null ? "" : chapter.getSortOrder() + 1), "title", chapter.getTitle(),
                    "material", material.toString()));
            ChatRequest req = new ChatRequest(List.of(ChatMessage.user(prompt)), 0.2, MAX_TOKENS, true);
            JsonNode root = null;
            AiCallLogService.Logged logged = null;
            for (int attempt = 0; attempt < 2 && root == null; attempt++) {   // 回复不是合法 JSON（多半是被截断）就重试一次
                logged = callLog.call(AiCallLogService.TASK_KP_EXTRACT, template.name(), req);
                try {
                    root = json.readTree(logged.result().content());
                } catch (JsonProcessingException e) {
                    log.warn("知识点识别：回复不是合法 JSON，第 {} 次", attempt + 1);
                }
            }
            if (root == null || !root.path("knowledge_points").isArray()) {
                fail(state, "AI 的回复格式不对，请点“重新识别”");
                return;
            }
            Set<String> valid = new HashSet<>();
            segs.forEach(s -> valid.add(s.key()));
            List<ChapterKp> fresh = parse(root.path("knowledge_points"), valid, chapterId);
            if (fresh.isEmpty()) {
                fail(state, "没有识别出有出处的知识点，请检查资料内容后点“重新识别”");
                return;
            }
            int n = save(chapterId, fresh);
            state.setStatus(ChapterKpState.DRAFT);
            state.setMessage("识别出 " + n + " 个知识点，待你确认");
            state.setRecognizedAt(LocalDateTime.now());
            state.setConfirmedAt(null);
            state.setAiCallId(logged.logId());
            stateMapper.updateById(state);
        } catch (Exception e) {
            log.warn("知识点识别失败 chapter={}", chapterId, e);
            fail(state, "识别失败：" + e.getMessage());
        }
    }

    /** 解析模型的结果；出处只保留真实存在的编号，没有出处的要点去掉 */
    List<ChapterKp> parse(JsonNode arr, Set<String> valid, Long chapterId) throws JsonProcessingException {
        List<ChapterKp> out = new ArrayList<>();
        for (JsonNode k : arr) {
            String name = k.path("name").asText("").strip();
            if (name.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> points = new ArrayList<>();
            Set<String> all = new LinkedHashSet<>();
            for (JsonNode p : k.path("key_points")) {
                List<String> src = new ArrayList<>();
                p.path("sources").forEach(s -> {
                    if (valid.contains(s.asText())) {
                        src.add(s.asText());
                    }
                });
                if (src.isEmpty() || p.path("text").asText("").isBlank()) {
                    continue;
                }
                all.addAll(src);
                if (points.size() >= MAX_KEY_POINTS) {
                    break;   // 每个知识点最多 3 条要点（对话里每条要点要走三级提示，10 句只够 3 条）
                }
                points.add(Map.of("text", p.path("text").asText().strip(), "sources", src));
            }
            k.path("sources").forEach(s -> {
                if (valid.contains(s.asText())) {
                    all.add(s.asText());
                }
            });
            if (points.isEmpty()) {
                continue;
            }
            List<String> mis = new ArrayList<>();
            k.path("misconceptions").forEach(m -> {
                if (!m.asText("").isBlank()) {
                    mis.add(m.asText().strip());
                }
            });
            ChapterKp kp = new ChapterKp();
            kp.setChapterId(chapterId);
            kp.setName(name.length() > 100 ? name.substring(0, 100) : name);
            kp.setSummary(k.path("summary").asText(""));
            kp.setImportance("一般".equals(k.path("importance").asText()) ? "一般" : "重点");
            kp.setKeyPoints(json.writeValueAsString(points));
            kp.setMisconceptions(json.writeValueAsString(mis));
            kp.setSources(json.writeValueAsString(all));
            kp.setEdited(false);
            out.add(kp);
        }
        return out;
    }

    /** 保留老师改过的，其余换成新结果；返回最后的知识点数 */
    private int save(Long chapterId, List<ChapterKp> fresh) {
        List<ChapterKp> old = kpMapper.selectList(new LambdaQueryWrapper<ChapterKp>()
                .eq(ChapterKp::getChapterId, chapterId).orderByAsc(ChapterKp::getSortOrder));
        List<ChapterKp> kept = old.stream().filter(k -> Boolean.TRUE.equals(k.getEdited())).toList();
        old.stream().filter(k -> !Boolean.TRUE.equals(k.getEdited())).forEach(k -> kpMapper.deleteById(k.getId()));
        int order = 0;
        List<ChapterKp> used = new ArrayList<>();
        for (ChapterKp k : fresh) {
            ChapterKp twin = kept.stream().filter(e -> !used.contains(e) && similar(e.getName(), k.getName()) >= SAME_NAME)
                    .findFirst().orElse(null);
            if (twin != null) {   // 老师改过的同一个知识点：留老师的版本，放在新结果的位置
                used.add(twin);
                twin.setSortOrder(order++);
                kpMapper.updateById(twin);
                continue;
            }
            k.setSortOrder(order++);
            kpMapper.insert(k);
        }
        for (ChapterKp e : kept) {
            if (!used.contains(e)) {
                e.setSortOrder(order++);
                kpMapper.updateById(e);
            }
        }
        return order;
    }

    /** 名称相似度：汉字按相邻两个字、英文按单词，算重合比例（和 tools/material_kp/extract_kps.py 一样） */
    static double similar(String a, String b) {
        Set<String> ga = grams(a);
        Set<String> gb = grams(b);
        if (ga.isEmpty() && gb.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(ga);
        inter.retainAll(gb);
        Set<String> union = new HashSet<>(ga);
        union.addAll(gb);
        return (double) inter.size() / union.size();
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+|[\\u4e00-\\u9fff]+");

    static Set<String> grams(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = WORD.matcher(text == null ? "" : text);
        while (m.find()) {
            String w = m.group();
            if (w.chars().allMatch(c -> c < 128)) {
                out.add(w.toLowerCase());
            } else {
                for (int i = 0; i + 1 < w.length(); i++) {
                    out.add(w.substring(i, i + 2));
                }
                if (w.length() == 1) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    private void fail(ChapterKpState state, String message) {
        state.setStatus(ChapterKpState.FAILED);
        state.setMessage(message);
        stateMapper.updateById(state);
    }
}
