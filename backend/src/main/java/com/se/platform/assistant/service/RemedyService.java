package com.se.platform.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.assistant.dto.AssistantDtos.RemedyResult;
import com.se.platform.assistant.dto.AssistantDtos.RemedyView;
import com.se.platform.assistant.entity.RemedyRound;
import com.se.platform.assistant.mapper.RemedyRoundMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.dto.LearnDtos.QuizQuestion;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.ExerciseGenService;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.course.service.Grader;
import com.se.platform.diagnosis.dto.DiagnosisDtos.MasteryChange;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.service.RecordService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小助手的自动干预（个人）：docs/模块说明/小助手-自动干预与聊天.md 第四节。
 * <ol>
 *   <li>只在这一章的练习第一次交卷之后（阶段二）</li>
 *   <li>根据这个学生自己的错题（考第几条要点、选错的选项对应的误解），大模型当场生成 3 道补练题（单选、判断、多选），
 *       不经过老师审核</li>
 *   <li>学生在小助手里做，程序判分，算进补练；够 2 道、平均 ≥ 0.8 → 已补回来（画像从红变橙，不变绿）</li>
 *   <li>最多 2 轮；2 轮后还没补回来 → 建议看资料、去 AI 对话讲一遍（老师不插手，只能看到补练情况）</li>
 * </ol>
 */
@Service
public class RemedyService {

    public static final int MAX_ROUNDS = 2;
    public static final int PER_ROUND = 3;

    private final RemedyRoundMapper roundMapper;
    private final KnowledgePointMapper kpMapper;
    private final ChapterItemMapper itemMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseService exerciseService;
    private final ExerciseGenService genService;
    private final AnswerRecordMapper answerMapper;
    private final MasteryService masteryService;
    private final MasteryMapper masteryMapper;
    private final RecordService recordService;

    public RemedyService(RemedyRoundMapper roundMapper, KnowledgePointMapper kpMapper, ChapterItemMapper itemMapper,
                         ExerciseMapper exerciseMapper, ExerciseService exerciseService, ExerciseGenService genService,
                         AnswerRecordMapper answerMapper, MasteryService masteryService, MasteryMapper masteryMapper,
                         RecordService recordService) {
        this.roundMapper = roundMapper;
        this.kpMapper = kpMapper;
        this.itemMapper = itemMapper;
        this.exerciseMapper = exerciseMapper;
        this.exerciseService = exerciseService;
        this.genService = genService;
        this.answerMapper = answerMapper;
        this.masteryService = masteryService;
        this.masteryMapper = masteryMapper;
        this.recordService = recordService;
    }

    // ------------------------------------------------------------------ 阶段

