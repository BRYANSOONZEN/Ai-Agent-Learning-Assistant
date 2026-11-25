package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.client.ChatResult;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ExerciseSaveRequest;
import com.se.platform.course.dto.ExerciseToolDtos.GenRequest;
import com.se.platform.course.dto.ExerciseToolDtos.GenResult;
import com.se.platform.course.dto.ExerciseToolDtos.KpGen;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.feynman.service.FeynmanDraftService;
import com.se.platform.material.dto.KpDtos.KeyPoint;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.service.KpRecognizer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 练习的两种出题方式（设计见 docs/模块说明/练习与掌握度-设计.md 第二、三节）：
 * <ul>
 *   <li>AI 生成：老师选知识点、考查类型、题型、每个知识点几道 → 每个知识点调一次模型出题 →
 *       程序检查（格式、照抄原文、要点和误解的覆盖）→ 再调一次模型“不看答案做一遍”，选的和答案不一样的标为可能有歧义</li>
 *   <li>上传识别：老师上传已有题目（.txt / .md / .docx，或者直接粘贴）→ 模型整理成题目，并标上知识点和考查类型，
 *       不改题目内容 → 程序检查</li>
 * </ul>
 * 两种方式出的题都先存成草稿，老师检查、修改后点“确认”，学生才看得到。
 */
@Service
public class ExerciseGenService {

    static final Map<String, String> ASSESS_NAMES = new LinkedHashMap<>();
    /** 老师的其他要求最多几个字 */
    static final int MAX_REQUIREMENT = 500;
    /** 按老师要求多出题时，每个知识点最多存几道 */
    static final int MAX_PER_KP = 8;
    static final Map<String, String> TYPE_NAMES = new LinkedHashMap<>();

    static {
        ASSESS_NAMES.put("concept", "概念辨析：分不分得清概念的边界，有没有常见误解；错误选项用常见误解来写");
        ASSESS_NAMES.put("scenario", "情境应用：给一个新的具体场景（课件里没有的），问该用哪种方法、环节或技术");
        ASSESS_NAMES.put("data", "数据分析：给出一组实验数据或结果（可以编一组合理的数据），问能得出什么结论");
        ASSESS_NAMES.put("process", "流程步骤：步骤的先后顺序、每一步做什么、下一步该做什么");
        TYPE_NAMES.put(Grader.SINGLE, "单选");
        TYPE_NAMES.put(Grader.TWO_TIER, "两段式单选（先选答案，再选理由）");
        TYPE_NAMES.put(Grader.MULTIPLE, "多选（至少 2 个正确选项，全对才算对）");
        TYPE_NAMES.put(Grader.JUDGE, "判断");
        TYPE_NAMES.put(Grader.FILL, "填空（答案是一个词或短语）");
        TYPE_NAMES.put(Grader.SHORT, "简答（一段话）");
    }

    private final ChapterItemMapper itemMapper;
    private final CourseMapper courseMapper;
    private final ChapterKpMapper chapterKpMapper;
    private final KnowledgePointMapper kpMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseService exerciseService;
    private final ChapterService chapterService;
    private final KpRecognizer recognizer;
    private final FeynmanDraftService feynmanDraft;
    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate genTemplate = PromptTemplate.load("exercise_gen_v1");
    private final PromptTemplate solveTemplate = PromptTemplate.load("exercise_solve_v1");
    private final PromptTemplate importTemplate = PromptTemplate.load("exercise_import_v1");

    public ExerciseGenService(ChapterItemMapper itemMapper, CourseMapper courseMapper, ChapterKpMapper chapterKpMapper,
                              KnowledgePointMapper kpMapper, ExerciseMapper exerciseMapper,
                              ExerciseService exerciseService, ChapterService chapterService,
                              KpRecognizer recognizer, FeynmanDraftService feynmanDraft, AiCallLogService callLog,
                              ObjectMapper json) {
        this.itemMapper = itemMapper;
        this.courseMapper = courseMapper;
        this.chapterKpMapper = chapterKpMapper;
        this.kpMapper = kpMapper;
        this.exerciseMapper = exerciseMapper;
        this.exerciseService = exerciseService;
        this.chapterService = chapterService;
        this.recognizer = recognizer;
        this.feynmanDraft = feynmanDraft;
        this.callLog = callLog;
        this.json = json;
    }

