package com.se.platform.course.dto;

import java.util.List;
import java.util.Map;

/** 练习的出题工具：AI 生成、上传识别、覆盖检查、简答改判 */
public final class ExerciseToolDtos {

    private ExerciseToolDtos() {
    }

    /**
     * AI 出题：kpIds 是本章已确认的知识点（chapter_kp.id）；assessTypes 考查类型；types 题型；
     * perKp 每个知识点出几道（1～6，默认 3）；requirement 老师的其他要求（可选，最多 500 字），原样放进提示词。
     */
    public record GenRequest(Long itemId, List<Long> kpIds, List<String> assessTypes, List<String> types,
                             Integer perKp, String requirement) {

        public GenRequest(Long itemId, List<Long> kpIds, List<String> assessTypes, List<String> types,
                          Integer perKp) {
            this(itemId, kpIds, assessTypes, types, perKp, null);
        }
    }

    /** 每个知识点的出题结果：出了几道、丢掉几道（格式不对）、要点覆盖、误解用上几个、几道可能有歧义 */
    public record KpGen(Long kpId, String name, int created, int dropped, String points, String misconceptions,
                        int flagged, List<String> notes) {
    }

    /** 一次出题 / 识别的结果：耗时、调用次数、token、费用（元） */
    public record GenResult(List<KpGen> kps, int created, long ms, int calls, int inputTokens, int outputTokens,
                            double costCny) {
    }

    /** 覆盖检查的一行：一个知识点在每种考查类型、每种题型下有几道（不含草稿）、草稿几道、提醒 */
    public record CoverageRow(Long knowledgePointId, String name, Map<String, Integer> byAssess,
                              Map<String, Integer> byType, int total, int drafts, String warning) {
    }

    /** 老师看简答作答：两次判分结果、学生原话；pending 的要老师确认 */
    public record ShortAnswerView(Long recordId, Long exerciseId, String stem, String studentName,
                                  String studentNo, String answer, String status, Double score, Double reliability,
                                  boolean reviewed, String submittedAt, List<Map<String, Object>> points) {
    }

    /** 老师改判：每条要点答到没有 */
    public record ReviewRequest(List<Boolean> hits) {
    }
}
