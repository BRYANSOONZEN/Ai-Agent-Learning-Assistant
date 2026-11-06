package com.se.platform.intervention.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.dialogue.entity.DialogueSession;
import com.se.platform.dialogue.entity.DialogueTopic;
import com.se.platform.dialogue.entity.DialogueTopicState;
import com.se.platform.dialogue.mapper.DialogueSessionMapper;
import com.se.platform.dialogue.mapper.DialogueTopicMapper;
import com.se.platform.dialogue.mapper.DialogueTopicStateMapper;
import com.se.platform.intervention.dto.InterventionDtos.TopicEvidence;
import com.se.platform.intervention.entity.Alert;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 两层状态（docs/计划-干预A和B.md 第 1 节）：一个学生在一个知识点上
 * <ul>
 *   <li>概念层：AI 对话里考这个知识点的话题，看最近一次已结束的对话 —— 全部自己答出 good，有提示后答出 fair，
 *       有系统讲解 bad，没做过 none。答非所问被跳过的话题不算（那是态度，不是理解）</li>
 *   <li>应用层：练习题的掌握度 —— 作答不到 3 次 none（少于 3 次时掌握度被公式压低，不可信），≥ 0.6 good，否则 bad</li>
 * </ul>
 */
@Service
public class LayerService {

    public static final String GOOD = "good";
    public static final String FAIR = "fair";
    public static final String BAD = "bad";
    public static final String NONE = "none";

    static final int MIN_ATTEMPTS = 3;
    static final double PASS = 0.6;

    private final MasteryMapper masteryMapper;
    private final DialogueTopicMapper topicMapper;
    private final DialogueSessionMapper sessionMapper;
    private final DialogueTopicStateMapper stateMapper;

    public LayerService(MasteryMapper masteryMapper, DialogueTopicMapper topicMapper,
                        DialogueSessionMapper sessionMapper, DialogueTopicStateMapper stateMapper) {
        this.masteryMapper = masteryMapper;
        this.topicMapper = topicMapper;
        this.sessionMapper = sessionMapper;
        this.stateMapper = stateMapper;
    }

    /** 概念层的结果；sessionId、time：用的是哪一次对话、什么时候结束的（none 时为空） */
    public record Concept(String state, Long sessionId, LocalDateTime time, List<TopicEvidence> topics) {
    }

    /** 概念层。after 不为空时只看这个时间之后开始的对话（判断干预后重做的那一次） */
    public Concept concept(long studentId, long kpId, LocalDateTime after) {
        Map<Long, String> titles = topicMapper.selectList(new LambdaQueryWrapper<DialogueTopic>()
                        .eq(DialogueTopic::getKnowledgePointId, kpId))
                .stream().collect(Collectors.toMap(DialogueTopic::getId, DialogueTopic::getTitle));
        Concept none = new Concept(NONE, null, null, List.of());
        if (titles.isEmpty()) {
            return none;
        }
        List<DialogueSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<DialogueSession>()
                .eq(DialogueSession::getStudentId, studentId)
                .eq(DialogueSession::getStatus, DialogueSession.FINISHED)
                .gt(after != null, DialogueSession::getCreatedAt, after)
                .orderByDesc(DialogueSession::getUpdatedAt)
                .orderByDesc(DialogueSession::getId));
        for (DialogueSession s : sessions) {
            List<DialogueTopicState> states = stateMapper.selectList(new LambdaQueryWrapper<DialogueTopicState>()
                            .eq(DialogueTopicState::getSessionId, s.getId())
                            .in(DialogueTopicState::getTopicId, titles.keySet())
                            .orderByAsc(DialogueTopicState::getTopicIdx))
                    .stream().filter(st -> st.getResult() != null && !"SKIPPED_IRRELEVANT".equals(st.getResult()))
                    .toList();
            if (states.isEmpty()) {
                continue; // 这次对话没聊到这个知识点（或都被跳过），看更早的一次
            }
            String state = states.stream().anyMatch(st -> "REVEALED".equals(st.getResult())) ? BAD
                    : states.stream().anyMatch(st -> "HINT_CORRECT".equals(st.getResult())
                            || "REVEALED_APPLIED".equals(st.getResult())) ? FAIR : GOOD;
            List<TopicEvidence> topics = states.stream().map(st -> new TopicEvidence(titles.get(st.getTopicId()),
                    st.getResult(), st.getWrongCount(), st.getStuckCount())).toList();
            return new Concept(state, s.getId(), s.getUpdatedAt(), topics);
        }
        return none;
    }

    /** 应用层：mastery 表那一行（可能为空）和状态 */
    public record Application(String state, Mastery mastery) {
    }

    public Application application(long studentId, long kpId) {
        Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId)
                .eq(Mastery::getKnowledgePointId, kpId));
        if (m == null || m.getAttemptCount() < MIN_ATTEMPTS) {
            return new Application(NONE, m);
        }
        return new Application(m.getMastery() >= PASS ? GOOD : BAD, m);
    }

    /**
     * 两层组合成问题类型：A 应用问题 / B 概念问题 / AB 两层都不好 / null 不报警。
     * 概念层未测、应用层不好属于"先补测"，这次不做，也返回 null。
     */
    public static String classify(String concept, String application) {
        boolean conceptOk = GOOD.equals(concept) || FAIR.equals(concept);
        if (conceptOk && BAD.equals(application)) {
            return Alert.A;
        }
        if (BAD.equals(concept)) {
            return BAD.equals(application) ? Alert.AB : Alert.B;
        }
        return null;
    }
}