    /** 阶段二：这一章的练习这个学生交过卷（任何一次练习里有作答） */
    public boolean submitted(long studentId, Long chapterId) {
        List<Long> items = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                        .eq(ChapterItem::getChapterId, chapterId).eq(ChapterItem::getType, "exercise"))
                .stream().map(ChapterItem::getId).toList();
        return !items.isEmpty() && answerMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getStudentId, studentId).in(AnswerRecord::getItemId, items)) > 0;
    }

    // ------------------------------------------------------------------ 开始一轮

    /** 开始（或继续）这个知识点的补练：有没交的一轮就接着做；否则生成新的一轮 */
    @Transactional
    public RemedyView start(long studentId, Long kpId) {
        KnowledgePoint kp = requireKp(kpId);
        if (!submitted(studentId, kp.getChapterId())) {
            throw BusinessException.badRequest("先完成本章的 AI 对话和练习，之后我会根据你的情况给你补练题");
        }
        List<RemedyRound> rounds = rounds(studentId, kpId);
        RemedyRound open = rounds.stream().filter(r -> RemedyRound.OPEN.equals(r.getStatus())).findFirst().orElse(null);
        if (open != null) {
            return view(kp, open, rounds.size(), "上次的补练题还没做完，接着做吧。");
        }
        Mastery m = mastery(studentId, kpId);
        String color = MasteryService.color(m);
        if (!MasteryService.RED.equals(color)) { // 只有红色（第一次没掌握、还没补回来）才补练
            return new RemedyView(kpId, kp.getName(), rounds.size(), MAX_ROUNDS, false, List.of(),
                    MasteryService.ORANGE.equals(color) ? "这个知识点你已经补回来了，复习时多留意就好。"
                            : "这个知识点你已经掌握了，不用补练。");
        }
        if (rounds.size() >= MAX_ROUNDS) {
            return new RemedyView(kpId, kp.getName(), rounds.size(), MAX_ROUNDS, true, List.of(),
                    "两轮补练做完了还没补回来。先回去看看资料，再去 AI 对话把这个知识点讲一遍，理清楚了再复习。");
        }
        int roundNo = rounds.size() + 1;
        List<ExerciseView> made = genService.generateRemedy(kpId, requirement(studentId, kpId, roundNo), PER_ROUND,
                roundNo);
        RemedyRound r = new RemedyRound();
        r.setStudentId(studentId);
        r.setKnowledgePointId(kpId);
        r.setRoundNo(roundNo);
        r.setExerciseIds(made.stream().map(e -> String.valueOf(e.id())).collect(Collectors.joining(",")));
        r.setStatus(RemedyRound.OPEN);
        r.setEscalated(false);
        roundMapper.insert(r);
        return view(kp, r, roundNo, roundNo == 1
                ? "根据你练习里做错的地方，给你出了 " + made.size() + " 道新题。"
                : "换了题型和场景，再来 " + made.size() + " 道。做之前可以先把这个知识点讲给 AI 同学听一遍。");
    }

    /**
     * 写给出题提示词【老师的其他要求】一栏的话：这个学生错在哪几条要点、选错的选项对应哪个误解、
     * 别和他做过的题重复。只用他自己的数据。
     */
    String requirement(long studentId, Long kpId, int roundNo) {
        List<AnswerRecord> records = masteryService.answersOf(studentId, kpId);
        Map<Long, AnswerRecord> first = new LinkedHashMap<>();
        records.forEach(a -> first.putIfAbsent(a.getExerciseId(), a));
        Map<Long, Exercise> exercises = first.isEmpty() ? Map.of()
                : exerciseMapper.selectByIds(first.keySet()).stream().collect(Collectors.toMap(Exercise::getId, e -> e));
        Set<Integer> missed = new LinkedHashSet<>();
        Map<String, Integer> notes = new LinkedHashMap<>();
        for (AnswerRecord a : first.values()) {
            Exercise e = exercises.get(a.getExerciseId());
            double score = a.getScore() != null ? a.getScore() : Boolean.TRUE.equals(a.getIsCorrect()) ? 1 : 0;
            if (e == null || score >= 1 || Grader.PENDING.equals(a.getStatus())) {
                continue;
            }
            if (e.getKeyPointNo() != null) {
                missed.add(e.getKeyPointNo());
            }
            if (!Grader.SHORT.equals(e.getType())) {
                String note = Grader.grade(exerciseService.toView(e), a.getAnswer()).note();
                if (note != null) {
                    notes.merge(note, 1, Integer::sum);
                }
            }
        }
        String misconception = notes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse(null);
        StringBuilder b = new StringBuilder("这是给一个学生的个人补练题（第 ").append(roundNo).append(" 轮），只出【出题计划】里的题数。");
        if (!missed.isEmpty()) {
            b.append("他做错的题考的是第 ").append(missed.stream().map(String::valueOf).collect(Collectors.joining("、")))
                    .append(" 条要点，补练题都考这几条。");
        }
        if (misconception != null) {
            b.append("他犯的误解是：「").append(misconception).append("」，错误选项要用到它。");
        }
        List<String> stems = exercises.values().stream().map(Exercise::getStem).limit(10).toList();
        if (!stems.isEmpty()) {
            b.append("不要和下面这些他做过的题重复，换新的场景和说法：");
            stems.forEach(s -> b.append("\n- ").append(s.length() > 60 ? s.substring(0, 60) + "…" : s));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ 交这一轮

    @Transactional
    public RemedyResult submit(long studentId, Long kpId, Map<Long, String> answers) {
        KnowledgePoint kp = requireKp(kpId);
        RemedyRound r = rounds(studentId, kpId).stream().filter(x -> RemedyRound.OPEN.equals(x.getStatus()))
                .findFirst().orElseThrow(() -> BusinessException.badRequest("没有要交的补练题"));
        List<Long> ids = ids(r);
        if (answers == null || !answers.keySet().containsAll(ids)) {
            throw BusinessException.badRequest("每道题都要作答");
        }
        List<QuestionResult> results = new ArrayList<>();
        for (Long id : ids) {
            Exercise e = exerciseMapper.selectById(id);
            if (e == null) {
                continue; // 题被删了
            }
            results.add(Grader.grade(exerciseService.toView(e), answers.get(id)));
        }
        recordService.saveAnswers(kp.getCourseId(), null, results);
        List<MasteryChange> changes = masteryService.update(studentId, ids);
        MasteryChange change = changes.stream().filter(c -> c.knowledgePointId().equals(kpId)).findFirst()
                .orElse(null);
        double score = results.isEmpty() ? 0
                : Math.round(results.stream().mapToDouble(QuestionResult::score).average().orElse(0) * 100) / 100.0;
        boolean recovered = change != null && MasteryService.STATUS_RECOVERED.equals(change.remedyStatus());
        boolean escalated = !recovered && r.getRoundNo() >= MAX_ROUNDS;
        r.setStatus(RemedyRound.DONE);
        r.setScore(score);
        r.setEscalated(escalated);
        r.setFinishedAt(LocalDateTime.now());
        roundMapper.updateById(r);
        String text = recovered ? "补回来了！这个知识点从红色变成橙色：说明你曾经在这里错过，复习时多留意。"
                : escalated ? "两轮补练做完了还没补回来。先回去看看资料，再去 AI 对话把这个知识点讲一遍。"
                : "这一轮还差一点。先看看解析和资料，再来第 2 轮。";
        return new RemedyResult(kpId, r.getRoundNo(), results, score, change, recovered, escalated,
                !recovered && !escalated, text);
    }

    // ------------------------------------------------------------------ 小工具

    /** 2 轮后还没补回来的（老师只能看到，不处理） */
    public List<RemedyRound> escalated(Long kpId) {
        return roundMapper.selectList(new LambdaQueryWrapper<RemedyRound>()
                .eq(RemedyRound::getEscalated, true)
                .eq(kpId != null, RemedyRound::getKnowledgePointId, kpId));
    }

    public List<RemedyRound> rounds(long studentId, Long kpId) {
        return roundMapper.selectList(new LambdaQueryWrapper<RemedyRound>()
                .eq(RemedyRound::getStudentId, studentId)
                .eq(RemedyRound::getKnowledgePointId, kpId)
                .orderByAsc(RemedyRound::getRoundNo));
    }

    private RemedyView view(KnowledgePoint kp, RemedyRound r, int done, String text) {
        List<QuizQuestion> qs = new ArrayList<>();
        for (Long id : ids(r)) {
            Exercise e = exerciseMapper.selectById(id);
            if (e != null) {
                ExerciseView v = exerciseService.toView(e);
                qs.add(new QuizQuestion(v.id(), v.type(), v.stem(), v.options(), v.reasons()));
            }
        }
        return new RemedyView(kp.getId(), kp.getName(), r.getRoundNo(), MAX_ROUNDS, false, qs, text);
    }

    private static List<Long> ids(RemedyRound r) {
        return r.getExerciseIds() == null || r.getExerciseIds().isBlank() ? List.of()
                : Arrays.stream(r.getExerciseIds().split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    private Mastery mastery(long studentId, Long kpId) {
        return masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId).eq(Mastery::getKnowledgePointId, kpId));
    }

    private KnowledgePoint requireKp(Long kpId) {
        KnowledgePoint kp = kpId == null ? null : kpMapper.selectById(kpId);
        if (kp == null) {
            throw BusinessException.notFound("知识点");
        }
        return kp;
    }
}
