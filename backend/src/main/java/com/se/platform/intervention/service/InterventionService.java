package com.se.platform.intervention.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.CourseService;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.diagnosis.dto.DiagnosisDtos.ClassPoint;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.diagnosis.service.DiagnosisService;
import com.se.platform.dialogue.entity.DialogueSession;
import com.se.platform.dialogue.mapper.DialogueSessionMapper;
import com.se.platform.intervention.dto.InterventionDtos.AlertView;
import com.se.platform.intervention.dto.InterventionDtos.AnswerEvidence;
import com.se.platform.intervention.dto.InterventionDtos.AssignedDialogue;
import com.se.platform.intervention.dto.InterventionDtos.AssignedExercise;
import com.se.platform.intervention.dto.InterventionDtos.AssignedItem;
import com.se.platform.intervention.dto.InterventionDtos.Board;
import com.se.platform.intervention.dto.InterventionDtos.ClassAlert;
import com.se.platform.intervention.dto.InterventionDtos.Counts;
import com.se.platform.intervention.dto.InterventionDtos.Evidence;
import com.se.platform.intervention.dto.InterventionDtos.InterventionView;
import com.se.platform.intervention.dto.InterventionDtos.MyAssignments;
import com.se.platform.intervention.dto.InterventionDtos.ReviewRequest;
import com.se.platform.intervention.entity.Alert;
import com.se.platform.intervention.entity.Intervention;
import com.se.platform.intervention.mapper.AlertMapper;
import com.se.platform.intervention.mapper.InterventionMapper;
import com.se.platform.intervention.service.LayerService.Application;
import com.se.platform.intervention.service.LayerService.Concept;
import com.se.platform.recommend.entity.Recommendation;
import com.se.platform.recommend.mapper.RecommendationMapper;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.mapper.LearningEventMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预警与干预 A、B（docs/计划-干预A和B.md）。
 * <ol>
 *   <li>扫描：课程里每个学生 × 每个知识点，按两层（LayerService）判断 A / B / AB，生成预警和一条系统建议（待审核）</li>
 *   <li>审核：老师通过（可改题目、留言）才推给学生；驳回 = 不干预，预警标为忽略</li>
 *   <li>执行：A 的题写进推荐表（source = teacher）；B 是复习内容 + 重做对话；AB 先 B 后 A</li>
 *   <li>看效果：读取时检查学生做完没有，做完记下前后数据和是否有效</li>
 * </ol>
 * 建议和留言都用模板生成，不调 AI。
 *
 * <p>新方向（docs/计划-个人学习助手.md 第 11 节）：A、B、AB 扫描出来后都<b>自动执行</b>，老师事后可以撤回、改期限
 * （2026-11-22 起 AB 也自动，之前 AB 要老师审核）。老师主要看<b>全班预警</b>：某知识点薄弱的人太多。
 * 配置 platform.intervention.auto-approve=false 可以回到"全部由老师审核"。
 */
@Service
public class InterventionService {

    static final int MAX_EXERCISES = 3;
    static final int RECENT_ANSWERS = 5;
    /** 主要误解至少出现几次才算"错因明确" */
    static final int MIN_MISCONCEPTION = 2;
    static final double HIGH_LEVEL_BELOW = 0.4;
    /** 全班预警：薄弱人数 / 练过人数超过这个比例 */
    static final double CLASS_WEAK_RATIO = 0.3;
    /** 全班预警：练过的人少于这个数，比例不可信，不报 */
    static final int CLASS_MIN_PRACTICED = 5;
    /** 老师指定的期限最长几天 */
    static final int MAX_DUE_DAYS = 60;

    /**
     * 扫描会新建预警。老师端打开干预页时 board 和 open-count 两个请求会同时到，学生端也会扫描自己；
     * 同时扫描会把同一条预警建两次。控制器调用扫描相关的方法时都先锁住它，一次只扫一个。
     * 锁要加在控制器里（事务外面），这样前一次的事务提交之后，下一次才开始读。
     */
    public static final Object SCAN_LOCK = new Object();

    /** A、B 是否自动执行；默认是 */
    private boolean autoApprove = true;

    private final AlertMapper alertMapper;
    private final InterventionMapper interventionMapper;
    private final LayerService layerService;
    private final MasteryMapper masteryMapper;
    private final AnswerRecordMapper answerMapper;
    private final LearningEventMapper eventMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final KnowledgePointMapper kpMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final RecommendationMapper recommendationMapper;
    private final DialogueSessionMapper sessionMapper;
    private final CourseService courseService;
    private final CourseMapper courseMapper;
    private final ExerciseService exerciseService;
    private final DiagnosisService diagnosisService;
    private final ObjectMapper objectMapper;

    public InterventionService(AlertMapper alertMapper, InterventionMapper interventionMapper,
                               LayerService layerService, MasteryMapper masteryMapper,
                               AnswerRecordMapper answerMapper, LearningEventMapper eventMapper,
                               ExerciseMapper exerciseMapper, ExerciseKnowledgePointMapper linkMapper,
                               KnowledgePointMapper kpMapper, ChapterMapper chapterMapper,
                               ChapterItemMapper itemMapper, RecommendationMapper recommendationMapper,
                               DialogueSessionMapper sessionMapper, CourseService courseService,
                               CourseMapper courseMapper, ExerciseService exerciseService,
                               DiagnosisService diagnosisService, ObjectMapper objectMapper) {
        this.alertMapper = alertMapper;
        this.interventionMapper = interventionMapper;
        this.layerService = layerService;
        this.masteryMapper = masteryMapper;
        this.answerMapper = answerMapper;
        this.eventMapper = eventMapper;
        this.exerciseMapper = exerciseMapper;
        this.linkMapper = linkMapper;
        this.kpMapper = kpMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.recommendationMapper = recommendationMapper;
        this.sessionMapper = sessionMapper;
        this.courseService = courseService;
        this.courseMapper = courseMapper;
        this.exerciseService = exerciseService;
        this.diagnosisService = diagnosisService;
        this.objectMapper = objectMapper;
    }

