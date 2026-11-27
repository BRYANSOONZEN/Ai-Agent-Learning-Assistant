package com.se.platform.intervention.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.CourseService;
import com.se.platform.course.service.Grader;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.intervention.dto.ClassAnalysisDtos.ClassBoard;
import com.se.platform.intervention.dto.ClassAnalysisDtos.IssueView;
import com.se.platform.intervention.dto.ClassAnalysisDtos.ItemBoard;
import com.se.platform.intervention.dto.ClassAnalysisDtos.KpGroup;
import com.se.platform.intervention.dto.ClassAnalysisDtos.StudentRef;
import com.se.platform.intervention.entity.ClassIssue;
import com.se.platform.intervention.entity.ItemAnalysis;
import com.se.platform.intervention.mapper.ClassIssueMapper;
import com.se.platform.intervention.mapper.ItemAnalysisMapper;
import com.se.platform.material.dto.KpDtos.KeyPoint;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
 * 老师干预：每次练习的全班分析（docs/模块说明/老师干预-全班分析.md）。
 * <ol>
 *   <li>时机：到截止时间，或全班都提前交完；截止前交卷人数 ÷ 全班 ≥ 80% 才分析，否则作废（提醒老师）</li>
 *   <li>只算截止前的第一次作答</li>
 *   <li>规则 ①：一道题错误率 > 80% → 先给老师检查</li>
 *   <li>规则 ②：要点 × 考查类型错误率 > 50% → 卡片 + 推荐方案（按考查类型），推送给做错的学生</li>
 *   <li>规则 ① 的题在等老师检查时不算进规则 ②；老师说“没问题”后算回去</li>
 * </ol>
 * 打开干预页时现算（没分析过、或者截止时间改了才重新分析），结果存在 item_analysis、class_issue。
 */
@Service
public class ClassAnalysisService {

    /** 选课：没加入这门课的学生不能学（字段注入，单元测试里直接 new 时为空） */
    @org.springframework.beans.factory.annotation.Autowired
    private com.se.platform.course.service.EnrollmentService enrollment;


    static final double QUESTION_RATE = 0.8;
    static final double CELL_RATE = 0.5;

    /** 考查类型 → 推荐的讲解形式、建议 */
    static final Map<String, String[]> METHODS = Map.of(
            "concept", new String[] {"对比讲解", "把容易混的概念放在一起讲区别，各举一个例子；再出几道辨析题"},
            "scenario", new String[] {"例题讲解", "用一个新场景，分步讲“看到什么 → 想到哪个概念 → 选什么方法”；再出几道新场景的情境题"},
            "data", new String[] {"示范讲解", "给一组数据，示范先看什么、比较什么、得出什么结论；再出几道数据分析题"},
            "process", new String[] {"步骤讲解", "按顺序讲每一步做什么、为什么在这一步；再出几道步骤题"},
            "custom", new String[] {"按老师的要求", "按你出题时写的要求补充讲解；再出几道同类的题"});
    static final Map<String, String> ASSESS = Map.of("concept", "概念辨析", "scenario", "情境应用", "data", "数据分析",
            "process", "流程步骤", "custom", "老师定制");

    private final double minSubmitRatio;
    private final ItemAnalysisMapper analysisMapper;
    private final ClassIssueMapper issueMapper;
    private final ChapterItemMapper itemMapper;
    private final ChapterMapper chapterMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final KnowledgePointMapper kpMapper;
    private final ChapterKpMapper chapterKpMapper;
    private final AnswerRecordMapper answerMapper;
    private final UserMapper userMapper;
    private final CourseService courseService;
    private final com.se.platform.course.mapper.CourseMapper courseMapper;
    private final MasteryService masteryService;
    private final ObjectMapper json;
    /** 已推送的干预（由 ClassPushService 注册，避免两个服务互相注入） */
    private java.util.function.Function<Long, List<com.se.platform.intervention.dto.ClassAnalysisDtos.PushView>> pushViews;

    public void setPushViews(java.util.function.Function<Long, List<com.se.platform.intervention.dto.ClassAnalysisDtos.PushView>> f) {
        this.pushViews = f;
    }

