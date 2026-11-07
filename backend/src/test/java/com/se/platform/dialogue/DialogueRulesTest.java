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

    /** 答错、没有新要点，还缺 uncovered 条 */
    private Decision wrong(int uncovered) {
        return rules.onAnswer(s, JudgedType.WRONG, false, uncovered);
    }

    @Test
    void correct_firstTry_isSelfCorrect() {
        Decision d = rules.onAnswer(s, JudgedType.CORRECT, true, 0);
        assertThat(d.action()).isEqualTo(NextAction.NEXT_TOPIC);
        assertThat(d.result()).isEqualTo(TopicResult.SELF_CORRECT);
    }

    @Test
    void correct_afterWrong_isHintCorrect() {
        wrong(3);
        assertThat(rules.onAnswer(s, JudgedType.CORRECT, true, 0).result()).isEqualTo(TopicResult.HINT_CORRECT);
    }

    @Test
    void correct_afterStuck_isHintCorrect() {
        rules.onAnswer(s, JudgedType.WANT_ANSWER, false, 3);
        assertThat(rules.onAnswer(s, JudgedType.CORRECT, true, 0).result()).isEqualTo(TopicResult.HINT_CORRECT);
    }

    @Test
    void newPoint_keepsAsking_withoutRaisingLevel() {
        for (int i = 0; i < 7; i++) {
            Decision d = rules.onAnswer(s, JudgedType.PARTIAL, true, 3);
            assertThat(d.action()).isEqualTo(NextAction.HINT);
            assertThat(d.result()).isNull();
        }
        assertThat(s.getHintLevel()).isEqualTo(-1);
    }

    @Test
    void noProgress_goesL1L2L3_thenGivesPoint() {
        wrong(3);
        assertThat(s.getHintLevel()).isEqualTo(0);
        wrong(3);
        assertThat(s.getHintLevel()).isEqualTo(1);
        assertThat(wrong(3).action()).isEqualTo(NextAction.HINT);
        assertThat(s.getHintLevel()).isEqualTo(2);
        Decision d = wrong(3);
        assertThat(d.action()).isEqualTo(NextAction.GIVE_POINT);
        assertThat(d.result()).isNull();
    }

    @Test
    void lastMissingPointAnswered_afterGiven_startsTransfer() {
        assertThat(rules.onAnswer(s, JudgedType.PARTIAL, true, 0).action()).isEqualTo(NextAction.START_TRANSFER);
    }

    @Test
    void wantAnswer_afterAttempt_raisesLevel_neverEnds() {
        wrong(3);
        Decision d = rules.onAnswer(s, JudgedType.WANT_ANSWER, false, 3);
        assertThat(d.action()).isEqualTo(NextAction.HINT);
        assertThat(s.getHintLevel()).isEqualTo(1);
    }

    @Test
    void wantAnswer_neverAttempted_thirdTimeSkips_withoutAnswer() {
        assertThat(rules.onAnswer(s, JudgedType.WANT_ANSWER, false, 3).action()).isEqualTo(NextAction.HINT);
        assertThat(rules.onAnswer(s, JudgedType.WANT_ANSWER, false, 3).action()).isEqualTo(NextAction.HINT);
        Decision d = rules.onAnswer(s, JudgedType.WANT_ANSWER, false, 3);
        assertThat(d.action()).isEqualTo(NextAction.SKIP);
        assertThat(d.result()).isEqualTo(TopicResult.SKIPPED_IRRELEVANT);
    }

    @Test
    void struggling_neverAttempted_raisesHintToL3_noPoints_noSkip() {
        for (int i = 0; i < 5; i++) {
            Decision d = rules.onAnswer(s, JudgedType.STRUGGLING, false, 3);
            assertThat(d.action()).isEqualTo(NextAction.HINT);
            assertThat(d.result()).isNull();
        }
        assertThat(s.getHintLevel()).isEqualTo(DialogueRules.MAX_HINT_LEVEL);
        assertThat(s.getRefuseCount()).isZero();
    }

    @Test
    void struggling_neverAttempted_skipsOnlyAtTurnLimit() {
        for (int i = 0; i < DialogueRules.MAX_TURNS - 1; i++) {
            rules.onAnswer(s, JudgedType.STRUGGLING, false, 3);
        }
        assertThat(rules.onAnswer(s, JudgedType.STRUGGLING, false, 3).action()).isEqualTo(NextAction.SKIP);
    }

    @Test
    void struggling_afterAttempt_escalatesToPoints() {
        wrong(3);
        rules.onAnswer(s, JudgedType.STRUGGLING, false, 3);
        rules.onAnswer(s, JudgedType.STRUGGLING, false, 3);
        assertThat(rules.onAnswer(s, JudgedType.STRUGGLING, false, 3).action()).isEqualTo(NextAction.GIVE_POINT);
    }

    @Test
    void transfer_struggling_keepsHinting_doesNotCountAsRefusal() {
        rules.startTransfer(s);
        for (int i = 0; i < DialogueRules.MAX_REFUSE; i++) {
            assertThat(rules.onTransfer(s, JudgedType.STRUGGLING, false, true).action()).isEqualTo(NextAction.HINT);
        }
        assertThat(s.getRefuseCount()).isZero();
    }

    @Test
    void irrelevantOnce_continues_twice_skips() {
        assertThat(rules.onAnswer(s, JudgedType.IRRELEVANT, false, 3).action()).isEqualTo(NextAction.CONTINUE);
        Decision d = rules.onAnswer(s, JudgedType.IRRELEVANT, false, 3);
        assertThat(d.action()).isEqualTo(NextAction.SKIP);
        assertThat(d.result()).isEqualTo(TopicResult.SKIPPED_IRRELEVANT);
    }

    @Test
    void offTopicAndClarify_continue_withoutRaisingLevel() {
        assertThat(rules.onAnswer(s, JudgedType.OFF_TOPIC, false, 3).action()).isEqualTo(NextAction.CONTINUE);
        assertThat(rules.onAnswer(s, JudgedType.CLARIFY, false, 3).action()).isEqualTo(NextAction.CONTINUE);
        assertThat(s.getHintLevel()).isEqualTo(-1);
        assertThat(s.getWrongCount() + s.getStuckCount() + s.getIrrelevantCount()).isZero();
    }

    @Test
    void turnLimit_attempted_goesToTransfer() {
        for (int i = 0; i < DialogueRules.MAX_TURNS - 1; i++) {
            rules.onAnswer(s, JudgedType.PARTIAL, true, 3);
        }
        assertThat(rules.onAnswer(s, JudgedType.PARTIAL, true, 3).action()).isEqualTo(NextAction.START_TRANSFER);
    }

    @Test
    void turnLimit_neverAttempted_skips() {
        for (int i = 0; i < DialogueRules.MAX_TURNS - 1; i++) {
            rules.onAnswer(s, JudgedType.CLARIFY, false, 3);
        }
        assertThat(rules.onAnswer(s, JudgedType.CLARIFY, false, 3).action()).isEqualTo(NextAction.SKIP);
    }

    @Test
    void transfer_complete_isRevealedApplied() {
        rules.startTransfer(s);
        Decision d = rules.onTransfer(s, JudgedType.CORRECT, true, true);
        assertThat(d.action()).isEqualTo(NextAction.REVEAL);
        assertThat(d.result()).isEqualTo(TopicResult.REVEALED_APPLIED);
    }

    @Test
    void transfer_notCompleteTwice_isRevealed() {
        rules.startTransfer(s);
        assertThat(rules.onTransfer(s, JudgedType.WRONG, false, true).action()).isEqualTo(NextAction.HINT);
        Decision d = rules.onTransfer(s, JudgedType.PARTIAL, false, true);
        assertThat(d.action()).isEqualTo(NextAction.REVEAL);
        assertThat(d.result()).isEqualTo(TopicResult.REVEALED);
    }

    @Test
    void transfer_refusingThreeTimes_endsWithoutAnswer() {
        rules.startTransfer(s);
        rules.onTransfer(s, JudgedType.WANT_ANSWER, false, true);
        rules.onTransfer(s, JudgedType.IRRELEVANT, false, true);
        Decision d = rules.onTransfer(s, JudgedType.WANT_ANSWER, false, true);
        assertThat(d.action()).isEqualTo(NextAction.END_WITHHELD);
        assertThat(d.result()).isEqualTo(TopicResult.REVEALED);
    }

    @Test
    void transfer_turnLimit_withoutAttempt_endsWithoutAnswer() {
        rules.startTransfer(s);
        for (int i = 0; i < DialogueRules.MAX_TURNS - 1; i++) {
            rules.onTransfer(s, JudgedType.CLARIFY, false, true);
        }
        assertThat(rules.onTransfer(s, JudgedType.CLARIFY, false, true).action()).isEqualTo(NextAction.END_WITHHELD);
    }

    @Test
    void noTransferQuestion_oneAttemptEnds_asRevealed() {
        rules.startTransfer(s);
        Decision d = rules.onTransfer(s, JudgedType.WRONG, false, false);
        assertThat(d.action()).isEqualTo(NextAction.REVEAL);
        assertThat(d.result()).isEqualTo(TopicResult.REVEALED);
    }

    @Test
    void pauseAfterTwoSkippedTopics() {
        assertThat(rules.shouldPause(1)).isFalse();
        assertThat(rules.shouldPause(2)).isTrue();
    }
}