    /** 干预的默认期限：开始执行后几天内做完。application.yml：platform.intervention.due-days，不写就是 7 */
    private int dueDays = 7;

    @Value("${platform.intervention.due-days:7}")
    public void setDueDays(int dueDays) {
        this.dueDays = dueDays;
    }

    /** application.yml：platform.intervention.auto-approve，不写就是 true */
    @Value("${platform.intervention.auto-approve:true}")
    public void setAutoApprove(boolean autoApprove) {
        this.autoApprove = autoApprove;
    }

    // ------------------------------------------------------------------ 老师：总览

    /** 干预管理页：先扫描生成新预警、检查执行中的干预做完没有，再返回全部 */
    @Transactional
    public Board board(Long courseId) {
        Course course = courseService.requireOwned(courseId);
        scan(courseId);
        List<Intervention> all = interventionMapper.selectList(new LambdaQueryWrapper<Intervention>()
                .eq(Intervention::getCourseId, courseId)
                .orderByDesc(Intervention::getId));
        all.forEach(this::refresh);

        Map<Long, Intervention> pendingByAlert = all.stream()
                .filter(iv -> Intervention.PENDING.equals(iv.getStatus()) && iv.getAlertId() != null)
                .collect(Collectors.toMap(Intervention::getAlertId, iv -> iv, (a, b) -> a));
        List<AlertView> alerts = alertMapper.selectList(new LambdaQueryWrapper<Alert>()
                        .eq(Alert::getCourseId, courseId)
                        .eq(Alert::getStatus, Alert.OPEN))
                .stream()
                .sorted(Comparator.comparing((Alert a) -> "high".equals(a.getLevel()) ? 0 : 1)
                        .thenComparing(Alert::getId))
                .map(a -> alertView(a, pendingByAlert.get(a.getId()))).toList();
        List<InterventionView> interventions = all.stream()
                .filter(iv -> Intervention.APPROVED.equals(iv.getStatus()) || Intervention.DONE.equals(iv.getStatus())
                        || Intervention.REVOKED.equals(iv.getStatus()))
                .map(iv -> view(iv, true)).toList();
        int effective = (int) all.stream().filter(iv -> Boolean.TRUE.equals(iv.getEffective())).count();
        int ineffective = (int) all.stream().filter(iv -> Boolean.FALSE.equals(iv.getEffective())).count();
        Counts counts = new Counts(alerts.size(),
                (int) all.stream().filter(iv -> Intervention.APPROVED.equals(iv.getStatus())).count(),
                (int) all.stream().filter(iv -> Intervention.DONE.equals(iv.getStatus())).count(),
                effective, ineffective,
                (int) all.stream().filter(InterventionService::overdue).count());
        return new Board(courseId, course.getName(), counts, classAlerts(courseId), alerts, interventions);
    }

    /**
     * 全班预警：直接用学情诊断页已经算好的全班统计（每个知识点练过几人、薄弱几人、最常见误解），
     * 练过的人够多、薄弱比例超过阈值就报。不存表，每次打开时现算。
     */
    public List<ClassAlert> classAlerts(Long courseId) {
        List<ClassAlert> list = new ArrayList<>();
        for (ClassPoint p : diagnosisService.course(courseId).points()) {
            if (p.practicedStudents() < CLASS_MIN_PRACTICED) {
                continue;
            }
            double ratio = (double) p.weakStudents() / p.practicedStudents();
            if (ratio <= CLASS_WEAK_RATIO) {
                continue;
            }
            var top = p.misconceptions().isEmpty() ? null : p.misconceptions().get(0);
            list.add(new ClassAlert(p.knowledgePointId(), p.name(), p.chapterTitle(), p.weakStudents(),
                    p.practicedStudents(), Math.round(ratio * 100) / 100.0,
                    top == null ? null : top.note(), top == null ? 0 : top.count()));
        }
        list.sort(Comparator.comparingDouble(ClassAlert::ratio).reversed());
        return list;
    }

