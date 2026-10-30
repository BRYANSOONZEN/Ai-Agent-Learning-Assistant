package com.se.platform.dialogue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.ai.client.AiCallException;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.client.ChatResult;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.common.BusinessException;
import com.se.platform.dialogue.dto.DialogueOpening;
import com.se.platform.dialogue.dto.DialogueView;
import com.se.platform.dialogue.dto.TopicView;
import com.se.platform.dialogue.entity.DialogueMessage;
import com.se.platform.dialogue.entity.DialogueSession;
import com.se.platform.dialogue.entity.DialogueTopicState;
import com.se.platform.dialogue.mapper.DialogueMessageMapper;
import com.se.platform.dialogue.mapper.DialogueSessionMapper;
import com.se.platform.dialogue.mapper.DialogueTopicStateMapper;
import com.se.platform.dialogue.model.JudgedType;
import com.se.platform.dialogue.model.Judgement;
import com.se.platform.dialogue.model.NextAction;
import com.se.platform.dialogue.model.TopicResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生 AI 对话的总流程（docs/ai-implementation.md 第 8 步）。
 *
 * <p>一轮的顺序：存学生消息 → 拼提示词 → 调模型 → 解析 → 程序重新数要点 → 规则决定下一步 → 生成要显示的消息。
 * 模型只负责"判断 + 写一句提示"；讲解、换话题、总结都用老师确认过的文字或程序生成。
 */
@Slf4j
@Service
public class DialogueService {

    /** 登录还没做，先固定一个演示学生（data.sql 里的"学生01"） */
    public static final long DEMO_STUDENT_ID = 5L;
    /** 回复超过这个长度就不用模型的，改用预写提示 */
    private static final int MAX_REPLY_CHARS = 120;
    private static final int RECENT_MESSAGES = 6;

    private final DialogueTopicService topicService;
    private final DialogueSessionMapper sessionMapper;
    private final DialogueTopicStateMapper stateMapper;
    private final DialogueMessageMapper messageMapper;
    private final AiCallLogService callLog;
    private final JudgementParser parser;
    private final DialogueRules rules;
    private final PromptTemplate turnTemplate = PromptTemplate.load("dialogue_turn_v1");

    public DialogueService(DialogueTopicService topicService, DialogueSessionMapper sessionMapper,
                           DialogueTopicStateMapper stateMapper, DialogueMessageMapper messageMapper,
                           AiCallLogService callLog, JudgementParser parser, DialogueRules rules) {
        this.topicService = topicService;
        this.sessionMapper = sessionMapper;
        this.stateMapper = stateMapper;
        this.messageMapper = messageMapper;
        this.callLog = callLog;
        this.parser = parser;
        this.rules = rules;
    }

    // ===================== 对外的几个操作 =====================

    /** 开场页：本章（或本章某个 AI 对话项）有哪些话题。itemId 为空 = 整章 */
    public DialogueOpening opening(Long chapterId, Long itemId) {
        List<String> titles = topicService.listEnabled(chapterId, itemId).stream().map(TopicView::title).toList();
        return new DialogueOpening(chapterId, titles);
    }

    /**
     * 开始一次对话：建 session，每个话题建一条进度，AI 说出第一个问题。
     * {@code @Transactional}：方法里的多次写库要么全部成功，要么全部撤销，不会只写一半。
     */
    @Transactional
    public DialogueView start(Long chapterId, Long itemId) {
        List<TopicView> topics = topicService.listEnabled(chapterId, itemId);
        if (topics.isEmpty()) {
            throw BusinessException.badRequest("这一章还没有对话话题，请老师先添加");
        }
        DialogueSession session = new DialogueSession();
        session.setStudentId(DEMO_STUDENT_ID);
        session.setChapterId(chapterId);
        session.setItemId(itemId);
        session.setKind("CHAPTER");
        session.setStatus(DialogueSession.OPEN);
        session.setCurrentIdx(0);
        session.setIrrelevantTopicsInRow(0);
        sessionMapper.insert(session);

        for (int i = 0; i < topics.size(); i++) {
            DialogueTopicState s = new DialogueTopicState();
            s.setSessionId(session.getId());
            s.setTopicIdx(i);
            s.setTopicId(topics.get(i).id());
            s.setWrongCount(0);
            s.setStuckCount(0);
            s.setIrrelevantCount(0);
            s.setHintLevel(-1);
            stateMapper.insert(s);
        }
        TopicView first = topics.get(0);
        aiSay(session, 0, "我们开始吧。先来讨论「" + first.title() + "」。\n" + first.question(), "RULE");
        return view(session.getId());
    }