    public ClassAnalysisService(@Value("${platform.class-analysis.min-submit-ratio:0.8}") double minSubmitRatio,
                                ItemAnalysisMapper analysisMapper, ClassIssueMapper issueMapper,
                                ChapterItemMapper itemMapper, ChapterMapper chapterMapper,
                                ExerciseMapper exerciseMapper, ExerciseKnowledgePointMapper linkMapper,
                                KnowledgePointMapper kpMapper, ChapterKpMapper chapterKpMapper,
                                AnswerRecordMapper answerMapper, UserMapper userMapper, CourseService courseService,
                                com.se.platform.course.mapper.CourseMapper courseMapper,
                                MasteryService masteryService, ObjectMapper json) {
        this.courseMapper = courseMapper;
        this.minSubmitRatio = minSubmitRatio;
        this.analysisMapper = analysisMapper;
        this.issueMapper = issueMapper;
        this.itemMapper = itemMapper;
        this.chapterMapper = chapterMapper;
        this.exerciseMapper = exerciseMapper;
        this.linkMapper = linkMapper;
        this.kpMapper = kpMapper;
        this.chapterKpMapper = chapterKpMapper;
        this.answerMapper = answerMapper;
        this.userMapper = userMapper;
        this.courseService = courseService;
        this.masteryService = masteryService;
        this.json = json;
    }

    // ------------------------------------------------------------------ 干预页

