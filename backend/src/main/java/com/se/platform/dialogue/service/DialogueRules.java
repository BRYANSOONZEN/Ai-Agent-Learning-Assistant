package com.se.platform.dialogue.service;

import com.se.platform.dialogue.entity.DialogueTopicState;
import com.se.platform.dialogue.model.JudgedType;
import com.se.platform.dialogue.model.NextAction;
import com.se.platform.dialogue.model.TopicResult;
import org.springframework.stereotype.Component;

/**
 * 对话规则（docs/ai-implementation.md 第 6 步的规则表）。纯 Java，不调模型，所以可以放心写单元测试。
 *
 * <p>输入：当前话题的状态 + 本轮发生了什么（模型的判断，或学生点了"我卡住了"）。
 * 输出：下一步动作和话题结果；同时更新状态里的计数。
 */
@Component
public class DialogueRules {

    /** 错几次就讲解 */
    public static final int MAX_WRONG = 2;
    /** 卡住几次就讲解 */
    public static final int MAX_STUCK = 2;
    /** 答非所问几次就跳过 */
    public static final int MAX_IRRELEVANT = 2;
    /** 连续跳过几个话题就暂停 */
    public static final int PAUSE_AFTER_SKIPS = 2;

    /**
     * @param action 下一步做什么
     * @param result 话题结果；话题还没结束时为 null
     */
    public record Decision(NextAction action, TopicResult result) {
    }

    /** 学生发了一句话，模型判断为 type */
    public Decision onJudged(DialogueTopicState s, JudgedType type) {
        // switch 表达式（Java 14+）：每个 case 用 -> 直接给出结果，不会"穿透"到下一个 case
        return switch (type) {
            case CORRECT -> {
                boolean clean = s.getWrongCount() == 0 && s.getStuckCount() == 0 && s.getHintLevel() < 0;
                yield finish(s, NextAction.NEXT_TOPIC, clean ? TopicResult.SELF_CORRECT : TopicResult.HINT_CORRECT);
            }
            case PARTIAL, WRONG -> {
                s.setWrongCount(s.getWrongCount() + 1);
                yield s.getWrongCount() >= MAX_WRONG
                        ? finish(s, NextAction.REVEAL, TopicResult.REVEALED)
                        : new Decision(NextAction.HINT, null);
            }
            case WANT_ANSWER -> onStuck(s);
            case IRRELEVANT -> {
                s.setIrrelevantCount(s.getIrrelevantCount() + 1);
                yield s.getIrrelevantCount() >= MAX_IRRELEVANT
                        ? finish(s, NextAction.SKIP, TopicResult.SKIPPED_IRRELEVANT)
                        : new Decision(NextAction.CONTINUE, null);
            }
            case OFF_TOPIC -> new Decision(NextAction.CONTINUE, null);
        };
    }

    /** 学生点了"我卡住了"（或直接要答案）：先给下一级预写提示，第二次就讲解 */
    public Decision onStuck(DialogueTopicState s) {
        s.setStuckCount(s.getStuckCount() + 1);
        if (s.getStuckCount() >= MAX_STUCK) {
            return finish(s, NextAction.REVEAL, TopicResult.REVEALED);
        }
        s.setHintLevel(s.getHintLevel() + 1);
        return new Decision(NextAction.HINT, null);
    }

    /** 跳过之后，连续跳过的话题数到了上限就暂停 */
    public boolean shouldPause(int irrelevantTopicsInRow) {
        return irrelevantTopicsInRow >= PAUSE_AFTER_SKIPS;
    }

    private Decision finish(DialogueTopicState s, NextAction action, TopicResult result) {
        s.setResult(result.name());
        return new Decision(action, result);
    }
}