    /** 学生发一句话 */
    @Transactional
    public DialogueView send(Long sessionId, String text) {
        DialogueSession session = requireOpen(sessionId);
        DialogueTopicState state = currentState(session);
        TopicView topic = topicService.get(state.getTopicId());

        DialogueMessage studentMsg = save(session.getId(), state.getTopicIdx(), "STUDENT", text.trim(), null);

        // 1. 调模型；失败（超时、报错、格式不对）就按"答错"处理，回复改用预写提示
        //    每次调用（成功或失败）都由 AiCallLogService 记一条日志：token、耗时、费用
        Judgement judgement;
        String modelReply;
        Long logId = null;
        try {
            AiCallLogService.Logged logged = callLog.call(AiCallLogService.TASK_DIALOGUE_TURN, turnTemplate.name(),
                    new ChatRequest(List.of(ChatMessage.user(buildPrompt(session, state, topic, text.trim()))),
                            // maxTokens 4096：Gemini 3 先"思考"，思考的 token 也算在上限里；
                            // 设 1024 时实测回复会被截断成半个 JSON
                            0.2, 4096, true));
            logId = logged.logId();
            ChatResult result = logged.result();
            judgement = parser.parse(result.content());
            modelReply = usable(judgement.reply()) ? judgement.reply() : null;
            callLog.markCheck(logId, modelReply != null, modelReply != null ? null : "回复为空或超过 " + MAX_REPLY_CHARS + " 字");
            log.info("对话判断：type={} hit={} 耗时 {} ms", judgement.type(), judgement.hitPoints(), result.latencyMs());
        } catch (AiCallException e) {
            log.warn("对话调用模型失败，走兜底：{}", e.getMessage());
            if (logId != null) {
                callLog.markCheck(logId, false, e.getMessage());   // 调用成功但格式不对
            }
            judgement = new Judgement(JudgedType.WRONG, List.of(), "");
            modelReply = null;
        }
        studentMsg.setAiCallId(logId);

        // 2. 是否答全由程序数要点决定，不完全相信模型给的 type
        JudgedType type = recount(state, topic, judgement);
        studentMsg.setJudgedType(type.name());
        messageMapper.updateById(studentMsg);

        // 3. 规则决定下一步，生成消息
        DialogueRules.Decision decision = rules.onJudged(state, type);
        apply(session, state, topic, decision, type, modelReply);
        return view(session.getId());
    }

    /** 学生点"我卡住了"：不调模型，直接给预写提示或讲解 */
    @Transactional
    public DialogueView stuck(Long sessionId) {
        DialogueSession session = requireOpen(sessionId);
        DialogueTopicState state = currentState(session);
        TopicView topic = topicService.get(state.getTopicId());
        save(session.getId(), state.getTopicIdx(), "STUDENT", "（我卡住了）", null);
        apply(session, state, topic, rules.onStuck(state), null, null);
        return view(session.getId());
    }

    /** 暂停后继续 */
    @Transactional
    public DialogueView resume(Long sessionId) {
        DialogueSession session = load(sessionId);
        if (!DialogueSession.PAUSED.equals(session.getStatus())) {
            throw BusinessException.badRequest("对话没有暂停");
        }
        session.setStatus(DialogueSession.OPEN);
        session.setIrrelevantTopicsInRow(0);
        sessionMapper.updateById(session);
        TopicView topic = topicService.get(currentState(session).getTopicId());
        aiSay(session, session.getCurrentIdx(), "我们继续。「" + topic.title() + "」：\n" + topic.question(), "RULE");
        return view(sessionId);
    }

    public DialogueView get(Long sessionId) {
        load(sessionId);
        return view(sessionId);
    }

    // ===================== 一轮里的各个步骤 =====================

    private String buildPrompt(DialogueSession session, DialogueTopicState state, TopicView topic, String text) {
        List<String> points = topic.keyPoints();
        String keyPoints = IntStream.range(0, points.size())
                .mapToObj(i -> (i + 1) + ". " + points.get(i))
                .collect(Collectors.joining("\n"));
        String source = (topic.sourceRef() == null ? "" : topic.sourceRef() + "：")
                + (topic.sourceText() == null ? "" : topic.sourceText());
        Set<Integer> before = hits(state);
        int attempts = state.getWrongCount() + state.getStuckCount() + state.getIrrelevantCount();

        // Map.ofEntries：超过 10 个键值对时用它（Map.of 最多 10 个）
        return turnTemplate.render(Map.ofEntries(
                Map.entry("topic_title", topic.title()),
                Map.entry("question", topic.question()),
                Map.entry("key_points", keyPoints),
                Map.entry("source", source.isBlank() ? "（无）" : source),
                Map.entry("attempts", String.valueOf(attempts)),
                Map.entry("hints_used", String.valueOf(state.getHintLevel() + 1)),
                Map.entry("hit_before", before.isEmpty() ? "无" : join(before)),
                Map.entry("instruction", "如果没答全，给出一个更具体的提示，但不要说出要点原文。"),
                Map.entry("recent", recent(session.getId())),
                Map.entry("student_text", text)));
    }

