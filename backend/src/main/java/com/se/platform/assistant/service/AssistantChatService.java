package com.se.platform.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.assistant.dto.AssistantDtos.ChatReply;
import com.se.platform.assistant.dto.AssistantDtos.ChatLine;
import com.se.platform.assistant.entity.AssistantMessage;
import com.se.platform.assistant.mapper.AssistantMessageMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.Grader;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.feynman.service.FeynmanDraftService;
import com.se.platform.material.dto.KpDtos.KeyPoint;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.service.KpRecognizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 小助手的自由聊天（docs/模块说明/小助手-自动干预与聊天.md 第二、三节）。
 * <ul>
 *   <li>两个阶段：这一章的练习还没交卷 = 学习中（不出任何题）；交过卷 = 练习已交卷（加上学生自己的弱项）</li>
 *   <li>每次把固定规则（assistant_chat_v1）+ 本章知识点 + 最相关的资料 + 最近几句交给大模型；不给题目答案</li>
 *   <li>程序把关：学生在问具体的题 → 提示词加一句只能引导；回复里出了题（阶段一）或者有这道题的答案 → 不发，换成固定的话</li>
 * </ul>
 */
@Service
public class AssistantChatService {

    static final int MAX_MESSAGE = 500;
    static final int HISTORY = 8;
    static final int MATERIAL_CHARS = 3000;
    static final int SEG_CHARS = 800;
    /** 连续多少个字一样算“贴了题目”或“照抄了参考答案” */
    static final int WINDOW = 15;

    static final String STAGE_LEARNING = "学习中（本章练习还没交卷）：不出任何题目，也不用选择题的形式举例；学生要练习题，就告诉他先完成本章的 AI 对话和练习，之后会根据他的情况给他补练题。";
    static final String STAGE_SUBMITTED = "练习已交卷：可以告诉他哪些知识点还不熟、错在哪条要点；补练题由系统给出（在小助手的“做补练题”里），你不要自己出题，只负责解释和引导。";

    static final String BLOCK_QUESTION = "现在还在学习阶段，我先不出题。等你完成本章的 AI 对话和练习，我会根据你的情况给你补练题。有不懂的概念可以直接问我。";

    private static final Pattern ASKS_ANSWER = Pattern.compile(
            "选哪个|选什么|答案是|答案|正确选项|对还是错|对不对|第\\s*\\d+\\s*题|这道题|这题|帮我写|替我写");
    private static final Pattern OPTION_LINE = Pattern.compile("(?m)^\\s*[A-DＡ-Ｄ]\\s*[\\.．、)）:：]");
    private static final Pattern QUIZ_WORDS = Pattern.compile("考考你|请回答|请作答|选择题：|判断题：|填空题：");
    private static final Pattern SAYS_LETTER = Pattern.compile(
            "(选|答案是|答案为|正确答案|正确的是|应该是)\\s*[“\"「]?\\s*[A-F](?![A-Za-z])");
    private static final Pattern SAYS_JUDGE = Pattern.compile("(这句话|这个说法|说法|这道题)[^。！？\\n]{0,6}(是)?(对|错|正确|错误)的");

    private final AssistantMessageMapper messageMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final ChapterKpMapper chapterKpMapper;
    private final KnowledgePointMapper kpMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final MasteryMapper masteryMapper;
    private final KpRecognizer recognizer;
    private final FeynmanDraftService feynmanDraft;
    private final RemedyService remedyService;
    private final PracticeAnalysis practiceAnalysis;
    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate template = PromptTemplate.load("assistant_chat_v1");

    public AssistantChatService(AssistantMessageMapper messageMapper, ChapterMapper chapterMapper,
                                CourseMapper courseMapper, ChapterKpMapper chapterKpMapper,
                                KnowledgePointMapper kpMapper, ExerciseMapper exerciseMapper,
                                ExerciseKnowledgePointMapper linkMapper, MasteryMapper masteryMapper,
                                KpRecognizer recognizer, FeynmanDraftService feynmanDraft,
                                RemedyService remedyService, PracticeAnalysis practiceAnalysis,
                                AiCallLogService callLog, ObjectMapper json) {
        this.messageMapper = messageMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.chapterKpMapper = chapterKpMapper;
        this.kpMapper = kpMapper;
        this.exerciseMapper = exerciseMapper;
        this.linkMapper = linkMapper;
        this.masteryMapper = masteryMapper;
        this.recognizer = recognizer;
        this.feynmanDraft = feynmanDraft;
        this.remedyService = remedyService;
        this.practiceAnalysis = practiceAnalysis;
        this.callLog = callLog;
        this.json = json;
    }

