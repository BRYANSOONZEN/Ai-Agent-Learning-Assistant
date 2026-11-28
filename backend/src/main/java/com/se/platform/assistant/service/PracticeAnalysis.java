package com.se.platform.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.assistant.dto.AssistantDtos.PlanStep;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.course.service.Grader;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.material.dto.KpDtos.KeyPoint;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.service.KpRecognizer;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 学习助手：从练习结果看出一个知识点具体哪里不足，自动给复习方案（不经过老师）。
 * 设计见 docs/模块说明/练习与掌握度-设计.md 第七节。
 * <ul>
 *   <li>每道题只看最近一次作答；等老师确认的简答不算</li>
 *   <li>不足：哪种考查类型做错了几道；做错的题考的是哪几条要点；选错的选项对应哪个误解（出现最多的）</li>
 *   <li>方案：① 看资料（没掌握的要点在哪个视频第几分钟、课件第几页，点了直接跳过去，最多 3 处）；
 *       ② 做题（这个知识点还没做对的题，优先做错过的考查类型）；③ 概念层也差时，再和 AI 同学讲一次</li>
 * </ul>
 */
@Service
public class PracticeAnalysis {

    static final int MAX_REVIEW = 3;
    static final Map<String, String> ASSESS = Map.of("custom", "老师定制", "concept", "概念辨析", "scenario", "情境应用",
            "data", "数据分析", "process", "流程步骤");

    private final AnswerRecordMapper answerMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseService exerciseService;
    private final MasteryService masteryService;
    private final ChapterKpMapper chapterKpMapper;
    private final KpRecognizer recognizer;
    private final ObjectMapper json;

    public PracticeAnalysis(AnswerRecordMapper answerMapper, ExerciseMapper exerciseMapper,
                            ExerciseService exerciseService, MasteryService masteryService,
                            ChapterKpMapper chapterKpMapper, KpRecognizer recognizer, ObjectMapper json) {
        this.answerMapper = answerMapper;
        this.exerciseMapper = exerciseMapper;
        this.exerciseService = exerciseService;
        this.masteryService = masteryService;
        this.chapterKpMapper = chapterKpMapper;
        this.recognizer = recognizer;
        this.json = json;
    }

    /** 一章的资料位置（出处编号 → 哪一项、第几秒、第几页），一章算一次 */
    public record Context(Map<String, KpRecognizer.Seg> segs) {
    }

    public record Result(List<String> details, List<PlanStep> plan, String misconception) {
    }

    public Context context(Long chapterId) {
        Map<String, KpRecognizer.Seg> segs = new LinkedHashMap<>();
        try {
            recognizer.segments(chapterId).forEach(s -> segs.putIfAbsent(s.key(), s));
        } catch (Exception e) {
            // 没有资料文字时不给“看资料”这一步
        }
        return new Context(segs);
    }

