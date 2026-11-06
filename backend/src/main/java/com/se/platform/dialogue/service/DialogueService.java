package com.se.platform.dialogue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.ai.client.AiCallException;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.client.ChatResult;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.common.BusinessException;
import com.se.platform.common.DemoIdentity;
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
 * 模型读完这个话题的对话后判断并回复，下一步由规则决定；给要点、讲解、换话题、总结都用老师确认过的文字或程序生成。
 * 模型的回复要过检查（不为空、不太长、不泄露要点）才发给学生，否则才用预写提示。
 */
@Slf4j
@Service
public class DialogueService {

    /** 回复超过这个长度就不用模型的，改用预写提示 */
    private static final int MAX_REPLY_CHARS = 120;
    /** 给模型看这个话题最近几条消息 */
    private static final int RECENT_MESSAGES = 12;

    private final DialogueTopicService topicService;
    private final DialogueSessionMapper sessionMapper;
    private final DialogueTopicStateMapper stateMapper;
    private final DialogueMessageMapper messageMapper;
    private final AiCallLogService callLog;
    private final JudgementParser parser;
    private final DialogueRules rules;
    private final PromptTemplate turnTemplate = PromptTemplate.load("dialogue_turn_v2");

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
        session.setStudentId(DemoIdentity.studentId());
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
        return turn(sessionId, text.trim(), text.trim(), null);
    }

    /** 学生点"我卡住了"：也调模型，回复才能接上刚才的对话；是否算进展不看模型 */
    @Transactional
    public DialogueView stuck(Long sessionId) {
        return turn(sessionId, "（我卡住了）", "我卡住了，想不出来", JudgedType.WANT_ANSWER);
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

    /**
     * 一轮的顺序：存学生消息 → 调模型 → 程序重新数要点 → 规则决定下一步 → 生成消息。
     *
     * @param shown      存进对话、显示给学生的文字
     * @param promptText 传给模型的学生这一句
     * @param forced     不为空时类型由程序定（点"我卡住了"），不看模型判的
     */
    private DialogueView turn(Long sessionId, String shown, String promptText, JudgedType forced) {
        DialogueSession session = requireOpen(sessionId);
        DialogueTopicState state = currentState(session);
        TopicView topic = topicService.get(state.getTopicId());

        DialogueMessage studentMsg = save(session.getId(), state.getTopicIdx(), "STUDENT", shown, null);

        // 每次调用（成功或失败）都由 AiCallLogService 记一条日志：token、耗时、费用
        Judgement judgement = null;
        Long logId = null;
        try {
            AiCallLogService.Logged logged = callLog.call(AiCallLogService.TASK_DIALOGUE_TURN, turnTemplate.name(),
                    new ChatRequest(List.of(ChatMessage.user(buildPrompt(session, state, topic, promptText))),
                            // maxTokens 4096：Gemini 3 先"思考"，思考的 token 也算在上限里；
                            // 设 1024 时实测回复会被截断成半个 JSON
                            0.2, 4096, true));
            logId = logged.logId();
            studentMsg.setAiCallId(logId);
            ChatResult result = logged.result();
            judgement = parser.parse(result.content());
            log.info("对话判断：type={} hit={} 耗时 {} ms", judgement.type(), judgement.hitPoints(), result.latencyMs());
        } catch (AiCallException e) {
            log.warn("对话调用模型失败，走兜底：{}", e.getMessage());
            if (logId != null) {
                callLog.markCheck(logId, false, e.getMessage());   // 调用成功但格式不对
            }
        }

        // 模型失败不是学生的错：不算没进展，给当前级别的预写提示，学生可以再说一次
        if (judgement == null && forced == null) {
            messageMapper.updateById(studentMsg);
            aiSay(session, state.getTopicIdx(), fallbackHint(state, topic), "TEMPLATE");
            return view(session.getId());
        }

        Counted counted = judgement == null ? new Counted(forced, false) : recount(state, topic, judgement, forced);
        if (judgement != null && !blank(judgement.misconception())) {
            state.setMisconception(judgement.misconception());
        }
        String modelReply = judgement == null ? null : checkedReply(judgement.reply(), state, topic, logId);
        studentMsg.setJudgedType(counted.type().name());
        messageMapper.updateById(studentMsg);

        DialogueRules.Decision decision = DialogueTopicState.TRANSFER.equals(state.getPhase())
                ? rules.onTransfer(state, counted.type(), counted.type() == JudgedType.CORRECT, hasTransfer(topic))
                : rules.onAnswer(state, counted.type(), counted.newPoint(), uncovered(state, topic).size());
        apply(session, state, topic, decision, modelReply);
        return view(session.getId());
    }

    /** 这一轮数完要点的结果 */
    private record Counted(JudgedType type, boolean newPoint) {
    }

    private String buildPrompt(DialogueSession session, DialogueTopicState state, TopicView topic, String text) {
        boolean checking = usingTransferPoints(state, topic);
        List<String> points = checking ? topic.transferPoints() : topic.keyPoints();
        String keyPoints = IntStream.range(0, points.size())
                .mapToObj(i -> (i + 1) + ". " + points.get(i))
                .collect(Collectors.joining("\n"));
        String source = (topic.sourceRef() == null ? "" : topic.sourceRef() + "：")
                + (topic.sourceText() == null ? "" : topic.sourceText());
        Set<Integer> before = parse(checking ? state.getTransferHitPoints() : state.getHitPoints());
        Set<Integer> given = checking ? Set.of() : parse(state.getGivenPoints());

        // Map.ofEntries：超过 10 个键值对时用它（Map.of 最多 10 个）
        return turnTemplate.render(Map.ofEntries(
                Map.entry("topic_title", topic.title()),
                Map.entry("question", checking ? topic.transferQuestion() : topic.question()),
                Map.entry("key_points", keyPoints),
                Map.entry("source", source.isBlank() ? "（无）" : source),
                Map.entry("hit_before", before.isEmpty() ? "无" : join(before)),
                Map.entry("given", given.isEmpty() ? "无" : join(given)),
                Map.entry("irrelevant", String.valueOf(state.getIrrelevantCount())),
                Map.entry("misconception", blank(state.getMisconception()) ? "无" : state.getMisconception()),
                Map.entry("instruction", instruction(state, topic)),
                Map.entry("recent", recent(session.getId(), state.getTopicIdx())),
                Map.entry("student_text", text)));
    }

    /** 程序告诉模型这一轮该怎么提示：级别由程序定，模型只负责写成接得上对话的话 */
    private String instruction(DialogueTopicState state, TopicView topic) {
        if (DialogueTopicState.TRANSFER.equals(state.getPhase())) {
            return hasTransfer(topic)
                    ? "这是换了情境的变式题，学生刚看过原题的要点。没答全时提示他把原题的思路用到这个情境里，不说出变式要点。"
                    : "学生要用自己的话说一遍原题的答案。只请他说出来，不补充内容。";
        }
        String next = switch (Math.min(state.getHintLevel() + 1, DialogueRules.MAX_HINT_LEVEL)) {
            case 0 -> "只指出思考方向";
            case 1 -> "缩小范围，针对学生的错误想法反问";
            default -> "拆成一个很小、很容易回答的问题";
        };
        return "如果学生这一句答到了新要点，肯定后针对还缺的要点追问；如果没有新要点，提示要" + next + "。";
    }

    /** 把这一句新答到的要点并入之前的，按总数重新确定 type。已经告诉学生的要点，照抄不算 */
    private Counted recount(DialogueTopicState state, TopicView topic, Judgement j, JudgedType forced) {
        if (forced != null) {
            return new Counted(forced, false);
        }
        boolean checking = usingTransferPoints(state, topic);
        int n = (checking ? topic.transferPoints() : topic.keyPoints()).size();
        Set<Integer> all = parse(checking ? state.getTransferHitPoints() : state.getHitPoints());
        Set<Integer> given = checking ? Set.of() : parse(state.getGivenPoints());
        List<Integer> fresh = j.hitPoints().stream().distinct()
                .filter(i -> i >= 1 && i <= n && !all.contains(i) && !given.contains(i)).toList();
        all.addAll(fresh);
        if (checking) {
            state.setTransferHitPoints(joinOrNull(all));
        } else {
            state.setHitPoints(joinOrNull(all));
        }

        JudgedType type = j.type();
        boolean answering = type == JudgedType.CORRECT || type == JudgedType.PARTIAL || type == JudgedType.WRONG;
        // 闲聊里带答案也按作答算，模型判成离题也一样
        if (answering || !fresh.isEmpty()) {
            if (all.size() == n) {
                type = JudgedType.CORRECT;
            } else if (type == JudgedType.CORRECT || !fresh.isEmpty()) {
                type = JudgedType.PARTIAL;
            }
        }
        return new Counted(type, !fresh.isEmpty());
    }

    /** 回复为空、太长或泄露要点就不用，返回 null；结果记进调用日志 */
    private String checkedReply(String reply, DialogueTopicState state, TopicView topic, Long logId) {
        String problem = !usable(reply) ? "回复为空或超过 " + MAX_REPLY_CHARS + " 字"
                : leaks(reply, state, topic) ? "回复包含未答到的要点原文" : null;
        callLog.markCheck(logId, problem == null, problem);
        return problem == null ? reply : null;
    }

    /** 泄露检查：去掉空格和标点后，回复里有没有还没答到、也还没给出的要点原文。换个说法抓不到 */
    private boolean leaks(String reply, DialogueTopicState state, TopicView topic) {
        boolean checking = usingTransferPoints(state, topic);
        List<String> points = checking ? topic.transferPoints() : topic.keyPoints();
        Set<Integer> covered = parse(checking ? state.getTransferHitPoints() : state.getHitPoints());
        if (!checking) {
            covered.addAll(parse(state.getGivenPoints()));
        }
        String text = normalize(reply);
        return IntStream.range(0, points.size())
                .filter(i -> !covered.contains(i + 1))
                .anyMatch(i -> text.contains(normalize(points.get(i))));
    }

    private String normalize(String s) {
        return s.replaceAll("[\\s\\p{P}\\p{S}]", "");
    }

    /** 按规则的决定生成 AI 消息、更新状态、必要时换话题或结束 */
    private void apply(DialogueSession session, DialogueTopicState state, TopicView topic,
                       DialogueRules.Decision d, String modelReply) {
        int idx = state.getTopicIdx();
        switch (d.action()) {
            case HINT -> {
                if (modelReply != null) {
                    aiSay(session, idx, modelReply, "MODEL");
                } else {
                    aiSay(session, idx, fallbackHint(state, topic), "TEMPLATE");
                }
            }
            case CONTINUE -> {
                if (modelReply != null) {
                    aiSay(session, idx, modelReply, "MODEL");
                } else {
                    aiSay(session, idx, "我们先回到这个问题：\n" + currentQuestion(state, topic), "TEMPLATE");
                }
            }
            case GIVE_POINT -> givePoint(session, state, topic);
            case START_TRANSFER -> {
                rules.startTransfer(state);
                aiSay(session, idx, transferIntro(topic), "RULE");
            }
            case NEXT_TOPIC -> aiSay(session, idx, modelReply != null ? modelReply : "回答正确！", modelReply != null ? "MODEL" : "RULE");
            case REVEAL -> aiSay(session, idx, revealMessage(topic, d), "TEMPLATE");
            case END_WITHHELD -> aiSay(session, idx, "这道题我们先到这里。"
                    + (blank(topic.sourceRef()) ? "" : "建议回看" + topic.sourceRef() + "，")
                    + "想好后可以重新开始对话再试一次。", "RULE");
            case SKIP -> aiSay(session, idx, "这个话题我们先跳过，想好后可以重新开始对话再试一次。", "RULE");
        }
        stateMapper.updateById(state);

        if (d.result() != null) {
            moveOn(session, d.action());
        }
    }

    /** 给一条还缺的要点，用老师的原文，不调模型。都给完了就接着出变式题 */
    private void givePoint(DialogueSession session, DialogueTopicState state, TopicView topic) {
        List<Integer> missing = uncovered(state, topic);
        int point = missing.get(0);
        Set<Integer> given = parse(state.getGivenPoints());
        given.add(point);
        state.setGivenPoints(join(given));

        List<String> points = topic.keyPoints();
        String said = parse(state.getHitPoints()).stream().map(i -> points.get(i - 1)).collect(Collectors.joining("；"));
        String text = (said.isEmpty() ? "" : "你已经说对了：" + said + "。\n")
                + "这里告诉你一条：" + points.get(point - 1) + "。\n";
        if (missing.size() > 1) {
            text += "结合这一条，你能接着说说还缺什么吗？";
        } else {
            rules.startTransfer(state);
            text += transferIntro(topic);
        }
        aiSay(session, state.getTopicIdx(), text, "TEMPLATE");
    }

    /** 还没答到、也还没给出的要点序号 */
    private List<Integer> uncovered(DialogueTopicState state, TopicView topic) {
        Set<Integer> covered = parse(state.getHitPoints());
        covered.addAll(parse(state.getGivenPoints()));
        return IntStream.rangeClosed(1, topic.keyPoints().size()).filter(i -> !covered.contains(i)).boxed().toList();
    }

    private String transferIntro(TopicView topic) {
        return hasTransfer(topic)
                ? "换个情境试试，答完就给你看完整讲解：\n" + topic.transferQuestion()
                : "看完整讲解之前，请先用自己的话说一遍这道题的答案，说错也没关系。";
    }

    /** 完整答案只在这里出现：学生已经作答过变式题，或用自己的话说过一遍 */
    private String revealMessage(TopicView topic, DialogueRules.Decision d) {
        boolean applied = d.result() == TopicResult.REVEALED_APPLIED;
        String head = applied ? "答得对，这个思路你已经会用了。\n" : "";
        String transfer = hasTransfer(topic) && !applied ? "变式题的参考答案：" + topic.transferReveal() + "\n" : "";
        return head + transfer + "完整讲解：\n" + topic.revealText() + sourceSuffix(topic);
    }

    private String fallbackHint(DialogueTopicState state, TopicView topic) {
        return DialogueTopicState.TRANSFER.equals(state.getPhase())
                ? "提示：想想刚才的要点，放到这个新情境里分别对应什么？"
                : "提示：" + prewrittenHint(topic, state.getHintLevel());
    }

    private String currentQuestion(DialogueTopicState state, TopicView topic) {
        if (usingTransferPoints(state, topic)) {
            return topic.transferQuestion();
        }
        return blank(topic.simpleQuestion()) ? topic.question() : topic.simpleQuestion();
    }

    private boolean hasTransfer(TopicView topic) {
        return !blank(topic.transferQuestion()) && !topic.transferPoints().isEmpty();
    }

    private boolean usingTransferPoints(DialogueTopicState state, TopicView topic) {
        return DialogueTopicState.TRANSFER.equals(state.getPhase()) && hasTransfer(topic);
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
            DialogueTopicState st = states.get(i);
            progress.add(new DialogueView.TopicProgress(topics.get(i).title(), st.getResult(),
                    st.getResult() == null && DialogueTopicState.TRANSFER.equals(st.getPhase())));
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
                case REVEALED_APPLIED -> improve.add(t.title() + "（讲解后会用）");
                case REVEALED -> improve.add(t.title() + "（讲解后仍不会）" + (blank(t.sourceRef()) ? "" : "，建议复习" + t.sourceRef()));
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

    /** 这个话题最近几条消息，拼成"学生：… / AI：…"给模型看上下文；不混入其他话题 */
    private String recent(Long sessionId, int topicIdx) {
        List<DialogueMessage> list = new ArrayList<>(messageMapper.selectList(new LambdaQueryWrapper<DialogueMessage>()
                .eq(DialogueMessage::getSessionId, sessionId)
                .eq(DialogueMessage::getTopicIdx, topicIdx)
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

    private Set<Integer> parse(String points) {
        // TreeSet：自动去重并按大小排序
        Set<Integer> set = new TreeSet<>();
        if (!blank(points)) {
            Arrays.stream(points.split(",")).map(String::trim).map(Integer::valueOf).forEach(set::add);
        }
        return set;
    }

    private String joinOrNull(Set<Integer> set) {
        return set.isEmpty() ? null : join(set);
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