    /** 这一章的聊天记录（从早到晚） */
    public List<ChatLine> history(long studentId, Long chapterId) {
        return messages(studentId, chapterId, 200).stream()
                .map(m -> new ChatLine(m.getRole(), m.getContent(), m.getCreatedAt())).toList();
    }

    public ChatReply chat(long studentId, Long chapterId, String text) {
        String msg = text == null ? "" : text.strip();
        if (msg.isEmpty()) {
            throw BusinessException.badRequest("说点什么吧");
        }
        if (msg.length() > MAX_MESSAGE) {
            throw BusinessException.badRequest("一次最多 " + MAX_MESSAGE + " 字");
        }
        Chapter ch = chapterMapper.selectById(chapterId);
        if (ch == null || !"published".equals(ch.getStatus())) {
            throw BusinessException.notFound("章节");
        }
        Course course = courseMapper.selectById(ch.getCourseId());
        boolean submitted = remedyService.submitted(studentId, chapterId);
        List<ChapterKp> kps = chapterKpMapper.selectList(new LambdaQueryWrapper<ChapterKp>()
                        .eq(ChapterKp::getChapterId, chapterId).isNotNull(ChapterKp::getKnowledgePointId)
                        .orderByAsc(ChapterKp::getSortOrder));
        List<Exercise> exercises = exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>()
                .eq(Exercise::getChapterId, chapterId).eq(Exercise::getDraft, false));
        Exercise asked = askedQuestion(msg, exercises);
        boolean asksAnswer = asked != null || ASKS_ANSWER.matcher(msg).find();

        List<AssistantMessage> recent = messages(studentId, chapterId, HISTORY);
        save(studentId, chapterId, AssistantMessage.STUDENT, msg, null, null, null);

