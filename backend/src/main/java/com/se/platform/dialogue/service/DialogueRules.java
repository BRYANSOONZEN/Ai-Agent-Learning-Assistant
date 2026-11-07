package com.se.platform.dialogue.service;

import com.se.platform.dialogue.entity.DialogueTopicState;
import com.se.platform.dialogue.model.JudgedType;
import com.se.platform.dialogue.model.NextAction;
import com.se.platform.dialogue.model.TopicResult;
import org.springframework.stereotype.Component;

/**
 * 对话规则（docs/计划-AI对话重新设计.md 第 2 节的规则表）。纯 Java，不调模型，所以可以放心写单元测试。
 *
 * <p>输入：当前话题的状态 + 本轮发生了什么（程序数过要点之后的判断）。
 * 输出：下一步动作和话题结果；同时更新状态里的计数。
 * 不按错误次数给答案：有进展就继续追问，没进展才升一级提示，三级之后一次给一条要点。
 */
@Component
public class DialogueRules {

    /** 最高提示级别：hintLevel 0、1、2 = L1、L2、L3 */
    public static final int MAX_HINT_LEVEL = 2;
    /** 每个阶段最多几轮，防止没完没了 */
    public static final int MAX_TURNS = 8;
    /** 从没作答就要答案，累计几次跳过；变式阶段不作答累计几次结束 */
    public static final int MAX_REFUSE = 3;
    /** 答非所问几次就跳过 */
    public static final int MAX_IRRELEVANT = 2;
    /** 变式题最多作答几次 */
    public static final int MAX_TRANSFER_TRIES = 2;
    /** 连续跳过几个话题就暂停 */
    public static final int PAUSE_AFTER_SKIPS = 2;

    /**
     * @param action 下一步做什么
     * @param result 话题结果；话题还没结束时为 null
     */
    public record Decision(NextAction action, TopicResult result) {
    }

    /**
     * 作答阶段：学生发了一句话，或点了"我卡住了"（type = STRUGGLING）。
     *
     * @param newPoint  这一句答到了之前没答到的要点
     * @param uncovered 这一句之后，还有几条要点既没答到也没给出
     */
    public Decision onAnswer(DialogueTopicState s, JudgedType type, boolean newPoint, int uncovered) {
        s.setTurnCount(s.getTurnCount() + 1);
        // switch 表达式（Java 14+）：每个 case 用 -> 直接给出结果，不会"穿透"到下一个 case
        Decision d = switch (type) {
            case CORRECT -> {
                boolean clean = s.getWrongCount() == 0 && s.getStuckCount() == 0 && s.getHintLevel() < 0;
                yield finish(s, NextAction.NEXT_TOPIC, clean ? TopicResult.SELF_CORRECT : TopicResult.HINT_CORRECT);
            }
            case PARTIAL, WRONG -> {
                s.setWrongCount(s.getWrongCount() + 1);
                s.setAnswerCount(s.getAnswerCount() + 1);
                if (uncovered == 0) {
                    yield new Decision(NextAction.START_TRANSFER, null);
                }
                yield newPoint ? new Decision(NextAction.HINT, null) : escalate(s);
            }
            case WANT_ANSWER -> {
                s.setStuckCount(s.getStuckCount() + 1);
                yield s.getAnswerCount() > 0 ? escalate(s) : refuse(s);
            }
            case STRUGGLING -> {
                s.setStuckCount(s.getStuckCount() + 1);
                yield s.getAnswerCount() > 0 ? escalate(s) : encourage(s);
            }
            case IRRELEVANT -> {
                s.setIrrelevantCount(s.getIrrelevantCount() + 1);
                yield s.getIrrelevantCount() >= MAX_IRRELEVANT
                        ? finish(s, NextAction.SKIP, TopicResult.SKIPPED_IRRELEVANT)
                        : new Decision(NextAction.CONTINUE, null);
            }
            case OFF_TOPIC, CLARIFY -> new Decision(NextAction.CONTINUE, null);
        };
        if (d.result() != null || d.action() == NextAction.START_TRANSFER || s.getTurnCount() < MAX_TURNS) {
            return d;
        }
        // 到了轮数上限：作答过的进入变式，没作答过的跳过，都不显示答案
        return s.getAnswerCount() > 0
                ? new Decision(NextAction.START_TRANSFER, null)
                : finish(s, NextAction.SKIP, TopicResult.SKIPPED_IRRELEVANT);
    }

