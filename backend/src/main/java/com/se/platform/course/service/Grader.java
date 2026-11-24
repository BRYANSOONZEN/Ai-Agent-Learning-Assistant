package com.se.platform.course.service;

import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import java.text.Normalizer;
import java.util.List;
import java.util.Set;

/**
 * 题型和客观题判分（设计见 docs/模块说明/练习与掌握度-设计.md）。
 *
 * <p>客观题（单选、两段式、多选、判断、填空）全部由程序判，只记对（1）或错（0），不给部分分；
 * 每道题再记一个“蒙对的概率 g”，算掌握度时扣掉（猜测校正）。简答题由 {@link ShortAnswerJudge} 判。
 */
public final class Grader {

    public static final String SINGLE = "single_choice";
    public static final String TWO_TIER = "two_tier";
    public static final String MULTIPLE = "multiple";
    public static final String JUDGE = "judge";
    public static final String FILL = "fill";
    public static final String SHORT = "short_answer";
    public static final Set<String> TYPES = Set.of(SINGLE, TWO_TIER, MULTIPLE, JUDGE, FILL, SHORT);

    /** 考查类型（通用 4 种） */
    public static final Set<String> ASSESS = Set.of("concept", "scenario", "data", "process");

    public static final String GRADED = "graded";
    public static final String PENDING = "pending";

    /** 判断题固定的两个选项 */
    public static final List<String> JUDGE_OPTIONS = List.of("正确", "错误");

    private static final String LETTERS = "ABCDEF";

    private Grader() {
    }

    /**
     * 蒙对的概率 g：完全不会、随便选时答对的概率。
     * 单选 1/n；判断 1/2；两段式 1/(n×m)；多选（全对才算对）1/(2ⁿ−1)；填空、简答 0。
     */
    public static double guess(String type, int options, int reasons) {
        return switch (type == null ? SINGLE : type) {
            case JUDGE -> 0.5;
            case TWO_TIER -> options > 0 && reasons > 0 ? 1.0 / (options * reasons) : 0;
            case MULTIPLE -> options > 0 ? 1.0 / (Math.pow(2, options) - 1) : 0;
            case FILL, SHORT -> 0;
            default -> options > 0 ? 1.0 / options : 0;
        };
    }

    public static double guess(ExerciseView e) {
        return guess(e.type(), e.options().size(), e.reasons().size());
    }

    /** 判一道客观题；选错时带上这个错误选项（或错误理由）的误解说明 */
    public static QuestionResult grade(ExerciseView e, String raw) {
        String type = e.type() == null ? SINGLE : e.type();
        String input = raw == null ? null : raw.strip();
        boolean right;
        String chosen = input;
        String reasonChosen = null;
        String note = null;
        switch (type) {
            case TWO_TIER -> {
                String[] parts = input == null ? new String[0] : input.toUpperCase().split("\\|", -1);
                chosen = parts.length > 0 && !parts[0].isBlank() ? parts[0].strip() : null;
                reasonChosen = parts.length > 1 && !parts[1].isBlank() ? parts[1].strip() : null;
                right = e.answer().equals(chosen) && e.reasonAnswer() != null && e.reasonAnswer().equals(reasonChosen);
                note = noteOf(e.optionNotes(), chosen, e.answer());
                if (note == null) {
                    note = noteOf(e.reasonNotes(), reasonChosen, e.reasonAnswer());
                }
            }
            case MULTIPLE -> {
                chosen = input == null ? null : sortLetters(input);
                right = sortLetters(e.answer()).equals(chosen);
            }
            case FILL -> right = input != null && e.accepts().stream().anyMatch(a -> normalize(a).equals(normalize(input)));
            default -> { // 单选、判断
                chosen = input == null ? null : input.toUpperCase();
                right = e.answer().equals(chosen);
                note = noteOf(e.optionNotes(), chosen, e.answer());
            }
        }
        return new QuestionResult(e.id(), type, e.stem(), e.options(), chosen, e.answer(), right, e.analysis(),
                right ? null : note, right ? 1 : 0, guess(e), 1, GRADED, e.reasons(), e.reasonAnswer(), reasonChosen,
                e.accepts(), null, null);
    }

    private static String noteOf(List<String> notes, String chosen, String answer) {
        if (chosen == null || chosen.length() != 1 || chosen.equals(answer)) {
            return null;
        }
        int idx = chosen.charAt(0) - 'A';
        return idx >= 0 && idx < notes.size() && !notes.get(idx).isBlank() ? notes.get(idx) : null;
    }

    /** 多选答案：只留 A～F，去重排序（"ca" → "AC"） */
    public static String sortLetters(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : LETTERS.toCharArray()) {
            if (s.toUpperCase().indexOf(c) >= 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** 填空对比前统一格式：全角转半角、英文不分大小写、去掉空格和标点 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase();
        return n.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");
    }
}