        Map<String, String> pointer = safePointers(chapterId);
        String prompt = template.render(Map.of(
                "course", course == null ? "" : course.getName(),
                "chapter", ch.getTitle(),
                "stage", submitted ? STAGE_SUBMITTED : STAGE_LEARNING,
                "kps", kpText(kps, pointer),
                "material", material(chapterId, msg, pointer),
                "student", submitted ? studentText(studentId, kps) : "（还没交卷，先不看）",
                "guard", asksAnswer ? "\n【注意】学生这一句是在问一道具体的题（或者要答案、要你代写）。只能说考的是哪个知识点、去看资料哪里，再用一个问题引导他；不说选项、不说对错结论、不代写。" : ""));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompt));
        for (AssistantMessage m : recent) {
            messages.add(new ChatMessage(AssistantMessage.STUDENT.equals(m.getRole()) ? "user" : "assistant",
                    m.getContent()));
        }
        messages.add(ChatMessage.user(msg));
        AiCallLogService.Logged logged = callLog.call(AiCallLogService.TASK_ASSISTANT_CHAT, template.name(),
                new ChatRequest(messages, 0.5, 600, false));
        String reply = logged.result().content() == null ? "" : logged.result().content().strip();

        String blocked = null;
        String finalText = reply;
        if (reply.isEmpty()) {
            finalText = "抱歉，我刚才没想好，你能再说一遍吗？";
        } else if (!submitted && looksLikeQuestion(reply)) {
            blocked = "question";
            finalText = BLOCK_QUESTION;
        } else if (leaks(reply, asked, exercises)) {
            blocked = "answer";
            finalText = hint(asked, pointer);
        }
        save(studentId, chapterId, AssistantMessage.ASSISTANT, finalText, blocked, blocked == null ? null : reply,
                logged.logId());
        return new ChatReply(finalText, submitted ? "submitted" : "learning", blocked != null,
                logged.result().latencyMs(), callLog.cost(logged.result().inputTokens(),
                logged.result().outputTokens()).doubleValue());
    }

    // ------------------------------------------------------------------ 程序把关

    /** 学生这一句是不是贴了本章某道题：和题干有连续 15 个字一样 */
    static Exercise askedQuestion(String msg, List<Exercise> exercises) {
        String m = squash(msg);
        for (Exercise e : exercises) {
            String stem = squash(e.getStem());
            int w = Math.min(WINDOW, stem.length());
            for (int i = 0; w >= 8 && i + w <= stem.length(); i++) {
                if (m.contains(stem.substring(i, i + w))) {
                    return e;
                }
            }
        }
        return null;
    }

    /** 阶段一：回复里出了题（两行以上“A. …”选项，或“考考你”这类话） */
    static boolean looksLikeQuestion(String reply) {
        long options = OPTION_LINE.matcher(reply).results().count();
        return options >= 2 || QUIZ_WORDS.matcher(reply).find();
    }

    /**
     * 回复里有没有答案：
     * 学生在问某道题时 → 这道题的正确选项原文（4 个字以上）、填空的标准答案、正确理由、“选 A”“答案是”、判断题说“是对的/错的”；
     * 任何时候 → 和本章任何一道简答的参考答案有连续 15 个字一样。
     */
    static boolean leaks(String reply, Exercise asked, List<Exercise> exercises) {
        String r = squash(reply);
        if (asked != null) {
            if (SAYS_LETTER.matcher(reply).find()) {
                return true;
            }
            if (Grader.JUDGE.equals(asked.getType()) && SAYS_JUDGE.matcher(reply).find()) {
                return true;
            }
            for (String a : correctTexts(asked)) {
                String s = squash(a);
                if (s.length() >= 4 && r.contains(s) || Grader.FILL.equals(asked.getType()) && s.length() >= 2
                        && r.contains(s)) {
                    return true;
                }
            }
        }
        for (Exercise e : exercises) {
            if (Grader.SHORT.equals(e.getType()) && e.getReference() != null) {
                String ref = squash(e.getReference());
                for (int i = 0; i + WINDOW <= ref.length(); i++) {
                    if (r.contains(ref.substring(i, i + WINDOW))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 一道题的“答案文字”：正确选项原文、两段式的正确理由、填空的标准说法 */
    static List<String> correctTexts(Exercise e) {
        List<String> out = new ArrayList<>();
        List<String> options = list(e.getOptions());
        String ans = e.getAnswer() == null ? "" : e.getAnswer();
        if (Grader.FILL.equals(e.getType())) {
            out.addAll(list(e.getAccepts()));
            return out;
        }
        if (Grader.JUDGE.equals(e.getType())) {
            return out; // 判断题只有“正确 / 错误”，看 SAYS_JUDGE
        }
        for (char c : ans.toCharArray()) {
            int i = c - 'A';
            if (i >= 0 && i < options.size()) {
                out.add(options.get(i));
            }
        }
        if (Grader.TWO_TIER.equals(e.getType()) && e.getReasonAnswer() != null) {
            List<String> reasons = list(e.getReasons());
            int i = e.getReasonAnswer().isEmpty() ? -1 : e.getReasonAnswer().charAt(0) - 'A';
            if (i >= 0 && i < reasons.size()) {
                out.add(reasons.get(i));
            }
        }
        return out;
    }

    /** 拦下后的固定回复：说考的是哪个知识点、去看哪里 */
    private String hint(Exercise asked, Map<String, String> pointer) {
        String kpName = null;
        String where = null;
        if (asked != null) {
            Long kpId = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                            .eq(ExerciseKnowledgePoint::getExerciseId, asked.getId()))
                    .stream().map(ExerciseKnowledgePoint::getKnowledgePointId).findFirst().orElse(null);
            KnowledgePoint kp = kpId == null ? null : kpMapper.selectById(kpId);
            kpName = kp == null ? null : kp.getName();
            ChapterKp ckp = kpId == null ? null : chapterKpMapper.selectOne(new LambdaQueryWrapper<ChapterKp>()
                    .eq(ChapterKp::getKnowledgePointId, kpId).last("LIMIT 1"));
            if (ckp != null) {
                List<KeyPoint> points = keyPoints(ckp);
                Integer no = asked.getKeyPointNo();
                KeyPoint p = no != null && no >= 1 && no <= points.size() ? points.get(no - 1)
                        : points.isEmpty() ? null : points.get(0);
                if (p != null && !p.sources().isEmpty()) {
                    where = pointer.get(p.sources().get(0));
                }
            }
        }
        return "这道题我不能直接告诉你答案。" + (kpName == null ? "" : "它考的是「" + kpName + "」，")
                + (where == null ? "可以先回去看一下这部分的资料，" : "可以先看" + where + "，")
                + "看完说说你是怎么想的，我再帮你理一理。";
    }

    // ------------------------------------------------------------------ 拼提示词

    private String kpText(List<ChapterKp> kps, Map<String, String> pointer) {
        StringBuilder b = new StringBuilder();
        for (ChapterKp k : kps) {
            b.append("- ").append(k.getName()).append('\n');
            List<KeyPoint> points = keyPoints(k);
            for (int i = 0; i < points.size(); i++) {
                KeyPoint p = points.get(i);
                b.append("  要点 ").append(i + 1).append("：").append(p.text());
                if (!p.sources().isEmpty() && pointer.containsKey(p.sources().get(0))) {
                    b.append(" [").append(pointer.get(p.sources().get(0))).append(']');
                }
                b.append('\n');
            }
            List<String> mis = read(k.getMisconceptions(), new TypeReference<List<String>>() { });
            if (!mis.isEmpty()) {
                b.append("  常见误解：").append(String.join("；", mis)).append('\n');
            }
        }
        return b.isEmpty() ? "（这一章还没有确认知识点）" : b.toString();
    }

    /** 和学生问题最相关的几段资料：按共有的两字词打分，取前几段，总共不超过 3000 字 */
    private String material(Long chapterId, String msg, Map<String, String> pointer) {
        List<KpRecognizer.Seg> segs;
        try {
            segs = recognizer.segments(chapterId);
        } catch (Exception e) {
            return "（没有资料文字）";
        }
        Set<String> grams = grams(msg);
        List<KpRecognizer.Seg> ranked = segs.stream()
                .filter(s -> s.text().getContent() != null && !s.text().getContent().isBlank())
                .sorted(Comparator.comparingInt((KpRecognizer.Seg s) -> -overlap(grams, s.text().getContent())))
                .toList();
        StringBuilder b = new StringBuilder();
        for (KpRecognizer.Seg s : ranked) {
            if (b.length() >= MATERIAL_CHARS || overlap(grams, s.text().getContent()) == 0 && !b.isEmpty()) {
                break;
            }
            String t = s.text().getContent().replaceAll("\\s+", " ");
            b.append('[').append(pointer.getOrDefault(s.key(), s.key())).append("] ")
                    .append(t.length() > SEG_CHARS ? t.substring(0, SEG_CHARS) + "…" : t).append('\n');
        }
        return b.isEmpty() ? "（没有资料文字）" : b.toString();
    }

    /** 阶段二：这个学生哪些知识点还不熟、错在哪里（只用他自己的数据） */
    private String studentText(long studentId, List<ChapterKp> kps) {
        PracticeAnalysis.Context ctx = practiceAnalysis.context(kps.isEmpty() ? null : kps.get(0).getChapterId());
        List<String> lines = new ArrayList<>();
        for (ChapterKp k : kps) {
            Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                    .eq(Mastery::getStudentId, studentId).eq(Mastery::getKnowledgePointId, k.getKnowledgePointId()));
            String level = MasteryService.level(m == null ? null : m.getMastery());
            if (!"weak".equals(level) && !"medium".equals(level)) {
                continue;
            }
            KnowledgePoint kp = kpMapper.selectById(k.getKnowledgePointId());
            List<String> details = kp == null ? List.of() : practiceAnalysis.analyse(studentId, kp, ctx, false, false)
                    .details();
            lines.add("「" + k.getName() + "」" + ("weak".equals(level) ? "还不熟" : "还差一点")
                    + (details.isEmpty() ? "" : "（" + String.join("；", details) + "）"));
        }
        return lines.isEmpty() ? "本章的知识点都掌握得不错。" : String.join("\n", lines);
    }

    // ------------------------------------------------------------------ 小工具

    private Map<String, String> safePointers(Long chapterId) {
        try {
            return feynmanDraft.pointers(chapterId);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<AssistantMessage> messages(long studentId, Long chapterId, int limit) {
        List<AssistantMessage> list = messageMapper.selectList(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getStudentId, studentId).eq(AssistantMessage::getChapterId, chapterId)
                .orderByDesc(AssistantMessage::getId).last("LIMIT " + limit));
        List<AssistantMessage> out = new ArrayList<>(list);
        java.util.Collections.reverse(out);
        return out;
    }

    private void save(long studentId, Long chapterId, String role, String content, String blocked, String original,
                      Long callId) {
        AssistantMessage m = new AssistantMessage();
        m.setStudentId(studentId);
        m.setChapterId(chapterId);
        m.setRole(role);
        m.setContent(content);
        m.setBlocked(blocked);
        m.setOriginal(original);
        m.setAiCallId(callId);
        messageMapper.insert(m);
    }

    private List<KeyPoint> keyPoints(ChapterKp k) {
        return read(k.getKeyPoints(), new TypeReference<List<KeyPoint>>() { });
    }

    private <T> List<T> read(String text, TypeReference<List<T>> type) {
        try {
            return text == null || text.isBlank() ? List.of() : json.readValue(text, type);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static final ObjectMapper PLAIN = new ObjectMapper();

    static List<String> list(String jsonArray) {
        try {
            return jsonArray == null || jsonArray.isBlank() ? List.of()
                    : PLAIN.readValue(jsonArray, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 比较前去掉空白和标点 */
    static String squash(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");
    }

    private static Set<String> grams(String s) {
        String t = squash(s);
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 2 <= t.length(); i++) {
            out.add(t.substring(i, i + 2));
        }
        return out;
    }

    private static int overlap(Set<String> grams, String text) {
        String t = squash(text);
        int n = 0;
        for (String g : grams) {
            if (t.contains(g)) {
                n++;
            }
        }
        return n;
    }

    static <T> List<T> distinct(List<T> list) {
        return list.stream().distinct().collect(Collectors.toList());
    }
}