    /** 把这一句答到的要点并入之前的，按总数重新确定 type */
    private JudgedType recount(DialogueTopicState state, TopicView topic, Judgement j) {
        int n = topic.keyPoints().size();
        Set<Integer> all = hits(state);
        List<Integer> valid = j.hitPoints().stream().filter(i -> i >= 1 && i <= n).toList();
        all.addAll(valid);
        state.setHitPoints(all.isEmpty() ? null : join(all));

        JudgedType type = j.type();
        if (type == JudgedType.CORRECT || type == JudgedType.PARTIAL || type == JudgedType.WRONG) {
            if (all.size() == n) {
                return JudgedType.CORRECT;
            }
            if (type == JudgedType.CORRECT || !valid.isEmpty()) {
                return JudgedType.PARTIAL;
            }
        }
        return type;
    }

    /** 按规则的决定生成 AI 消息、更新状态、必要时换话题或结束 */
    private void apply(DialogueSession session, DialogueTopicState state, TopicView topic,
                       DialogueRules.Decision d, JudgedType type, String modelReply) {
        int idx = state.getTopicIdx();
        switch (d.action()) {
            case HINT -> {
                // 要答案时也用预写提示：最容易泄露的场景，不让模型自由发挥
                if (modelReply != null && type != JudgedType.WANT_ANSWER) {
                    aiSay(session, idx, modelReply, "MODEL");
                } else {
                    // 卡住时用 hintLevel 指向的那条；答错兜底时按错误次数取
                    int level = type == null || type == JudgedType.WANT_ANSWER
                            ? state.getHintLevel() : state.getWrongCount() - 1;
                    aiSay(session, idx, "提示：" + prewrittenHint(topic, level), "TEMPLATE");
                }
            }
            case CONTINUE -> {
                String text = type == JudgedType.OFF_TOPIC
                        ? "这个问题课件里没有涉及，我们先回到刚才的问题：\n" + topic.question()
                        : "我们先回到这个问题：\n" + (blank(topic.simpleQuestion()) ? topic.question() : topic.simpleQuestion());
                aiSay(session, idx, text, "TEMPLATE");
            }
            case NEXT_TOPIC -> aiSay(session, idx, modelReply != null ? modelReply : "回答正确！", modelReply != null ? "MODEL" : "RULE");
            case REVEAL -> aiSay(session, idx, "我们一起来看：\n" + topic.revealText() + sourceSuffix(topic), "TEMPLATE");
            case SKIP -> aiSay(session, idx, "这个问题我们先看讲解：\n" + topic.revealText() + sourceSuffix(topic), "TEMPLATE");
        }
        stateMapper.updateById(state);

        if (d.result() != null) {
            moveOn(session, d.action());
        }
    }

    /** 话题结束：进入下一个话题、暂停或整段结束 */
    private void moveOn(DialogueSession session, NextAction action) {
        if (action == NextAction.SKIP) {
            session.setIrrelevantTopicsInRow(session.getIrrelevantTopicsInRow() + 1);
        } else {
            session.setIrrelevantTopicsInRow(0);
        }
        List<DialogueTopicState> states = states(session.getId());
        int next = session.getCurrentIdx() + 1;
        if (next >= states.size()) {
            session.setStatus(DialogueSession.FINISHED);
            sessionMapper.updateById(session);
            aiSay(session, session.getCurrentIdx(), "所有话题都完成了，下面是本次对话的总结。", "RULE");
            return;
        }
        session.setCurrentIdx(next);
        TopicView nextTopic = topicService.get(states.get(next).getTopicId());
        if (action == NextAction.SKIP && rules.shouldPause(session.getIrrelevantTopicsInRow())) {
            session.setStatus(DialogueSession.PAUSED);
            sessionMapper.updateById(session);
            aiSay(session, next, "看起来你现在不太方便继续，我们先暂停。准备好后点\"继续对话\"。", "RULE");
            return;
        }
        sessionMapper.updateById(session);
        aiSay(session, next, "接下来讨论「" + nextTopic.title() + "」。\n" + nextTopic.question(), "RULE");
    }

    // ===================== 组装返回给前端的内容 =====================

