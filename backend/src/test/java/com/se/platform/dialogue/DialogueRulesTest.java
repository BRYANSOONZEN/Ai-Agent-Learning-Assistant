package com.se.platform.dialogue;

import static org.assertj.core.api.Assertions.assertThat;

import com.se.platform.dialogue.entity.DialogueTopicState;
import com.se.platform.dialogue.model.JudgedType;
import com.se.platform.dialogue.model.NextAction;
import com.se.platform.dialogue.model.TopicResult;
import com.se.platform.dialogue.service.DialogueRules;
import com.se.platform.dialogue.service.DialogueRules.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 规则表每一行一个测试。纯 Java，不启动 Spring，不调模型，几毫秒就跑完 */
class DialogueRulesTest {

    private final DialogueRules rules = new DialogueRules();
    private DialogueTopicState s;

    @BeforeEach
    void freshState() {
        s = new DialogueTopicState();
        s.setWrongCount(0);
        s.setStuckCount(0);
        s.setIrrelevantCount(0);
        s.setHintLevel(-1);
    }

    @Test
    void correct_firstTry_isSelfCorrect() {
        Decision d = rules.onJudged(s, JudgedType.CORRECT);
        assertThat(d.action()).isEqualTo(NextAction.NEXT_TOPIC);
        assertThat(d.result()).isEqualTo(TopicResult.SELF_CORRECT);
    }

    @Test
    void correct_afterOneWrong_isHintCorrect() {
        rules.onJudged(s, JudgedType.WRONG);
        assertThat(rules.onJudged(s, JudgedType.CORRECT).result()).isEqualTo(TopicResult.HINT_CORRECT);
    }

    @Test
    void correct_afterStuck_isHintCorrect() {
        rules.onStuck(s);
        assertThat(rules.onJudged(s, JudgedType.CORRECT).result()).isEqualTo(TopicResult.HINT_CORRECT);
    }

    @Test
    void partialOnce_givesHint() {
        Decision d = rules.onJudged(s, JudgedType.PARTIAL);
        assertThat(d.action()).isEqualTo(NextAction.HINT);
        assertThat(d.result()).isNull();
        assertThat(s.getWrongCount()).isEqualTo(1);
    }

    @Test
    void wrongTwice_reveals() {
        rules.onJudged(s, JudgedType.WRONG);
        Decision d = rules.onJudged(s, JudgedType.PARTIAL);
        assertThat(d.action()).isEqualTo(NextAction.REVEAL);
        assertThat(d.result()).isEqualTo(TopicResult.REVEALED);
        assertThat(s.getResult()).isEqualTo("REVEALED");
    }

    @Test
    void stuckOnce_givesFirstPrewrittenHint() {
        Decision d = rules.onStuck(s);
        assertThat(d.action()).isEqualTo(NextAction.HINT);
        assertThat(s.getHintLevel()).isEqualTo(0);
    }

    @Test
    void stuckThenWantAnswer_reveals() {
        rules.onStuck(s);
        assertThat(rules.onJudged(s, JudgedType.WANT_ANSWER).action()).isEqualTo(NextAction.REVEAL);
    }

    @Test
    void irrelevantOnce_continues_twice_skips() {
        assertThat(rules.onJudged(s, JudgedType.IRRELEVANT).action()).isEqualTo(NextAction.CONTINUE);
        Decision d = rules.onJudged(s, JudgedType.IRRELEVANT);
        assertThat(d.action()).isEqualTo(NextAction.SKIP);
        assertThat(d.result()).isEqualTo(TopicResult.SKIPPED_IRRELEVANT);
    }

    @Test
    void offTopic_continues_withoutCounting() {
        assertThat(rules.onJudged(s, JudgedType.OFF_TOPIC).action()).isEqualTo(NextAction.CONTINUE);
        assertThat(s.getWrongCount() + s.getStuckCount() + s.getIrrelevantCount()).isZero();
    }

    @Test
    void pauseAfterTwoSkippedTopics() {
        assertThat(rules.shouldPause(1)).isFalse();
        assertThat(rules.shouldPause(2)).isTrue();
    }
}