    /** 统计一次出题的耗时、调用次数和费用 */
    private final class Meter {
        final long start = System.currentTimeMillis();
        int calls;
        int in;
        int out;

        ChatResult call(String task, PromptTemplate t, String prompt, double temperature, int maxTokens) {
            calls++;
            ChatResult r = callLog.call(task, t.name(),
                    new ChatRequest(List.of(ChatMessage.user(prompt)), temperature, maxTokens, true)).result();
            in += r.inputTokens();
            out += r.outputTokens();
            return r;
        }

        GenResult result(List<KpGen> kps) {
            return new GenResult(kps, kps.stream().mapToInt(KpGen::created).sum(), System.currentTimeMillis() - start,
                    calls, in, out, callLog.cost(in, out).doubleValue());
        }
    }

    // ------------------------------------------------------------------ AI 生成

    /** 一个知识点的出题计划：几道、每种题型几道、每种考查类型几道（程序排好，交给 AI 照着出） */
    public record Plan(int count, Map<String, Integer> types, Map<String, Integer> assess) {

        public String text(int points) {
            StringBuilder b = new StringBuilder("一共出 ").append(count).append(" 道。题型：");
            b.append(types.entrySet().stream().map(e -> e.getKey() + "（" + TYPE_NAMES.get(e.getKey()).split("（")[0]
                            + "）" + e.getValue() + " 道")
                    .collect(Collectors.joining("、")));
            if (!assess.isEmpty()) {
                b.append("；考查类型：").append(assess.entrySet().stream()
                        .map(e -> e.getKey() + "（" + assessName(e.getKey()) + "）" + e.getValue() + " 道")
                        .collect(Collectors.joining("、")));
            }
            if (points > 0) {
                b.append(count >= points ? "；" + points + " 条要点每条至少 1 道" : "；尽量考到不同的要点");
            }
            return b.append("。").toString();
        }
    }