    private DialogueView view(Long sessionId) {
        DialogueSession session = load(sessionId);
        List<DialogueTopicState> states = states(sessionId);
        List<TopicView> topics = states.stream().map(s -> topicService.get(s.getTopicId())).toList();

        List<DialogueView.TopicProgress> progress = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            progress.add(new DialogueView.TopicProgress(topics.get(i).title(), states.get(i).getResult()));
        }
        List<DialogueView.MessageView> messages = messageMapper.selectList(new LambdaQueryWrapper<DialogueMessage>()
                        .eq(DialogueMessage::getSessionId, sessionId)
                        .orderByAsc(DialogueMessage::getId))
                .stream()
                .map(m -> new DialogueView.MessageView(m.getRole(), m.getContent(), m.getSource(), m.getTopicIdx()))
                .toList();
        DialogueView.Summary summary = DialogueSession.FINISHED.equals(session.getStatus())
                ? summary(states, topics) : null;
        return new DialogueView(sessionId, session.getStatus(), session.getCurrentIdx(), progress, messages, summary);
    }

    /** 总结：按每个话题的结果分组（docs 第 9 步，不调模型） */
    private DialogueView.Summary summary(List<DialogueTopicState> states, List<TopicView> topics) {
        List<String> good = new ArrayList<>();
        List<String> improve = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int completed = 0;
        for (int i = 0; i < states.size(); i++) {
            String r = states.get(i).getResult();
            TopicView t = topics.get(i);
            if (r == null) {
                continue;
            }
            completed++;
            switch (TopicResult.valueOf(r)) {
                case SELF_CORRECT -> good.add(t.title() + "（自己答对）");
                case HINT_CORRECT -> good.add(t.title() + "（提示后答对）");
                case REVEALED -> improve.add(t.title() + (blank(t.sourceRef()) ? "" : "，建议复习" + t.sourceRef()));
                case SKIPPED_IRRELEVANT -> skipped.add(t.title());
            }
        }
        return new DialogueView.Summary(states.size(), completed, good, improve, skipped);
    }

    // ===================== 小工具 =====================

    private DialogueSession load(Long id) {
        DialogueSession s = sessionMapper.selectById(id);
        if (s == null) {
            throw BusinessException.notFound("对话");
        }
        return s;
    }

    private DialogueSession requireOpen(Long id) {
        DialogueSession s = load(id);
        if (!DialogueSession.OPEN.equals(s.getStatus())) {
            throw BusinessException.badRequest(DialogueSession.PAUSED.equals(s.getStatus())
                    ? "对话已暂停，请先点\"继续对话\"" : "对话已经结束");
        }
        return s;
    }

    private List<DialogueTopicState> states(Long sessionId) {
        return stateMapper.selectList(new LambdaQueryWrapper<DialogueTopicState>()
                .eq(DialogueTopicState::getSessionId, sessionId)
                .orderByAsc(DialogueTopicState::getTopicIdx));
    }

    private DialogueTopicState currentState(DialogueSession session) {
        return states(session.getId()).get(session.getCurrentIdx());
    }

    /** 最近几条消息，拼成"学生：… / AI：…"给模型看上下文 */
    private String recent(Long sessionId) {
        List<DialogueMessage> list = new ArrayList<>(messageMapper.selectList(new LambdaQueryWrapper<DialogueMessage>()
                .eq(DialogueMessage::getSessionId, sessionId)
                .orderByDesc(DialogueMessage::getId)
                .last("LIMIT " + RECENT_MESSAGES)));
        Collections.reverse(list);
        return list.stream()
                .map(m -> ("STUDENT".equals(m.getRole()) ? "学生：" : "AI：") + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private DialogueMessage save(Long sessionId, Integer topicIdx, String role, String content, String source) {
        DialogueMessage m = new DialogueMessage();
        m.setSessionId(sessionId);
        m.setTopicIdx(topicIdx);
        m.setRole(role);
        m.setContent(content);
        m.setSource(source);
        messageMapper.insert(m);
        return m;
    }

    private void aiSay(DialogueSession session, int topicIdx, String content, String source) {
        save(session.getId(), topicIdx, "AI", content, source);
    }

    private String prewrittenHint(TopicView topic, int level) {
        List<String> hints = topic.hints();
        if (hints.isEmpty()) {
            return blank(topic.simpleQuestion()) ? "再想想题目里的关键条件。" : topic.simpleQuestion();
        }
        return hints.get(Math.max(0, Math.min(level, hints.size() - 1)));
    }

    private boolean usable(String reply) {
        return reply != null && !reply.isBlank() && reply.length() <= MAX_REPLY_CHARS;
    }

    private Set<Integer> hits(DialogueTopicState s) {
        // TreeSet：自动去重并按大小排序
        Set<Integer> set = new TreeSet<>();
        if (!blank(s.getHitPoints())) {
            Arrays.stream(s.getHitPoints().split(",")).map(String::trim).map(Integer::valueOf).forEach(set::add);
        }
        return set;
    }

    private String join(Set<Integer> set) {
        return set.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String sourceSuffix(TopicView t) {
        return blank(t.sourceRef()) ? "" : "（出处：" + t.sourceRef() + "）";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