    @Transactional
    public ClassBoard board(Long courseId) {
        Course course = courseService.requireOwned(courseId);
        List<Chapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCourseId, courseId).orderByAsc(Chapter::getSortOrder).orderByAsc(Chapter::getId));
        List<User> students = students(courseId);
        List<ItemBoard> items = new ArrayList<>();
        for (Chapter ch : chapters) {
            for (ChapterItem it : itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                    .eq(ChapterItem::getChapterId, ch.getId()).eq(ChapterItem::getType, "exercise")
                    .orderByAsc(ChapterItem::getSortOrder).orderByAsc(ChapterItem::getId))) {
                items.add(itemBoard(ch, it, students, LocalDateTime.now()));
            }
        }
        return new ClassBoard(courseId, course.getName(), minSubmitRatio, items);
    }

    ItemBoard itemBoard(Chapter ch, ChapterItem it, List<User> students, LocalDateTime now) {
        if (it.getDueAt() == null) {
            return new ItemBoard(it.getId(), it.getTitle(), ch.getId(), ch.getTitle(), null, students.size(), 0,
                    "nodue", List.of(), List.of(), List.of());
        }
        Map<Long, Map<Long, AnswerRecord>> firsts = firstAttempts(it, students);
        Set<Long> submitted = new LinkedHashSet<>();
        firsts.values().forEach(m -> submitted.addAll(m.keySet()));
        boolean ready = !now.isBefore(it.getDueAt()) || submitted.size() >= students.size() && !students.isEmpty();
        if (!ready) {
            return new ItemBoard(it.getId(), it.getTitle(), ch.getId(), ch.getTitle(), it.getDueAt(), students.size(),
                    submitted.size(), "waiting", List.of(), List.of(), List.of());
        }
        ItemAnalysis a = analysisMapper.selectOne(new LambdaQueryWrapper<ItemAnalysis>()
                .eq(ItemAnalysis::getItemId, it.getId()).orderByDesc(ItemAnalysis::getId).last("LIMIT 1"));
        if (a == null || !it.getDueAt().equals(a.getDueAt())) {
            a = analyse(it, students, firsts, submitted.size());
        }
        if (ItemAnalysis.VOID.equals(a.getStatus())) {
            List<StudentRef> missing = students.stream().filter(s -> !submitted.contains(s.getId()))
                    .map(s -> new StudentRef(s.getId(), s.getName())).toList();
            return new ItemBoard(it.getId(), it.getTitle(), ch.getId(), ch.getTitle(), it.getDueAt(), students.size(),
                    a.getSubmitted(), ItemAnalysis.VOID, missing, List.of(), List.of());
        }
        return new ItemBoard(it.getId(), it.getTitle(), ch.getId(), ch.getTitle(), it.getDueAt(), students.size(),
                a.getSubmitted(), ItemAnalysis.DONE, List.of(), groups(a.getId()),
                pushViews == null ? List.of() : pushViews.apply(it.getId()));
    }

    /** 侧边栏数字：当前老师所有课程里，分析作废的练习 + 待处理的卡片 */
    @Transactional
    public int openCount() {
        int n = 0;
        for (Course c : courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, courseService.currentTeacherId()))) {
            for (ItemBoard it : board(c.getId()).items()) {
                n += ItemAnalysis.VOID.equals(it.status()) ? 1 : (int) it.groups().stream()
                        .flatMap(g -> g.issues().stream()).filter(i -> ClassIssue.OPEN.equals(i.status())).count();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 分析

    /** 截止前每道题每个学生的第一次作答：题目 id → 学生 id → 作答 */
    public Map<Long, Map<Long, AnswerRecord>> firstAttempts(ChapterItem it, List<User> students) {
        List<Long> ids = questions(it.getId()).stream().map(Exercise::getId).toList();
        Map<Long, Map<Long, AnswerRecord>> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Set<Long> studentIds = students.stream().map(User::getId).collect(Collectors.toSet());
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .in(AnswerRecord::getExerciseId, ids)
                        .eq(AnswerRecord::getItemId, it.getId())
                        .orderByAsc(AnswerRecord::getSubmittedAt).orderByAsc(AnswerRecord::getId))
                .forEach(a -> {
                    if (!studentIds.contains(a.getStudentId())) {
                        return;
                    }
                    Map<Long, AnswerRecord> m = out.computeIfAbsent(a.getExerciseId(), k -> new LinkedHashMap<>());
                    // 每个学生每道题只看第一次；第一次在截止之后的（迟交）不算进全班分析
                    if (!m.containsKey(a.getStudentId())) {
                        m.put(a.getStudentId(), a);
                    }
                });
        out.values().forEach(m -> m.values().removeIf(a -> a.getSubmittedAt() != null
                && a.getSubmittedAt().isAfter(it.getDueAt())));
        return out;
    }

    private ItemAnalysis analyse(ChapterItem it, List<User> students, Map<Long, Map<Long, AnswerRecord>> firsts,
                                 int submitted) {
        ItemAnalysis a = new ItemAnalysis();
        a.setItemId(it.getId());
        a.setDueAt(it.getDueAt());
        a.setStudents(students.size());
        a.setSubmitted(submitted);
        a.setAnalyzedAt(LocalDateTime.now());
        boolean enough = !students.isEmpty() && (double) submitted / students.size() >= minSubmitRatio;
        a.setStatus(enough ? ItemAnalysis.DONE : ItemAnalysis.VOID);
        analysisMapper.insert(a);
        if (!enough) {
            return a;
        }
        // 规则 ①：一道题错误率 > 80%
        for (Exercise e : questions(it.getId())) {
            Map<Long, AnswerRecord> m = firsts.getOrDefault(e.getId(), Map.of());
            List<Long> wrong = wrongStudents(m.values());
            if (!m.isEmpty() && (double) wrong.size() / m.size() > QUESTION_RATE) {
                ClassIssue q = issue(a, it, ClassIssue.QUESTION, kpOf(e.getId()), e.getKeyPointNo(), assess(e));
                q.setExerciseId(e.getId());
                q.setAttempts(m.size());
                q.setWrong(wrong.size());
                q.setStudentIds(join(wrong));
                issueMapper.insert(q);
            }
        }
        cells(a, it, firsts);
        return a;
    }

    /**
     * 规则 ②：要点 × 考查类型。等老师检查的题（规则 ① 待处理、已作废、已修改）不算；
     * 已经推送或忽略过的同一格不再重新出。
     */
    void cells(ItemAnalysis a, ChapterItem it, Map<Long, Map<Long, AnswerRecord>> firsts) {
        List<ClassIssue> existing = issueMapper.selectList(new LambdaQueryWrapper<ClassIssue>()
                .eq(ClassIssue::getAnalysisId, a.getId()));
        Set<Long> excluded = existing.stream().filter(i -> ClassIssue.QUESTION.equals(i.getKind())
                        && !ClassIssue.OK.equals(i.getStatus()))
                .map(ClassIssue::getExerciseId).collect(Collectors.toSet());
        Set<String> handled = existing.stream().filter(i -> ClassIssue.CELL.equals(i.getKind())
                        && !ClassIssue.OPEN.equals(i.getStatus()))
                .map(i -> cellKey(i.getKnowledgePointId(), i.getKeyPointNo(), i.getAssessType()))
                .collect(Collectors.toSet());
        existing.stream().filter(i -> ClassIssue.CELL.equals(i.getKind()) && ClassIssue.OPEN.equals(i.getStatus()))
                .forEach(i -> issueMapper.deleteById(i.getId()));

        Map<String, List<AnswerRecord>> byCell = new LinkedHashMap<>();
        Map<String, Exercise> sample = new LinkedHashMap<>();
        for (Exercise e : questions(it.getId())) {
            if (excluded.contains(e.getId())) {
                continue;
            }
            String key = cellKey(kpOf(e.getId()), e.getKeyPointNo(), assess(e));
            byCell.computeIfAbsent(key, k -> new ArrayList<>()).addAll(firsts.getOrDefault(e.getId(), Map.of()).values());
            sample.putIfAbsent(key, e);
        }
        for (Map.Entry<String, List<AnswerRecord>> c : byCell.entrySet()) {
            List<AnswerRecord> all = c.getValue();
            long wrong = all.stream().filter(x -> score(x) < 1).count();
            if (all.isEmpty() || handled.contains(c.getKey()) || (double) wrong / all.size() <= CELL_RATE) {
                continue;
            }
            Exercise e = sample.get(c.getKey());
            ClassIssue i = issue(a, it, ClassIssue.CELL, kpOf(e.getId()), e.getKeyPointNo(), assess(e));
            i.setAttempts(all.size());
            i.setWrong((int) wrong);
            i.setStudentIds(join(wrongStudents(all)));
            issueMapper.insert(i);
        }
    }

    // ------------------------------------------------------------------ 题目检查（规则 ①）

    /** 老师检查后：题目没问题 → 算回规则 ② 重新算 */
    @Transactional
    public void questionOk(Long issueId) {
        ClassIssue q = requireQuestion(issueId, ClassIssue.OK);
        recomputeCells(q);
    }

    /** 老师已经修改了这道题：旧的作答还是不算进规则 ② */
    @Transactional
    public void questionFixed(Long issueId) {
        requireQuestion(issueId, ClassIssue.FIXED);
    }

    /** 作废：这道题删掉，不算进任何人的掌握度，做过的学生重算 */
    @Transactional
    public void questionVoid(Long issueId) {
        ClassIssue q = requireQuestion(issueId, ClassIssue.VOIDED);
        Long exerciseId = q.getExerciseId();
        List<Long> kpIds = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getExerciseId, exerciseId))
                .stream().map(ExerciseKnowledgePoint::getKnowledgePointId).toList();
        Set<Long> studentIds = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getExerciseId, exerciseId))
                .stream().map(AnswerRecord::getStudentId).collect(Collectors.toSet());
        linkMapper.delete(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                .eq(ExerciseKnowledgePoint::getExerciseId, exerciseId));
        exerciseMapper.deleteById(exerciseId);
        for (Long s : studentIds) {
            masteryService.updateKps(s, kpIds);
        }
        recomputeCells(q);
    }

    @Transactional
    public void ignore(Long issueId) {
        ClassIssue i = requireOwnedIssue(issueId);
        i.setStatus(ClassIssue.IGNORED);
        issueMapper.updateById(i);
    }

    private void recomputeCells(ClassIssue q) {
        ItemAnalysis a = analysisMapper.selectById(q.getAnalysisId());
        ChapterItem it = itemMapper.selectById(q.getItemId());
        cells(a, it, firstAttempts(it, students(it.getCourseId())));
    }

    private ClassIssue requireQuestion(Long issueId, String newStatus) {
        ClassIssue q = requireOwnedIssue(issueId);
        if (!ClassIssue.QUESTION.equals(q.getKind()) || !ClassIssue.OPEN.equals(q.getStatus())) {
            throw BusinessException.badRequest("这道题已经处理过了");
        }
        q.setStatus(newStatus);
        issueMapper.updateById(q);
        return q;
    }

    public ClassIssue requireOwnedIssue(Long issueId) {
        ClassIssue i = issueId == null ? null : issueMapper.selectById(issueId);
        if (i == null) {
            throw BusinessException.notFound("问题");
        }
        courseService.requireOwned(i.getCourseId());
        return i;
    }

    // ------------------------------------------------------------------ 给页面看

    private List<KpGroup> groups(Long analysisId) {
        List<ClassIssue> issues = issueMapper.selectList(new LambdaQueryWrapper<ClassIssue>()
                .eq(ClassIssue::getAnalysisId, analysisId).orderByAsc(ClassIssue::getId));
        Map<Long, List<IssueView>> byKp = new LinkedHashMap<>();
        for (ClassIssue i : issues) {
            byKp.computeIfAbsent(i.getKnowledgePointId() == null ? 0L : i.getKnowledgePointId(), k -> new ArrayList<>())
                    .add(view(i));
        }
        List<KpGroup> out = new ArrayList<>();
        byKp.forEach((kpId, list) -> {
            KnowledgePoint kp = kpId == 0 ? null : kpMapper.selectById(kpId);
            // 题目检查在前，然后按错误率从高到低
            list.sort(Comparator.comparing((IssueView v) -> ClassIssue.QUESTION.equals(v.kind()) ? 0 : 1)
                    .thenComparing(IssueView::rate, Comparator.reverseOrder()));
            out.add(new KpGroup(kpId == 0 ? null : kpId, kp == null ? "（没有关联知识点）" : kp.getName(), list));
        });
        return out;
    }

    public IssueView view(ClassIssue i) {
        double rate = i.getAttempts() == 0 ? 0 : Math.round(100.0 * i.getWrong() / i.getAttempts()) / 100.0;
        String assess = i.getAssessType() == null ? "concept" : i.getAssessType();
        String stem = null;
        if (i.getExerciseId() != null) {
            Exercise e = exerciseMapper.selectById(i.getExerciseId());
            stem = e == null ? "（已删除）" : e.getStem();
        }
        String[] m = METHODS.getOrDefault(assess, METHODS.get("concept"));
        boolean question = ClassIssue.QUESTION.equals(i.getKind());
        String advice = question
                ? String.format("%d 人里 %d 人做错（%d%%）。先检查这道题：题目有问题就修改或作废；没问题就算回这条要点，按要点给推荐方案。",
                i.getAttempts(), i.getWrong(), Math.round(rate * 100))
                : String.format("%d 人次里错 %d 人次（%d%%），推送给做错的 %d 人。推荐：%s。", i.getAttempts(), i.getWrong(),
                Math.round(rate * 100), count(i.getStudentIds()), m[1]);
        return new IssueView(i.getId(), i.getKind(), i.getKeyPointNo(), keyPointText(i.getKnowledgePointId(),
                i.getKeyPointNo()), assess, ASSESS.getOrDefault(assess, assess), i.getExerciseId(), stem,
                i.getAttempts(), i.getWrong(), rate, count(i.getStudentIds()), i.getStatus(), question ? null : m[0],
                advice);
    }

    public String keyPointText(Long kpId, Integer no) {
        if (kpId == null || no == null) {
            return null;
        }
        ChapterKp ckp = chapterKpMapper.selectOne(new LambdaQueryWrapper<ChapterKp>()
                .eq(ChapterKp::getKnowledgePointId, kpId).last("LIMIT 1"));
        if (ckp == null || ckp.getKeyPoints() == null) {
            return null;
        }
        try {
            List<KeyPoint> points = json.readValue(ckp.getKeyPoints(), new TypeReference<List<KeyPoint>>() { });
            return no >= 1 && no <= points.size() ? points.get(no - 1).text() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 小工具

    /** 全班 = 选了这门课、账号在用的学生（和截止时间页一样） */
    public List<User> students(Long courseId) {
        return enrollment.students(courseId);
    }

    private List<Exercise> questions(Long itemId) {
        return exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>()
                .eq(Exercise::getItemId, itemId).eq(Exercise::getDraft, false)
                .orderByAsc(Exercise::getSortOrder).orderByAsc(Exercise::getId));
    }

    private ClassIssue issue(ItemAnalysis a, ChapterItem it, String kind, Long kpId, Integer keyPointNo, String assess) {
        ClassIssue i = new ClassIssue();
        i.setAnalysisId(a.getId());
        i.setItemId(it.getId());
        i.setCourseId(it.getCourseId());
        i.setKind(kind);
        i.setKnowledgePointId(kpId);
        i.setKeyPointNo(keyPointNo);
        i.setAssessType(assess);
        i.setStatus(ClassIssue.OPEN);
        return i;
    }

    private Long kpOf(Long exerciseId) {
        return linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getExerciseId, exerciseId))
                .stream().map(ExerciseKnowledgePoint::getKnowledgePointId).findFirst().orElse(null);
    }

    private static String assess(Exercise e) {
        return e.getAssessType() == null ? "concept" : e.getAssessType();
    }

    static String cellKey(Long kpId, Integer keyPointNo, String assess) {
        return kpId + "|" + keyPointNo + "|" + assess;
    }

    private static double score(AnswerRecord a) {
        if (Grader.PENDING.equals(a.getStatus())) {
            return 1; // 等老师确认的简答先不算错
        }
        return a.getScore() != null ? a.getScore() : Boolean.TRUE.equals(a.getIsCorrect()) ? 1 : 0;
    }

    private static List<Long> wrongStudents(java.util.Collection<AnswerRecord> list) {
        return list.stream().filter(a -> score(a) < 1).map(AnswerRecord::getStudentId).distinct().toList();
    }

    static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public static List<Long> split(String s) {
        return s == null || s.isBlank() ? List.of()
                : Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).toList();
    }

    private static int count(String ids) {
        return split(ids).size();
    }

    static boolean same(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