    /** 侧边栏数字：当前老师所有课程里待处理的预警（先扫描，数据量小时很快） */
    @Transactional
    public int openCount() {
        List<Long> courseIds = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, courseService.currentTeacherId()))
                .stream().map(Course::getId).toList();
        if (courseIds.isEmpty()) {
            return 0;
        }
        courseIds.forEach(this::scan);
        int classAlerts = courseIds.stream().mapToInt(id -> classAlerts(id).size()).sum();
        return classAlerts + Math.toIntExact(alertMapper.selectCount(new LambdaQueryWrapper<Alert>()
                .in(Alert::getCourseId, courseIds)
                .eq(Alert::getStatus, Alert.OPEN)));
    }

    // ------------------------------------------------------------------ 扫描

    /** 课程里每个学生 × 每个知识点按两层判断；有新问题就建预警和系统建议 */
    void scan(Long courseId) {
        List<KnowledgePoint> kps = kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .orderByAsc(KnowledgePoint::getSortOrder)
                .orderByAsc(KnowledgePoint::getId));
        if (kps.isEmpty()) {
            return;
        }
        Set<Long> students = new LinkedHashSet<>();
        masteryMapper.selectList(new LambdaQueryWrapper<Mastery>()
                        .in(Mastery::getKnowledgePointId, kps.stream().map(KnowledgePoint::getId).toList()))
                .forEach(m -> students.add(m.getStudentId()));
        sessionMapper.selectList(new LambdaQueryWrapper<DialogueSession>()
                        .in(DialogueSession::getChapterId, kps.stream().map(KnowledgePoint::getChapterId).distinct()
                                .toList()))
                .forEach(s -> students.add(s.getStudentId()));

        for (Long studentId : students.stream().sorted().toList()) {
            scanOne(courseId, studentId, kps);
        }
    }

    /**
     * 学生端也扫描自己：干预自动执行之后，不能等老师打开干预页才推给学生。
     * 学生打开个人学习助手或"老师布置"时调用，只扫这个学生在已发布课程里的知识点。
     */
    @Transactional
    public void scanStudent(long studentId) {
        List<Long> courseIds = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, "published"))
                .stream().map(Course::getId).toList();
        for (Long courseId : courseIds) {
            List<KnowledgePoint> kps = kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                    .eq(KnowledgePoint::getCourseId, courseId)
                    .orderByAsc(KnowledgePoint::getSortOrder)
                    .orderByAsc(KnowledgePoint::getId));
            scanOne(courseId, studentId, kps);
        }
    }

    private void scanOne(Long courseId, long studentId, List<KnowledgePoint> kps) {
        for (KnowledgePoint kp : kps) {
            Concept concept = layerService.concept(studentId, kp.getId(), null);
            Application app = layerService.application(studentId, kp.getId());
            String type = LayerService.classify(concept.state(), app.state());
            if (type != null && !blocked(studentId, kp.getId(), concept, app)) {
                raise(courseId, studentId, kp, type, concept, app);
            }
        }
    }

    /**
     * 不再生成预警的情况：已有待处理的预警，或待审核 / 执行中的干预；
     * 或者上一条预警之后没有新数据（新作答、新对话），避免老师忽略后马上又冒出来。
     */
    private boolean blocked(long studentId, long kpId, Concept concept, Application app) {
        boolean busy = alertMapper.exists(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getStudentId, studentId)
                .eq(Alert::getKnowledgePointId, kpId)
                .eq(Alert::getStatus, Alert.OPEN))
                || interventionMapper.exists(new LambdaQueryWrapper<Intervention>()
                .eq(Intervention::getStudentId, studentId)
                .eq(Intervention::getKnowledgePointId, kpId)
                .in(Intervention::getStatus, Intervention.PENDING, Intervention.APPROVED));
        if (busy) {
            return true;
        }
        Alert last = alertMapper.selectOne(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getStudentId, studentId)
                .eq(Alert::getKnowledgePointId, kpId)
                .orderByDesc(Alert::getId)
                .last("LIMIT 1"));
        if (last == null) {
            return false;
        }
        LocalDateTime latest = latest(concept.time(), app.mastery() == null ? null : app.mastery().getUpdatedAt());
        return latest == null || !latest.isAfter(last.getCreatedAt());
    }

    private void raise(Long courseId, long studentId, KnowledgePoint kp, String type, Concept concept,
                       Application app) {
        Mastery m = app.mastery();
        List<AnswerEvidence> recent = recentAnswers(studentId, kp.getId());
        String misconception = mainMisconception(studentId, kp.getId());
        Evidence evidence = new Evidence(concept.state(), app.state(), m == null ? null : m.getMastery(),
                m == null ? 0 : m.getAttemptCount(), m == null ? 0 : m.getCorrectCount(), concept.topics(),
                concept.sessionId(), recent, misconception);

        Alert alert = new Alert();
        alert.setCourseId(courseId);
        alert.setStudentId(studentId);
        alert.setKnowledgePointId(kp.getId());
        alert.setType(type);
        alert.setLevel(Alert.B.equals(type) ? "medium"
                : Alert.AB.equals(type) || m.getMastery() < HIGH_LEVEL_BELOW ? "high" : "medium");
        alert.setMessage(message(type, kp, concept, app) + (hadIneffective(studentId, kp.getId())
                ? "；上一次干预后仍未解决" : ""));
        alert.setEvidence(toJson(evidence));
        alert.setStatus(Alert.OPEN);
        alertMapper.insert(alert);

        Intervention iv = new Intervention();
        iv.setAlertId(alert.getId());
        iv.setCourseId(courseId);
        iv.setStudentId(studentId);
        iv.setKnowledgePointId(kp.getId());
        iv.setType(Alert.A.equals(type) ? Intervention.PRACTICE
                : Alert.B.equals(type) ? Intervention.REVIEW : Intervention.REVIEW_PRACTICE);
        iv.setStep(1);
        if (!Alert.B.equals(type)) {
            iv.setExerciseIds(join(pickExercises(studentId, kp.getId(), misconception)));
        }
        if (!Alert.A.equals(type)) {
            iv.setItemIds(join(reviewItems(kp.getChapterId()).stream().map(ChapterItem::getId).toList()));
        }
        iv.setNote(note(type, kp, misconception));
        iv.setSource("system");
        iv.setStatus(Intervention.PENDING);
        interventionMapper.insert(iv);

        // A、B、AB 都直接执行，不等老师审核；老师事后可以撤回、改期限。
        // 要做练习的（A、AB）如果这个知识点下没有题可布置，留给老师处理
        boolean canRun = Alert.B.equals(type) || !split(iv.getExerciseIds()).isEmpty();
        if (autoApprove && canRun) {
            start(iv, alert, null, null);
        }
    }

    /**
     * 开始执行一条干预：老师审核通过时调用，系统自动执行时也调用（reviewerId 为空 = 系统自动）。
     * 记下开始时间和干预前的数据，A 的题写进学生的推荐表，预警标为已处理。
     */
    private void start(Intervention iv, Alert alert, Long reviewerId, LocalDateTime dueAt) {
        LocalDateTime now = LocalDateTime.now();
        iv.setStatus(Intervention.APPROVED);
        iv.setReviewerId(reviewerId);
        iv.setReviewedAt(now);
        // 期限：老师审核时指定的，或者默认开始后 dueDays 天（到当天 23:59）
        iv.setDueAt(dueAt != null ? dueAt : now.plusDays(dueDays).withHour(23).withMinute(59).withSecond(0).withNano(0));
        iv.setBeforeValue(Intervention.REVIEW.equals(iv.getType())
                ? layerService.concept(iv.getStudentId(), iv.getKnowledgePointId(), null).state()
                : masteryText(iv.getStudentId(), iv.getKnowledgePointId()));
        interventionMapper.updateById(iv);
        if (Intervention.PRACTICE.equals(iv.getType())) {
            assign(iv);
        }
        closeAlert(alert, Alert.HANDLED);
    }

    private boolean hadIneffective(long studentId, long kpId) {
        return interventionMapper.exists(new LambdaQueryWrapper<Intervention>()
                .eq(Intervention::getStudentId, studentId)
                .eq(Intervention::getKnowledgePointId, kpId)
                .eq(Intervention::getStatus, Intervention.DONE)
                .eq(Intervention::getEffective, false));
    }

    /** 给老师看的一句话 */
    private String message(String type, KnowledgePoint kp, Concept concept, Application app) {
        Mastery m = app.mastery();
        String practice = m == null ? "还没做过练习"
                : LayerService.NONE.equals(app.state()) ? "练习只做了 " + m.getAttemptCount() + " 次"
                : String.format("练习掌握度 %.2f（%d 次对 %d 次）", m.getMastery(), m.getAttemptCount(),
                m.getCorrectCount());
        String dialogue = switch (concept.state()) {
            case LayerService.GOOD -> "对话里自己答出";
            case LayerService.FAIR -> "对话里提示后答出";
            case LayerService.BAD -> concept.topics().stream().anyMatch(t -> "SKIPPED_IRRELEVANT".equals(t.result()))
                    ? "对话里跳过了话题，没有作答" : "对话里讲解后仍不会";
            default -> "还没做对话";
        };
        return switch (type) {
            case Alert.A -> "「" + kp.getName() + "」" + dialogue + "，但" + practice;
            case Alert.B -> "「" + kp.getName() + "」" + practice + "，但" + dialogue;
            default -> "「" + kp.getName() + "」" + dialogue + "，" + practice;
        };
    }

    /** 给学生的话（模板） */
    private String note(String type, KnowledgePoint kp, String misconception) {
        String practice = misconception != null
                ? "你在「" + kp.getName() + "」上几次都是这个问题：" + misconception + "。做这几道题时注意这一点。"
                : "「" + kp.getName() + "」还不稳，做这几道题巩固一下。";
        String review = "关于「" + kp.getName() + "」，对话里你还没能自己讲出来。先复习本章内容，再重新和 AI 聊一次。";
        return switch (type) {
            case Alert.A -> practice;
            case Alert.B -> review;
            default -> review + "做完后会再给你几道练习。";
        };
    }

    // ------------------------------------------------------------------ 证据

    /** 这个知识点的题上最近几次作答，新的在前 */
    private List<AnswerEvidence> recentAnswers(long studentId, long kpId) {
        Map<Long, Exercise> exercises = kpExercises(kpId);
        if (exercises.isEmpty()) {
            return List.of();
        }
        return answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId)
                        .in(AnswerRecord::getExerciseId, exercises.keySet())
                        .orderByDesc(AnswerRecord::getSubmittedAt)
                        .orderByDesc(AnswerRecord::getId)
                        .last("LIMIT " + RECENT_ANSWERS))
                .stream().map(a -> {
                    Exercise e = exercises.get(a.getExerciseId());
                    boolean ok = Boolean.TRUE.equals(a.getIsCorrect());
                    return new AnswerEvidence(e.getId(), e.getStem(), a.getAnswer(), ok,
                            ok ? null : noteOf(e, a.getAnswer()), a.getSubmittedAt());
                }).toList();
    }

    /**
     * 主要误解：每道题只看最近一次作答，答错时所选选项的误解说明，出现最多的一个；
     * 少于 2 次不算"错因明确"，返回 null
     */
    public String mainMisconception(long studentId, long kpId) {
        Map<Long, Exercise> exercises = kpExercises(kpId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        latestAnswers(studentId, exercises.keySet()).values().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsCorrect()))
                .map(a -> noteOf(exercises.get(a.getExerciseId()), a.getAnswer()))
                .filter(Objects::nonNull)
                .forEach(n -> counts.merge(n, 1, Integer::sum));
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_MISCONCEPTION)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    /**
     * 选 A 的练习：去掉最近一次已经做对的题，按 含主要误解的选项 > 上次做错 > 没做过 排，最多 3 道。
     * 只选已发布章节里的题
     */
    List<Long> pickExercises(long studentId, long kpId, String misconception) {
        Map<Long, Exercise> exercises = kpExercises(kpId);
        Set<Long> published = exercises.isEmpty() ? Set.of()
                : chapterMapper.selectByIds(exercises.values().stream().map(Exercise::getChapterId).distinct().toList())
                .stream().filter(ch -> "published".equals(ch.getStatus())).map(Chapter::getId)
                .collect(Collectors.toSet());
        Map<Long, AnswerRecord> latest = latestAnswers(studentId, exercises.keySet());
        return exercises.values().stream()
                .filter(e -> published.contains(e.getChapterId()))
                .filter(Exercise::recommendable)
                .filter(e -> latest.get(e.getId()) == null) // 只布置没做过的新题：掌握度只算第一次作答
                .sorted(Comparator.comparingInt((Exercise e) ->
                                misconception != null && notes(e).contains(misconception) ? 0 : 1)
                        .thenComparingInt(e -> e.getSortOrder() == null ? 0 : e.getSortOrder())
                        .thenComparingLong(Exercise::getId))
                .limit(MAX_EXERCISES)
                .map(Exercise::getId).toList();
    }

    /** 本章可以复习的视频和文档 */
    private List<ChapterItem> reviewItems(Long chapterId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                .eq(ChapterItem::getChapterId, chapterId)
                .in(ChapterItem::getType, "video", "document")
                .orderByAsc(ChapterItem::getSortOrder)
                .orderByAsc(ChapterItem::getId));
    }

    private Map<Long, Exercise> kpExercises(long kpId) {
        List<Long> ids = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getKnowledgePointId, kpId))
                .stream().map(ExerciseKnowledgePoint::getExerciseId).toList();
        return ids.isEmpty() ? Map.of()
                : exerciseMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e, (a, b) -> a, LinkedHashMap::new));
    }

    /** 每道题最近一次作答 */
    private Map<Long, AnswerRecord> latestAnswers(long studentId, Set<Long> exerciseIds) {
        Map<Long, AnswerRecord> map = new HashMap<>();
        if (exerciseIds.isEmpty()) {
            return map;
        }
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId)
                        .in(AnswerRecord::getExerciseId, exerciseIds)
                        .orderByAsc(AnswerRecord::getSubmittedAt)
                        .orderByAsc(AnswerRecord::getId))
                .forEach(a -> map.put(a.getExerciseId(), a));
        return map;
    }

    private List<String> notes(Exercise e) {
        if (e == null || e.getOptionNotes() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(e.getOptionNotes(), new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    /** 选了这个答案（"B"）的误解说明 */
    private String noteOf(Exercise e, String answer) {
        if (answer == null || answer.length() != 1) {
            return null;
        }
        List<String> notes = notes(e);
        int idx = answer.charAt(0) - 'A';
        String note = idx >= 0 && idx < notes.size() ? notes.get(idx) : null;
        return note == null || note.isBlank() ? null : note;
    }

    // ------------------------------------------------------------------ 老师：审核

    /** 通过（可改题目、留言）或驳回。驳回 = 这次不干预，预警标为忽略 */
    @Transactional
    public InterventionView review(Long id, ReviewRequest req) {
        Intervention iv = requireOwned(id);
        if (!Intervention.PENDING.equals(iv.getStatus())) {
            throw BusinessException.conflict("这条干预已经审核过了");
        }
        Alert alert = iv.getAlertId() == null ? null : alertMapper.selectById(iv.getAlertId());
        if (req == null || req.approve() == null) {
            throw BusinessException.badRequest("请选择通过或驳回");
        }
        if (!req.approve()) {
            iv.setStatus(Intervention.REJECTED);
            iv.setReviewerId(courseService.currentTeacherId());
            iv.setReviewedAt(LocalDateTime.now());
            interventionMapper.updateById(iv);
            closeAlert(alert, Alert.IGNORED);
            return view(iv, true);
        }

        if (req.exerciseIds() != null && !Intervention.REVIEW.equals(iv.getType())) {
            Map<Long, Exercise> allowed = kpExercises(iv.getKnowledgePointId());
            List<Long> ids = req.exerciseIds().stream().distinct().toList();
            if (ids.isEmpty() || !allowed.keySet().containsAll(ids)) {
                throw BusinessException.badRequest("只能布置这个知识点下的题，且至少一道");
            }
            iv.setExerciseIds(join(ids));
        }
        if (!Intervention.REVIEW.equals(iv.getType()) && split(iv.getExerciseIds()).isEmpty()) {
            throw BusinessException.badRequest("这个知识点下没有可以布置的题，请先在建课页加题");
        }
        if (req.note() != null && !req.note().isBlank()) {
            if (req.note().length() > 500) {
                throw BusinessException.badRequest("留言不超过 500 字");
            }
            iv.setNote(req.note().trim());
        }
        start(iv, alert, courseService.currentTeacherId(), checkDue(req.dueAt()));
        return view(iv, true);
    }

    /** 执行中的干预改期限（延期或提前）。只能改执行中的，期限要晚于现在 */
    @Transactional
    public InterventionView changeDue(Long id, LocalDateTime dueAt) {
        Intervention iv = requireOwned(id);
        if (!Intervention.APPROVED.equals(iv.getStatus())) {
            throw BusinessException.conflict("只能修改执行中的干预的期限");
        }
        if (dueAt == null) {
            throw BusinessException.badRequest("请选择新的期限");
        }
        iv.setDueAt(checkDue(dueAt));
        interventionMapper.updateById(iv);
        return view(iv, true);
    }

    /** 老师指定的期限：要晚于现在，最长 60 天；秒数抹掉。null = 用默认 */
    private static LocalDateTime checkDue(LocalDateTime dueAt) {
        if (dueAt == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!dueAt.isAfter(now)) {
            throw BusinessException.badRequest("期限要晚于现在");
        }
        if (dueAt.isAfter(now.plusDays(MAX_DUE_DAYS))) {
            throw BusinessException.badRequest("期限最长 " + MAX_DUE_DAYS + " 天");
        }
        return dueAt.withSecond(0).withNano(0);
    }

    /**
     * 老师撤回一条执行中的干预（主要用于系统自动执行的）。
     * 学生那边"老师布置"里不再显示；还没做的题交回系统推荐，之后照常按薄弱知识点推。
     */
    @Transactional
    public InterventionView revoke(Long id) {
        Intervention iv = requireOwned(id);
        if (!Intervention.APPROVED.equals(iv.getStatus())) {
            throw BusinessException.conflict("只能撤回执行中的干预");
        }
        iv.setStatus(Intervention.REVOKED);
        iv.setCompletedAt(LocalDateTime.now());
        interventionMapper.updateById(iv);
        returnToSystem(iv);
        return view(iv, true);
    }

    /** 这条干预布置的、还没做的题，改回系统推荐 */
    private void returnToSystem(Intervention iv) {
        recommendationMapper.selectList(new LambdaQueryWrapper<Recommendation>()
                        .eq(Recommendation::getInterventionId, iv.getId())
                        .eq(Recommendation::getStatus, Recommendation.NEW))
                .forEach(r -> {
                    r.setSource(Recommendation.SYSTEM);
                    r.setReason("上次做错了");
                    recommendationMapper.updateById(r);
                });
    }

    /** 忽略预警：这次不管，它的系统建议一起驳回 */
    @Transactional
    public void ignoreAlert(Long alertId) {
        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BusinessException.notFound("预警");
        }
        courseService.requireOwned(alert.getCourseId());
        if (!Alert.OPEN.equals(alert.getStatus())) {
            throw BusinessException.conflict("这条预警已经处理过了");
        }
        closeAlert(alert, Alert.IGNORED);
        interventionMapper.selectList(new LambdaQueryWrapper<Intervention>()
                        .eq(Intervention::getAlertId, alertId)
                        .eq(Intervention::getStatus, Intervention.PENDING))
                .forEach(iv -> {
                    iv.setStatus(Intervention.REJECTED);
                    interventionMapper.updateById(iv);
                });
    }

    private void closeAlert(Alert alert, String status) {
        if (alert != null && Alert.OPEN.equals(alert.getStatus())) {
            alert.setStatus(status);
            alert.setHandledAt(LocalDateTime.now());
            alertMapper.updateById(alert);
        }
    }

    private Intervention requireOwned(Long id) {
        Intervention iv = interventionMapper.selectById(id);
        if (iv == null) {
            throw BusinessException.notFound("干预");
        }
        courseService.requireOwned(iv.getCourseId());
        return iv;
    }

    /** 把题写进学生的推荐表，标为老师布置；之前推荐过或做过的同一道题也改成老师布置、重新待做 */
    private void assign(Intervention iv) {
        for (Long exerciseId : split(iv.getExerciseIds())) {
            Recommendation r = recommendationMapper.selectOne(new LambdaQueryWrapper<Recommendation>()
                    .eq(Recommendation::getStudentId, iv.getStudentId())
                    .eq(Recommendation::getTargetType, Recommendation.EXERCISE)
                    .eq(Recommendation::getTargetId, exerciseId));
            boolean isNew = r == null;
            if (isNew) {
                r = new Recommendation();
                r.setStudentId(iv.getStudentId());
                r.setTargetType(Recommendation.EXERCISE);
                r.setTargetId(exerciseId);
            }
            r.setKnowledgePointId(iv.getKnowledgePointId());
            // 系统自动执行的（没有审核人）不能说成“老师布置”
            r.setReason(iv.getReviewerId() == null ? "系统根据你的情况布置" : "老师布置");
            r.setStatus(Recommendation.NEW);
            r.setSource(Recommendation.TEACHER);
            r.setInterventionId(iv.getId());
            if (isNew) {
                recommendationMapper.insert(r);
            } else {
                recommendationMapper.updateById(r);
            }
        }
    }

    // ------------------------------------------------------------------ 执行和看效果

    /** 执行中的干预：检查学生做完没有；做完记下前后数据。AB 第 1 步做完后推出第 2 步的题 */
    void refresh(Intervention iv) {
        if (!Intervention.APPROVED.equals(iv.getStatus())) {
            return;
        }
        long student = iv.getStudentId();
        long kp = iv.getKnowledgePointId();
        switch (iv.getType()) {
            case Intervention.PRACTICE -> {
                if (allAnswered(student, split(iv.getExerciseIds()), iv.getReviewedAt())) {
                    finishByMastery(iv);
                }
            }
            case Intervention.REVIEW -> {
                if (reviewDone(iv)) {
                    String after = layerService.concept(student, kp, iv.getReviewedAt()).state();
                    finish(iv, after, LayerService.GOOD.equals(after) || LayerService.FAIR.equals(after));
                }
            }
            default -> { // REVIEW_PRACTICE
                if (iv.getStep() == 1 && reviewDone(iv)) {
                    iv.setStep(2);
                    iv.setStep2At(LocalDateTime.now());
                    interventionMapper.updateById(iv);
                    assign(iv);
                } else if (iv.getStep() == 2 && allAnswered(student, split(iv.getExerciseIds()), iv.getStep2At())) {
                    finishByMastery(iv);
                }
            }
        }
    }

    private void finishByMastery(Intervention iv) {
        Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, iv.getStudentId())
                .eq(Mastery::getKnowledgePointId, iv.getKnowledgePointId()));
        // 有效 = 这次布置的新题（够 2 道）第一次作答平均 ≥ 0.8，和“已补回来”同一个标准
        List<Double> scores = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, iv.getStudentId())
                        .in(AnswerRecord::getExerciseId, split(iv.getExerciseIds()))
                        .orderByAsc(AnswerRecord::getSubmittedAt).orderByAsc(AnswerRecord::getId))
                .forEach(a -> {
                    if (seen.add(a.getExerciseId()) && !"pending".equals(a.getStatus())) {
                        scores.add(a.getScore() != null ? a.getScore() : Boolean.TRUE.equals(a.getIsCorrect()) ? 1.0 : 0.0);
                    }
                });
        Double avg = MasteryService.compute(scores);
        finish(iv, masteryText(iv.getStudentId(), iv.getKnowledgePointId()),
                m != null && scores.size() >= MasteryService.MIN_REMEDY && avg >= MasteryService.RECOVERED);
    }

    private void finish(Intervention iv, String after, boolean effective) {
        iv.setStatus(Intervention.DONE);
        iv.setAfterValue(after);
        iv.setEffective(effective);
        iv.setCompletedAt(LocalDateTime.now());
        interventionMapper.updateById(iv);
        // 没做对的题还给系统推荐，之后照常按薄弱知识点推
        returnToSystem(iv);
    }

    /** 每道题在 since 之后都至少交过一次（不要求做对，否则做不对就永远结束不了） */
    private boolean allAnswered(long studentId, List<Long> exerciseIds, LocalDateTime since) {
        if (exerciseIds.isEmpty() || since == null) {
            return false;
        }
        return answeredSince(studentId, exerciseIds, since).size() == exerciseIds.size();
    }

    private Set<Long> answeredSince(long studentId, List<Long> exerciseIds, LocalDateTime since) {
        if (exerciseIds.isEmpty() || since == null) {
            return Set.of();
        }
        return answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getStudentId, studentId)
                        .in(AnswerRecord::getExerciseId, exerciseIds)
                        .ge(AnswerRecord::getSubmittedAt, since))
                .stream().map(AnswerRecord::getExerciseId).collect(Collectors.toSet());
    }

    /** B 的第 1 步：复习内容都看过，并且之后重新做完了一次这一章的对话 */
    private boolean reviewDone(Intervention iv) {
        List<Long> items = split(iv.getItemIds());
        return viewedSince(iv.getStudentId(), items, iv.getReviewedAt()).size() == items.size()
                && dialogueFinishedSince(iv) != null;
    }

    private Set<Long> viewedSince(long studentId, List<Long> itemIds, LocalDateTime since) {
        if (itemIds.isEmpty() || since == null) {
            return Set.of();
        }
        return eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getStudentId, studentId)
                        .in(LearningEvent::getTargetId, itemIds)
                        .ge(LearningEvent::getCreatedAt, since))
                .stream().map(LearningEvent::getTargetId).collect(Collectors.toSet());
    }

    /** 干预通过之后开始、已经结束的这一章对话 */
    private DialogueSession dialogueFinishedSince(Intervention iv) {
        KnowledgePoint kp = kpMapper.selectById(iv.getKnowledgePointId());
        if (kp == null || iv.getReviewedAt() == null) {
            return null;
        }
        return sessionMapper.selectOne(new LambdaQueryWrapper<DialogueSession>()
                .eq(DialogueSession::getStudentId, iv.getStudentId())
                .eq(DialogueSession::getChapterId, kp.getChapterId())
                .eq(DialogueSession::getStatus, DialogueSession.FINISHED)
                .ge(DialogueSession::getCreatedAt, iv.getReviewedAt())
                .orderByDesc(DialogueSession::getId)
                .last("LIMIT 1"));
    }

    // ------------------------------------------------------------------ 学生

    /** 老师布置给我的：执行中的，和最近 7 天完成的 */
    @Transactional
    public MyAssignments mine(long studentId) {
        List<Intervention> list = interventionMapper.selectList(new LambdaQueryWrapper<Intervention>()
                .eq(Intervention::getStudentId, studentId)
                .in(Intervention::getStatus, Intervention.APPROVED, Intervention.DONE)
                .orderByDesc(Intervention::getId));
        list.forEach(this::refresh);
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        return new MyAssignments(
                list.stream().filter(iv -> Intervention.APPROVED.equals(iv.getStatus()))
                        .map(iv -> view(iv, false)).toList(),
                list.stream().filter(iv -> Intervention.DONE.equals(iv.getStatus())
                                && iv.getCompletedAt() != null && iv.getCompletedAt().isAfter(weekAgo))
                        .map(iv -> view(iv, false)).toList());
    }

    // ------------------------------------------------------------------ 组装返回

    private AlertView alertView(Alert a, Intervention suggestion) {
        KnowledgePoint kp = kpMapper.selectById(a.getKnowledgePointId());
        Chapter ch = kp == null ? null : chapterMapper.selectById(kp.getChapterId());
        return new AlertView(a.getId(), a.getStudentId(), courseMapper.findUserName(a.getStudentId()),
                a.getKnowledgePointId(), kp == null ? null : kp.getName(), ch == null ? null : ch.getTitle(),
                a.getType(), a.getLevel(), a.getMessage(), a.getStatus(), fromJson(a.getEvidence()),
                suggestion == null ? null : view(suggestion, true), a.getCreatedAt());
    }

    /**
     * forTeacher = false 时（学生看）：AB 还在第 1 步就不显示练习
     */
    private InterventionView view(Intervention iv, boolean forTeacher) {
        KnowledgePoint kp = kpMapper.selectById(iv.getKnowledgePointId());
        LocalDateTime practiceSince = Intervention.REVIEW_PRACTICE.equals(iv.getType()) ? iv.getStep2At()
                : iv.getReviewedAt();
        List<Long> exerciseIds = split(iv.getExerciseIds());
        boolean hideExercises = !forTeacher && Intervention.REVIEW_PRACTICE.equals(iv.getType()) && iv.getStep() == 1;
        List<AssignedExercise> exercises = new ArrayList<>();
        if (!hideExercises && !exerciseIds.isEmpty()) {
            Set<Long> answered = answeredSince(iv.getStudentId(), exerciseIds, practiceSince);
            Map<Long, AnswerRecord> latest = latestAnswers(iv.getStudentId(), Set.copyOf(exerciseIds));
            Map<Long, Long> recIds = recommendationMapper.selectList(new LambdaQueryWrapper<Recommendation>()
                            .eq(Recommendation::getStudentId, iv.getStudentId())
                            .eq(Recommendation::getTargetType, Recommendation.EXERCISE)
                            .in(Recommendation::getTargetId, exerciseIds))
                    .stream().collect(Collectors.toMap(Recommendation::getTargetId, Recommendation::getId));
            for (Exercise e : exerciseMapper.selectByIds(exerciseIds)) {
                ExerciseView v = exerciseService.toView(e);
                AnswerRecord last = latest.get(e.getId());
                boolean done = answered.contains(e.getId());
                exercises.add(new AssignedExercise(e.getId(), recIds.get(e.getId()), v.stem(), v.options(),
                        v.difficulty(), done, done && last != null && Boolean.TRUE.equals(last.getIsCorrect())));
            }
            exercises.sort(Comparator.comparingInt(x -> exerciseIds.indexOf(x.exerciseId())));
        }

        List<AssignedItem> items = new ArrayList<>();
        AssignedDialogue dialogue = null;
        if (!Intervention.PRACTICE.equals(iv.getType()) && kp != null) {
            List<Long> itemIds = split(iv.getItemIds());
            Set<Long> viewed = viewedSince(iv.getStudentId(), itemIds, iv.getReviewedAt());
            if (!itemIds.isEmpty()) {
                itemMapper.selectByIds(itemIds).stream()
                        .sorted(Comparator.comparingInt(i -> itemIds.indexOf(i.getId())))
                        .forEach(i -> items.add(new AssignedItem(i.getId(), i.getChapterId(), i.getCourseId(),
                                i.getType(), i.getTitle(), viewed.contains(i.getId()))));
            }
            ChapterItem dialogueItem = itemMapper.selectOne(new LambdaQueryWrapper<ChapterItem>()
                    .eq(ChapterItem::getChapterId, kp.getChapterId())
                    .eq(ChapterItem::getType, "dialogue")
                    .orderByAsc(ChapterItem::getSortOrder)
                    .last("LIMIT 1"));
            dialogue = new AssignedDialogue(kp.getChapterId(), dialogueItem == null ? null : dialogueItem.getId(),
                    kp.getCourseId(), dialogueItem == null ? "本章 AI 对话" : dialogueItem.getTitle(),
                    dialogueFinishedSince(iv) != null);
        }
        return new InterventionView(iv.getId(), iv.getAlertId(), iv.getStudentId(),
                courseMapper.findUserName(iv.getStudentId()), iv.getKnowledgePointId(),
                kp == null ? null : kp.getName(), iv.getType(), iv.getStep() == null ? 1 : iv.getStep(),
                iv.getNote(), iv.getSource(), iv.getStatus(),
                iv.getReviewedAt() != null && iv.getReviewerId() == null,   // 开始执行了但没有审核人 = 系统自动
                exercises, items, dialogue, iv.getBeforeValue(),
                iv.getAfterValue(), iv.getEffective(), iv.getReviewedAt(), iv.getCompletedAt(),
                iv.getDueAt(), overdue(iv), late(iv));
    }

    /** 执行中、已经过了期限 */
    static boolean overdue(Intervention iv) {
        return Intervention.APPROVED.equals(iv.getStatus()) && iv.getDueAt() != null
                && LocalDateTime.now().isAfter(iv.getDueAt());
    }

    /** 已完成，但完成时间晚于期限 */
    static boolean late(Intervention iv) {
        return Intervention.DONE.equals(iv.getStatus()) && iv.getDueAt() != null && iv.getCompletedAt() != null
                && iv.getCompletedAt().isAfter(iv.getDueAt());
    }

    // ------------------------------------------------------------------ 小工具

    private String masteryText(long studentId, long kpId) {
        Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId)
                .eq(Mastery::getKnowledgePointId, kpId));
        return m == null ? null : String.format("%.2f", m.getMastery());
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        return a == null ? b : b == null ? a : a.isAfter(b) ? a : b;
    }

    private static String join(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> split(String ids) {
        return ids == null || ids.isBlank() ? List.of()
                : Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Evidence fromJson(String json) {
        try {
            return json == null ? null : objectMapper.readValue(json, Evidence.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