    /** 这个学生做过这一章的练习没有（做过就可以先给一份总结，不用等整章学完） */
    public boolean practiced(long studentId, List<ChapterItem> items) {
        List<Long> exerciseItems = items.stream().filter(i -> "exercise".equals(i.getType())).map(ChapterItem::getId)
                .toList();
        return !exerciseItems.isEmpty() && answerMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getStudentId, studentId).in(AnswerRecord::getItemId, exerciseItems)) > 0;
    }

    /**
     * @param weak        这个知识点要不要给方案（稳了的不给）
     * @param conceptWeak 概念层也差（现在不再加“和 AI 同学再讲一次”：AI 对话只在练习之前）
     */
    public Result analyse(long studentId, KnowledgePoint kp, Context ctx, boolean weak, boolean conceptWeak) {
        List<AnswerRecord> records = masteryService.answersOf(studentId, kp.getId()).stream()
                .filter(a -> !Grader.PENDING.equals(a.getStatus())).toList();
        Map<Long, AnswerRecord> latest = new LinkedHashMap<>();
        records.forEach(a -> latest.put(a.getExerciseId(), a)); // 从早到晚，后面的覆盖前面的
        Map<Long, Exercise> exercises = latest.isEmpty() ? Map.of()
                : exerciseMapper.selectByIds(latest.keySet()).stream().collect(Collectors.toMap(Exercise::getId, e -> e));

        Map<String, int[]> byAssess = new LinkedHashMap<>(); // 考查类型 → [做了几道, 错了几道]
        Set<Integer> missedPoints = new LinkedHashSet<>();
        Map<String, Integer> notes = new LinkedHashMap<>();
        for (AnswerRecord a : latest.values()) {
            Exercise e = exercises.get(a.getExerciseId());
            if (e == null) {
                continue;
            }
            String assess = e.getAssessType() == null ? "concept" : e.getAssessType();
            int[] c = byAssess.computeIfAbsent(assess, k -> new int[2]);
            c[0]++;
            double score = a.getScore() != null ? a.getScore() : Boolean.TRUE.equals(a.getIsCorrect()) ? 1 : 0;
            if (score >= 1) {
                continue;
            }
            c[1]++;
            if (e.getKeyPointNo() != null) {
                missedPoints.add(e.getKeyPointNo());
            }
            if (!Grader.SHORT.equals(e.getType())) {
                String note = Grader.grade(exerciseService.toView(e), a.getAnswer()).note();
                if (note != null) {
                    notes.merge(note, 1, Integer::sum);
                }
            }
        }
        List<String> details = new ArrayList<>();
        byAssess.forEach((k, c) -> {
            if (c[1] > 0) {
                details.add(ASSESS.getOrDefault(k, k) + "：" + c[0] + " 道错了 " + c[1] + " 道");
            }
        });
        ChapterKp ckp = chapterKpMapper.selectOne(new LambdaQueryWrapper<ChapterKp>()
                .eq(ChapterKp::getKnowledgePointId, kp.getId()).last("LIMIT 1"));
        List<KeyPoint> points = ckp == null ? List.of() : readPoints(ckp.getKeyPoints());
        for (Integer no : missedPoints) {
            if (no >= 1 && no <= points.size()) {
                details.add("没掌握的要点 " + no + "：" + abbrev(points.get(no - 1).text(), 40));
            }
        }
        String misconception = notes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse(null);
        if (misconception != null) {
            details.add("容易想成：" + misconception);
        }

        List<PlanStep> plan = new ArrayList<>();
        if (weak) {
            plan.addAll(review(points, missedPoints, ctx));
            int left = practiceLeft(kp.getId(), latest, byAssess);
            String failed = byAssess.entrySet().stream().filter(x -> x.getValue()[1] > 0)
                    .map(x -> "「" + ASSESS.getOrDefault(x.getKey(), x.getKey()) + "」").collect(Collectors.joining(""));
            if (left > 0) {
                plan.add(new PlanStep("practice", "再做 " + Math.min(left, 3) + " 道题" + (failed.isEmpty() ? ""
                        : "，先做" + failed + "类的"), null, null));
            } else if (!latest.isEmpty()) {
                plan.add(new PlanStep("practice", "这个知识点的题都做过了，看完资料后去学习推荐里再练", null, null));
            }
        }
        return new Result(details, plan, misconception);
    }

    /** 看资料：没掌握的要点的出处；不知道是哪条要点时，用知识点的全部出处。同一份资料只给一处，最多 3 处 */
    private List<PlanStep> review(List<KeyPoint> points, Set<Integer> missed, Context ctx) {
        List<Integer> nos = new ArrayList<>(missed);
        if (nos.isEmpty()) {
            for (int i = 1; i <= points.size(); i++) {
                nos.add(i);
            }
        }
        List<PlanStep> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Integer no : nos) {
            if (no < 1 || no > points.size()) {
                continue;
            }
            for (String src : points.get(no - 1).sources()) {
                KpRecognizer.Seg seg = ctx.segs().get(src);
                if (seg == null || out.size() >= MAX_REVIEW) {
                    continue;
                }
                String title = seg.item().getTitle();
                boolean video = "video".equals(seg.text().getKind());
                Integer start = video && seg.text().getStartSec() != null ? seg.text().getStartSec().intValue() : null;
                String where = video ? "看视频《" + title + "》" + KpRecognizer.mmss(seg.text().getStartSec()) + " 起"
                        : seg.text().getPage() != null ? "看《" + title + "》第 " + seg.text().getPage() + " 页"
                        : "看《" + title + "》";
                if (seen.add("item-" + seg.item().getId())) { // 同一份资料只给一处（最先出现的，一般是最早讲到的地方）
                    out.add(new PlanStep("review", where + (missed.isEmpty() ? "" : "（要点 " + no + "）"),
                            seg.item().getId(), start));
                }
            }
        }
        return out;
    }

    /** 这个知识点还能做的题：学生能看到的（不是草稿、不是简答）、最近一次没做对的 */
    private int practiceLeft(Long kpId, Map<Long, AnswerRecord> latest, Map<String, int[]> byAssess) {
        List<Long> ids = masteryService.exerciseIdsOf(kpId);
        if (ids.isEmpty()) {
            return 0;
        }
        return (int) exerciseMapper.selectByIds(ids).stream().filter(Exercise::recommendable)
                .filter(e -> {
                    AnswerRecord a = latest.get(e.getId());
                    return a == null || !Boolean.TRUE.equals(a.getIsCorrect());
                }).count();
    }

    private List<KeyPoint> readPoints(String text) {
        try {
            return text == null ? List.of() : json.readValue(text, new TypeReference<List<KeyPoint>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String abbrev(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