    /**
     * 变式阶段：只有真正作答过，才走到显示答案的 REVEAL。
     *
     * @param complete    变式要点全部答到
     * @param hasQuestion 老师写了变式题；旧话题没有，学生用自己的话说一遍就结束
     */
    public Decision onTransfer(DialogueTopicState s, JudgedType type, boolean complete, boolean hasQuestion) {
        s.setTurnCount(s.getTurnCount() + 1);
        Decision d = switch (type) {
            case CORRECT, PARTIAL, WRONG -> {
                s.setTransferTries(s.getTransferTries() + 1);
                if (!hasQuestion) {
                    yield finish(s, NextAction.REVEAL, TopicResult.REVEALED);
                }
                if (complete) {
                    yield finish(s, NextAction.REVEAL, TopicResult.REVEALED_APPLIED);
                }
                yield s.getTransferTries() >= MAX_TRANSFER_TRIES
                        ? finish(s, NextAction.REVEAL, TopicResult.REVEALED)
                        : new Decision(NextAction.HINT, null);
            }
            case WANT_ANSWER, IRRELEVANT -> {
                s.setRefuseCount(s.getRefuseCount() + 1);
                if (s.getRefuseCount() >= MAX_REFUSE) {
                    yield finish(s, NextAction.END_WITHHELD, TopicResult.REVEALED);
                }
                yield new Decision(type == JudgedType.WANT_ANSWER ? NextAction.HINT : NextAction.CONTINUE, null);
            }
            case STRUGGLING -> {
                s.setStuckCount(s.getStuckCount() + 1);
                yield new Decision(NextAction.HINT, null);
            }
            case OFF_TOPIC, CLARIFY -> new Decision(NextAction.CONTINUE, null);
        };
        if (d.result() != null || s.getTurnCount() < MAX_TURNS) {
            return d;
        }
        return s.getTransferTries() > 0
                ? finish(s, NextAction.REVEAL, TopicResult.REVEALED)
                : finish(s, NextAction.END_WITHHELD, TopicResult.REVEALED);
    }

    /** 进入变式阶段：轮数和不作答次数重新算 */
    public void startTransfer(DialogueTopicState s) {
        s.setPhase(DialogueTopicState.TRANSFER);
        s.setTurnCount(0);
        s.setRefuseCount(0);
    }

    /** 跳过之后，连续跳过的话题数到了上限就暂停 */
    public boolean shouldPause(int irrelevantTopicsInRow) {
        return irrelevantTopicsInRow >= PAUSE_AFTER_SKIPS;
    }

    /** 没进展：升一级提示；L3 之后给一条要点 */
    private Decision escalate(DialogueTopicState s) {
        if (s.getHintLevel() >= MAX_HINT_LEVEL) {
            return new Decision(NextAction.GIVE_POINT, null);
        }
        s.setHintLevel(s.getHintLevel() + 1);
        return new Decision(NextAction.HINT, null);
    }

    /** 从没作答就要答案：给提示、不给答案，累计到上限就跳过 */
    private Decision refuse(DialogueTopicState s) {
        s.setRefuseCount(s.getRefuseCount() + 1);
        return s.getRefuseCount() >= MAX_REFUSE ? finish(s, NextAction.SKIP, TopicResult.SKIPPED_IRRELEVANT) : encourage(s);
    }

    /** 从没作答但说不会：提示升到 L3 为止，不给要点，也不累计跳过；到轮数上限才跳过 */
    private Decision encourage(DialogueTopicState s) {
        s.setHintLevel(Math.min(s.getHintLevel() + 1, MAX_HINT_LEVEL));
        return new Decision(NextAction.HINT, null);
    }

    private Decision finish(DialogueTopicState s, NextAction action, TopicResult result) {
        s.setResult(result.name());
        return new Decision(action, result);
    }
}