    /**
     * 出题计划（整章一起排）：一般知识点 perKp 道，重点知识点多 1 道；
     * 题型、考查类型按整章轮流分配（上一个知识点排到哪种，下一个从下一种接着排），整章每种的数量相差不超过 1。
     */
    public static List<Plan> plan(List<ChapterKp> kps, int perKp, List<String> types, List<String> assess) {
        List<Plan> plans = new ArrayList<>();
        int t = 0;
        int a = 0;
        for (ChapterKp kp : kps) {
            int count = Math.min(MAX_PER_KP, perKp + ("重点".equals(kp.getImportance()) ? 1 : 0));
            Map<String, Integer> byType = new LinkedHashMap<>();
            Map<String, Integer> byAssess = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                byType.merge(types.get(t++ % types.size()), 1, Integer::sum);
                if (!assess.isEmpty()) {
                    byAssess.merge(assess.get(a++ % assess.size()), 1, Integer::sum);
                }
            }
            plans.add(new Plan(count, byType, byAssess));
        }
        return plans;
    }

    /** 出出来的题和计划对不上的地方（只提醒老师，不丢题）；老师写了其他要求时，多出来的不算，只提醒少了的 */
    static List<String> planGaps(Plan plan, List<ExerciseSaveRequest> got, boolean custom) {
        List<String> gaps = new ArrayList<>();
        Map<String, Long> byType = got.stream().collect(Collectors.groupingBy(ExerciseSaveRequest::type, Collectors.counting()));
        Map<String, Long> byAssess = got.stream().collect(Collectors.groupingBy(ExerciseSaveRequest::assessType,
                Collectors.counting()));
        plan.types().forEach((k, v) -> {
            long n = byType.getOrDefault(k, 0L);
            if (custom ? n < v : n != v) {
                gaps.add(TYPE_NAMES.get(k).split("（")[0] + " 计划 " + v + " 道，出了 " + n + " 道");
            }
        });
        plan.assess().forEach((k, v) -> {
            long n = byAssess.getOrDefault(k, 0L);
            if (custom ? n < v : n != v) {
                gaps.add(assessName(k) + " 计划 " + v + " 道，出了 " + n + " 道");
            }
        });
        return gaps;
    }

    public GenResult generate(GenRequest req) {
        ChapterItem item = requireExerciseItem(req.itemId());
        if (req.kpIds() == null || req.kpIds().isEmpty()) {
            throw BusinessException.badRequest("请至少选 1 个知识点");
        }
        String requirement = req.requirement() == null ? "" : req.requirement().strip();
        if (requirement.length() > MAX_REQUIREMENT) {
            throw BusinessException.badRequest("其他要求最多 " + MAX_REQUIREMENT + " 字");
        }
        List<String> assess = req.assessTypes() == null ? List.of() : req.assessTypes().stream()
                .filter(ASSESS_NAMES::containsKey).distinct().toList();
        List<String> types = req.types() == null ? List.of() : req.types().stream()
                .filter(TYPE_NAMES::containsKey).distinct().toList();
        if ((assess.isEmpty() && requirement.isEmpty()) || types.isEmpty()) {
            throw BusinessException.badRequest("请至少选 1 种考查类型（或写其他要求）和 1 种题型");
        }
        List<Long> order = chapterKpMapper.selectList(new LambdaQueryWrapper<ChapterKp>()
                        .eq(ChapterKp::getChapterId, item.getChapterId()).orderByAsc(ChapterKp::getSortOrder))
                .stream().map(ChapterKp::getId).toList();
        // 按本章顺序排计划，题型、考查类型在整章里轮流分配
        List<ChapterKp> chosen = new ArrayList<>();
        int perKp = req.perKp() == null ? 3 : Math.max(1, Math.min(6, req.perKp()));
        Course course = courseMapper.selectById(item.getCourseId());
        Map<String, String> pointer = feynmanDraft.pointers(item.getChapterId());
        Map<String, String> texts = recognizer.segments(item.getChapterId()).stream()
                .collect(Collectors.toMap(KpRecognizer.Seg::key, s -> s.text().getContent(), (a, b) -> a));
        Meter meter = new Meter();
        List<KpGen> out = new ArrayList<>();
        for (Long kpId : req.kpIds()) {
            ChapterKp kp = chapterKpMapper.selectById(kpId);
            if (kp == null || !kp.getChapterId().equals(item.getChapterId())) {
                throw BusinessException.badRequest("知识点不属于这一章");
            }
            if (kp.getKnowledgePointId() == null) {
                throw BusinessException.badRequest("知识点“" + kp.getName() + "”还没确认，先在“知识点”里确认");
            }
            chosen.add(kp);
        }
        chosen.sort(Comparator.comparingInt(k -> order.indexOf(k.getId())));
        List<Plan> plans = plan(chosen, perKp, types, assess);
        for (int i = 0; i < chosen.size(); i++) {
            ChapterKp kp = chosen.get(i);
            out.add(generateOne(meter, item, course.getName(), kp, order.indexOf(kp.getId()) + 1, assess, types,
                    plans.get(i), requirement, pointer, texts));
        }
        return meter.result(out);
    }

    // ------------------------------------------------------------------ 小助手的个人补练题

    /** 补练题只出程序能马上判的题型 */
    public static final List<String> REMEDY_TYPES = List.of(Grader.SINGLE, Grader.TWO_TIER, Grader.JUDGE, Grader.MULTIPLE);

    /**
     * 给一个学生当场生成补练题（docs/模块说明/小助手-自动干预与聊天.md 第四节）：不属于任何练习、不是草稿、不经过老师审核。
     * requirement 由小助手按这个学生的错题写好（考哪几条要点、用哪个误解、别和哪些题重复）。
     * AI 不看答案做一遍，做错的（可能有歧义）直接删掉；可用的少于 2 道就再生成一次。
     */
    public List<ExerciseView> generateRemedy(Long knowledgePointId, String requirement, int count, int round) {
        ChapterKp kp = chapterKpMapper.selectOne(new LambdaQueryWrapper<ChapterKp>()
                .eq(ChapterKp::getKnowledgePointId, knowledgePointId).last("LIMIT 1"));
        if (kp == null) {
            throw BusinessException.badRequest("这个知识点没有识别资料，没法生成补练题");
        }
        com.se.platform.course.entity.Chapter chapter = chapterMapperOf(kp.getChapterId());
        Course course = courseMapper.selectById(chapter.getCourseId());
        List<Long> order = chapterKpMapper.selectList(new LambdaQueryWrapper<ChapterKp>()
                        .eq(ChapterKp::getChapterId, kp.getChapterId()).orderByAsc(ChapterKp::getSortOrder))
                .stream().map(ChapterKp::getId).toList();
        Map<String, String> pointer = feynmanDraft.pointers(kp.getChapterId());
        Map<String, String> texts = recognizer.segments(kp.getChapterId()).stream()
                .collect(Collectors.toMap(KpRecognizer.Seg::key, x -> x.text().getContent(), (x, y) -> x));
        // 每一轮换着题型排，第 2 轮从第 2 种开始
        List<String> types = new ArrayList<>(REMEDY_TYPES);
        java.util.Collections.rotate(types, -(Math.max(1, round) - 1));
        List<String> assess = List.of("scenario", "concept");
        Plan plan = plan(List.of(kp), count, types.subList(0, Math.min(count, types.size())), assess).get(0);
        plan = new Plan(count, plan.types(), plan.assess());
        Meter meter = new Meter();
        List<ExerciseView> kept = new ArrayList<>();
        for (int attempt = 0; attempt < 2 && kept.size() < 2; attempt++) {
            JsonNode d = askQuestions(meter, course.getName(), kp, order.indexOf(kp.getId()) + 1, assess, types, plan,
                    requirement, pointer, texts);
            List<ExerciseView> made = new ArrayList<>();
            for (JsonNode q : d.path("questions")) {
                if (made.size() + kept.size() >= count) {
                    break;
                }
                ExerciseSaveRequest r = toRequest(q, kp.getChapterId(), null, List.of(knowledgePointId));
                if (!REMEDY_TYPES.contains(r.type())) {
                    continue;
                }
                try {
                    exerciseService.validate(r);
                } catch (BusinessException e) {
                    continue;
                }
                made.add(exerciseService.createRemedy(course.getId(), course.getTeacherId(), r));
            }
            for (ExerciseView e : made) {
                if (solveFails(meter, e)) {
                    exerciseMapper.deleteById(e.id()); // 有歧义的直接丢掉
                } else {
                    kept.add(e);
                }
            }
        }
        if (kept.isEmpty()) {
            throw BusinessException.badRequest("补练题生成失败，请稍后再试");
        }
        return kept;
    }

    private com.se.platform.course.entity.Chapter chapterMapperOf(Long chapterId) {
        return chapterService.require(chapterId);
    }

    /** AI 不看答案做这一道，做错了返回 true */
    private boolean solveFails(Meter meter, ExerciseView e) {
        return solveCheck(meter, List.of(e)) > 0;
    }

    /** 拼出题提示词、调 AI，返回 {"questions": [...]}；两次都失败抛错 */
    private JsonNode askQuestions(Meter meter, String course, ChapterKp kp, int kpNo, List<String> assess,
                                  List<String> types, Plan plan, String requirement, Map<String, String> pointer,
                                  Map<String, String> texts) {
        List<KeyPoint> points = readList(kp.getKeyPoints(), new TypeReference<List<KeyPoint>>() { });
        List<String> mis = readList(kp.getMisconceptions(), new TypeReference<List<String>>() { });
        StringBuilder p = new StringBuilder();
        StringBuilder material = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < points.size(); i++) {
            p.append(i + 1).append(". ").append(points.get(i).text()).append('\n');
            for (String src : points.get(i).sources()) {
                if (seen.add(src) && texts.containsKey(src)) {
                    material.append('[').append(pointer.getOrDefault(src, src)).append("] ")
                            .append(texts.get(src).replaceAll("\\s+", " ")).append('\n');
                }
            }
        }
        boolean custom = !requirement.isEmpty();
        List<String> assessLines = new ArrayList<>(assess.stream().map(a -> "- " + a + "：" + ASSESS_NAMES.get(a)).toList());
        if (custom) {
            assessLines.add("- " + Grader.CUSTOM + "：老师在【老师的其他要求】里提出的、不属于上面几种的考查方式（老师没提这类要求就不用，不算在“每种至少 1 道”里）");
        }
        String prompt = genTemplate.render(Map.ofEntries(Map.entry("course", course), Map.entry("name", kp.getName()),
                Map.entry("kp_no", String.valueOf(kpNo)),
                Map.entry("requirement", custom ? requirement : "（无）"),
                Map.entry("summary", kp.getSummary() == null ? "" : kp.getSummary()), Map.entry("points", p.toString()),
                Map.entry("misconceptions", mis.isEmpty() ? "（无）" : mis.stream().map(m -> "- " + m)
                        .collect(Collectors.joining("\n"))),
                Map.entry("material", material.length() > 6000 ? material.substring(0, 6000) : material.toString()),
                Map.entry("assess", String.join("\n", assessLines)),
                Map.entry("types", types.stream().map(t -> "- " + t + "：" + TYPE_NAMES.get(t))
                        .collect(Collectors.joining("\n"))),
                Map.entry("plan", plan.text(points.size()))));
        JsonNode d = null;
        for (int attempt = 0; attempt < 2 && d == null; attempt++) {
            try {
                d = json.readTree(meter.call(AiCallLogService.TASK_EXERCISE_GEN, genTemplate, prompt, 0.6, 8192)
                        .content());
                if (!d.path("questions").isArray()) {
                    d = null;
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                d = null;
            }
        }
        if (d == null) {
            throw BusinessException.badRequest("AI 出题失败（" + kp.getName() + "），请再试一次");
        }
        return d;
    }

    private KpGen generateOne(Meter meter, ChapterItem item, String course, ChapterKp kp, int kpNo,
                              List<String> assess, List<String> types, Plan plan, String requirement,
                              Map<String, String> pointer, Map<String, String> texts) {
        List<KeyPoint> points = readList(kp.getKeyPoints(), new TypeReference<List<KeyPoint>>() { });
        List<String> mis = readList(kp.getMisconceptions(), new TypeReference<List<String>>() { });
        boolean custom = !requirement.isEmpty();
        JsonNode d = askQuestions(meter, course, kp, kpNo, assess, types, plan, requirement, pointer, texts);
        List<String> notes = new ArrayList<>();
        List<ExerciseView> created = new ArrayList<>();
        int dropped = 0;
        Set<Integer> coveredPoints = new LinkedHashSet<>();
        Set<String> usedMis = new LinkedHashSet<>();
        List<ExerciseSaveRequest> saved = new ArrayList<>();
        for (JsonNode q : d.path("questions")) {
            ExerciseSaveRequest r = toRequest(q, item, List.of(kp.getKnowledgePointId()));
            if (created.size() >= MAX_PER_KP) {
                dropped++;
                notes.add("丢掉 1 道：超过每个知识点最多 " + MAX_PER_KP + " 道");
                continue;
            }
            if (custom && !types.contains(r.type()) && TYPE_NAMES.containsKey(r.type())) {
                notes.add("按老师的其他要求用了题型：" + TYPE_NAMES.get(r.type()).split("（")[0]);
            } else if (!types.contains(r.type())) {
                dropped++;
                notes.add("丢掉 1 道：题型不是老师选的（" + r.type() + "）");
                continue;
            }
            try {
                exerciseService.validate(r);
            } catch (BusinessException e) {
                dropped++;
                notes.add("丢掉 1 道：" + e.getMessage());
                continue;
            }
            saved.add(r);
            String check = copyCheck(r, points);
            created.add(exerciseService.createDraft(item.getId(), r, "ai", check));
            if (r.keyPointNo() != null) {
                coveredPoints.add(r.keyPointNo());
            }
            for (String m : mis) {
                if (mentions(r, m)) {
                    usedMis.add(m);
                }
            }
        }
        String reqNote = d.path("requirement_note").asText("").strip();
        if (custom && !reqNote.isEmpty()) {
            notes.add(0, "AI 说明怎么照做其他要求：" + reqNote);
        }
        List<String> gaps = planGaps(plan, saved, custom);
        if (!gaps.isEmpty()) {
            notes.add("和出题计划不一致：" + String.join("；", gaps));
        }
        int flagged = solveCheck(meter, created);
        if (coveredPoints.size() < points.size()) {
            List<Integer> miss = new ArrayList<>();
            for (int i = 1; i <= points.size(); i++) {
                if (!coveredPoints.contains(i)) {
                    miss.add(i);
                }
            }
            notes.add("没考到的要点：" + miss.stream().map(String::valueOf).collect(Collectors.joining("、")));
        }
        return new KpGen(kp.getId(), kp.getName(), created.size(), dropped,
                coveredPoints.size() + "/" + points.size(), usedMis.size() + "/" + mis.size(), flagged, notes);
    }

    private ExerciseSaveRequest toRequest(JsonNode q, ChapterItem item, List<Long> kpIds) {
        return toRequest(q, item.getChapterId(), item.getId(), kpIds);
    }

    /** 模型的一道题 → 保存请求（字段名和数据库一致，见 exercise_gen_v1 的输出格式） */
    private ExerciseSaveRequest toRequest(JsonNode q, Long chapterId, Long itemId, List<Long> kpIds) {
        Integer no = q.path("key_point_no").isInt() ? q.path("key_point_no").asInt() : null;
        Integer diff = q.path("difficulty").isInt() ? Math.max(1, Math.min(3, q.path("difficulty").asInt())) : 2;
        return new ExerciseSaveRequest(chapterId, itemId, q.path("type").asText(Grader.SINGLE),
                q.path("assess_type").asText("concept"), no, q.path("stem").asText(""), strings(q.path("options")),
                q.path("answer").asText(""), q.path("analysis").asText(null), strings(q.path("option_notes")),
                strings(q.path("reasons")), q.path("reason_answer").asText(null), strings(q.path("reason_notes")),
                strings(q.path("accepts")), q.path("reference").asText(null), strings(q.path("key_points")), diff,
                kpIds);
    }

    /** 题干或选项里有连续 15 个字和要点原文一样，提醒老师“照抄了原文”（背原句就能答对） */
    static String copyCheck(ExerciseSaveRequest r, List<KeyPoint> points) {
        String body = Grader.normalize(r.stem() + String.join("", r.options() == null ? List.of() : r.options()));
        for (int i = 0; i < points.size(); i++) {
            String t = Grader.normalize(points.get(i).text());
            for (int s = 0; s + 15 <= t.length(); s++) {
                if (body.contains(t.substring(s, s + 15))) {
                    return "题干或选项照抄了要点 " + (i + 1) + " 的原文，学生背原句就能答对";
                }
            }
        }
        return null;
    }

    private static boolean mentions(ExerciseSaveRequest r, String misconception) {
        String m = Grader.normalize(misconception);
        List<String> notes = new ArrayList<>();
        if (r.optionNotes() != null) {
            notes.addAll(r.optionNotes());
        }
        if (r.reasonNotes() != null) {
            notes.addAll(r.reasonNotes());
        }
        return notes.stream().map(Grader::normalize).anyMatch(n -> !n.isEmpty() && (n.contains(m) || m.contains(n)));
    }

    /**
     * 不看答案做一遍：把客观题（不含答案）交给模型作答，选的和标准答案不一样的，标为“可能有歧义”。
     * 返回标出来的题数。这一步失败不影响出题，只是少了这项检查。
     */
    int solveCheck(Meter meter, List<ExerciseView> created) {
        List<ExerciseView> objective = created.stream().filter(e -> !Grader.SHORT.equals(e.type())).toList();
        if (objective.isEmpty()) {
            return 0;
        }
        StringBuilder qs = new StringBuilder();
        for (int i = 0; i < objective.size(); i++) {
            ExerciseView e = objective.get(i);
            qs.append("第 ").append(i + 1).append(" 题（").append(TYPE_NAMES.get(e.type())).append("）")
                    .append(e.stem()).append('\n');
            for (int j = 0; j < e.options().size(); j++) {
                qs.append("  ").append((char) ('A' + j)).append(". ").append(e.options().get(j)).append('\n');
            }
            for (int j = 0; j < e.reasons().size(); j++) {
                qs.append("  理由").append((char) ('A' + j)).append(". ").append(e.reasons().get(j)).append('\n');
            }
        }
        try {
            JsonNode d = json.readTree(meter.call(AiCallLogService.TASK_EXERCISE_SOLVE, solveTemplate,
                    solveTemplate.render(Map.of("questions", qs.toString())), 0.0, 2048).content());
            JsonNode answers = d.path("answers");
            int flagged = 0;
            for (int i = 0; i < objective.size() && i < answers.size(); i++) {
                ExerciseView e = objective.get(i);
                String a = answers.get(i).asText("");
                if (!Grader.grade(e, a).correct()) {
                    flagged++;
                    Exercise row = exerciseMapper.selectById(e.id());
                    String note = "AI 不看答案做这道题，答的是“" + a + "”，和标准答案不一样，可能有歧义，请检查";
                    row.setCheckNote(row.getCheckNote() == null ? note : row.getCheckNote() + "；" + note);
                    exerciseMapper.updateById(row);
                }
            }
            return flagged;
        } catch (Exception ex) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ 上传识别

    /** 老师上传已有题目：文件（.txt / .md / .docx）或粘贴的文字 → 识别成草稿题 */
    public GenResult importQuestions(Long itemId, MultipartFile file, String pasted) {
        ChapterItem item = requireExerciseItem(itemId);
        String text = pasted == null ? "" : pasted.strip();
        if (file != null && !file.isEmpty()) {
            text = extractText(file);
        }
        if (text.isBlank()) {
            throw BusinessException.badRequest("没有读到题目：请上传 .txt / .md / .docx 文件，或者把题目粘贴进来");
        }
        if (text.length() > 20000) {
            throw BusinessException.badRequest("一次最多 2 万字，请分几次上传");
        }
        List<KnowledgePoint> kps = kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getChapterId, item.getChapterId()).orderByAsc(KnowledgePoint::getId));
        if (kps.isEmpty()) {
            throw BusinessException.badRequest("这一章还没有知识点：题目要关联知识点，先去确认知识点");
        }
        StringBuilder kpList = new StringBuilder();
        for (int i = 0; i < kps.size(); i++) {
            kpList.append(i + 1).append(". ").append(kps.get(i).getName()).append('\n');
        }
        Meter meter = new Meter();
        JsonNode d;
        try {
            d = json.readTree(meter.call(AiCallLogService.TASK_EXERCISE_IMPORT, importTemplate,
                    importTemplate.render(Map.of("kps", kpList.toString(), "text", text)), 0.1, 8192).content());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.badRequest("识别失败，请再试一次");
        }
        int created = 0;
        int dropped = 0;
        List<String> notes = new ArrayList<>();
        for (JsonNode q : d.path("questions")) {
            int k = q.path("kp_no").asInt(0);
            if (k < 1 || k > kps.size()) {
                dropped++;
                notes.add("丢掉 1 道：没认出属于哪个知识点（" + abbrev(q.path("stem").asText()) + "）");
                continue;
            }
            ExerciseSaveRequest r = toRequest(q, item, List.of(kps.get(k - 1).getId()));
            try {
                exerciseService.validate(r);
            } catch (BusinessException e) {
                dropped++;
                notes.add("丢掉 1 道：" + e.getMessage() + "（" + abbrev(r.stem()) + "）");
                continue;
            }
            String why = q.path("why").asText("");
            String check = "识别为“" + kps.get(k - 1).getName() + "”、" + assessName(r.assessType())
                    + (why.isBlank() ? "" : "，理由：" + why);
            if (!q.path("confident").asBoolean(true)) {
                check = "识别没把握，请核对：" + check;
            }
            exerciseService.createDraft(item.getId(), r, "upload", check);
            created++;
        }
        return meter.result(List.of(new KpGen(null, "上传的题目", created, dropped, "", "", 0, notes)));
    }

    static String assessName(String a) {
        return switch (a == null ? "concept" : a) {
            case "scenario" -> "情境应用";
            case "data" -> "数据分析";
            case "process" -> "流程步骤";
            case "custom" -> "老师定制";
            default -> "概念辨析";
        };
    }

    /** .docx 是一个 zip，正文在 word/document.xml 里：段落换行，去掉标签。不用另外装库 */
    static String extractText(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            byte[] bytes = file.getBytes();
            if (name.endsWith(".docx")) {
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                    for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                        if ("word/document.xml".equals(e.getName())) {
                            String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                            return xml.replaceAll("</w:p>", "\n").replaceAll("<w:tab/>", "\t")
                                    .replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">")
                                    .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&").strip();
                        }
                    }
                }
                throw BusinessException.badRequest("读不出这个 Word 文件的内容");
            }
            if (name.endsWith(".txt") || name.endsWith(".md")) {
                return new String(bytes, StandardCharsets.UTF_8).strip();
            }
            throw BusinessException.badRequest("只支持 .txt、.md、.docx 文件（PDF、Excel 可以先把题目复制出来粘贴）");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.badRequest("读文件失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 工具

    private ChapterItem requireExerciseItem(Long itemId) {
        ChapterItem item = itemId == null ? null : itemMapper.selectById(itemId);
        if (item == null) {
            throw BusinessException.notFound("内容");
        }
        chapterService.requireOwned(item.getChapterId());
        if (!"exercise".equals(item.getType())) {
            throw BusinessException.badRequest("这一项不是练习");
        }
        return item;
    }

    private List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(x -> out.add(x.asText("")));
        }
        return out;
    }

    private <T> List<T> readList(String text, TypeReference<List<T>> type) {
        try {
            return text == null || text.isBlank() ? List.of() : json.readValue(text, type);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String abbrev(String s) {
        return s == null ? "" : s.length() > 20 ? s.substring(0, 20) + "…" : s;
    }
}
