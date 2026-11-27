package com.se.platform.intervention.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.assistant.entity.AssistantNotice;
import com.se.platform.assistant.mapper.AssistantNoticeMapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.dto.LearnDtos.QuizQuestion;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.CourseService;
import com.se.platform.course.service.ExerciseGenService;
import com.se.platform.course.service.ExerciseService;
import com.se.platform.course.service.Grader;
import com.se.platform.diagnosis.dto.DiagnosisDtos.MasteryChange;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.diagnosis.service.MasteryService;
import com.se.platform.feynman.service.FeynmanDraftService;
import com.se.platform.intervention.dto.ClassAnalysisDtos.Effect;
import com.se.platform.intervention.dto.ClassAnalysisDtos.MyPush;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushCreate;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushResult;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushUpdate;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushView;
import com.se.platform.intervention.dto.ClassAnalysisDtos.StudentRef;
import com.se.platform.intervention.entity.ClassIssue;
import com.se.platform.intervention.entity.ClassPush;
import com.se.platform.intervention.mapper.ClassIssueMapper;
import com.se.platform.intervention.mapper.ClassPushMapper;
import com.se.platform.material.dto.KpDtos.KeyPoint;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.service.KpRecognizer;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.service.RecordService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 老师全班干预的第 3～5 步（docs/模块说明/老师干预-全班分析.md 第四、五节）：
 * 处理卡片 → 补充讲解（大模型按考查类型起草，或老师自己写）+ 新题（大模型出、老师逐题确认）→ 推送给做错的学生
 * → 学生在小助手里看讲解、做新题（算进补练）→ 效果：推送前后的错误率。
 */
@Service
public class ClassPushService {

    static final int QUESTIONS = 3;
    static final int MATERIAL_CHARS = 3000;

    private final ClassPushMapper pushMapper;
    private final ClassIssueMapper issueMapper;
    private final ClassAnalysisService analysis;
    private final ChapterItemMapper itemMapper;
    private final CourseMapper courseMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final KnowledgePointMapper kpMapper;
    private final ChapterKpMapper chapterKpMapper;
    private final ExerciseService exerciseService;
    private final ExerciseGenService genService;
    private final AnswerRecordMapper answerMapper;
    private final MasteryMapper masteryMapper;
    private final MasteryService masteryService;
    private final RecordService recordService;
    private final UserMapper userMapper;
    private final AssistantNoticeMapper noticeMapper;
    private final KpRecognizer recognizer;
    private final FeynmanDraftService feynmanDraft;
    private final CourseService courseService;
    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate template = PromptTemplate.load("class_explain_v1");

    public ClassPushService(ClassPushMapper pushMapper, ClassIssueMapper issueMapper, ClassAnalysisService analysis,
                            ChapterItemMapper itemMapper, CourseMapper courseMapper, ExerciseMapper exerciseMapper,
                            ExerciseKnowledgePointMapper linkMapper, KnowledgePointMapper kpMapper,
                            ChapterKpMapper chapterKpMapper, ExerciseService exerciseService,
                            ExerciseGenService genService, AnswerRecordMapper answerMapper,
                            MasteryMapper masteryMapper, MasteryService masteryService, RecordService recordService,
                            UserMapper userMapper, AssistantNoticeMapper noticeMapper, KpRecognizer recognizer,
                            FeynmanDraftService feynmanDraft, CourseService courseService, AiCallLogService callLog,
                            ObjectMapper json) {
        this.pushMapper = pushMapper;
        this.issueMapper = issueMapper;
        this.analysis = analysis;
        this.itemMapper = itemMapper;
        this.courseMapper = courseMapper;
        this.exerciseMapper = exerciseMapper;
        this.linkMapper = linkMapper;
        this.kpMapper = kpMapper;
        this.chapterKpMapper = chapterKpMapper;
        this.exerciseService = exerciseService;
        this.genService = genService;
        this.answerMapper = answerMapper;
        this.masteryMapper = masteryMapper;
        this.masteryService = masteryService;
        this.recordService = recordService;
        this.userMapper = userMapper;
        this.noticeMapper = noticeMapper;
        this.recognizer = recognizer;
        this.feynmanDraft = feynmanDraft;
        this.courseService = courseService;
        this.callLog = callLog;
        this.json = json;
    }

    @PostConstruct
    void register() {
        analysis.setPushViews(this::pushesOf);
    }

    // ------------------------------------------------------------------ 老师：处理卡片

    /** 处理一张（或同一个知识点的几张）卡片：起草讲解（可选）+ 出新题，存成草稿给老师改 */
    @Transactional
    public PushView create(PushCreate req) {
        if (req == null || req.issueIds() == null || req.issueIds().isEmpty()) {
            throw BusinessException.badRequest("请选择要处理的卡片");
        }
        List<ClassIssue> issues = req.issueIds().stream().map(analysis::requireOwnedIssue).toList();
        ClassIssue first = issues.get(0);
        for (ClassIssue i : issues) {
            if (!ClassIssue.CELL.equals(i.getKind()) || !ClassIssue.OPEN.equals(i.getStatus())) {
                throw BusinessException.badRequest("只能处理待处理的“要点 × 考查类型”卡片");
            }
            if (!i.getItemId().equals(first.getItemId())
                    || !java.util.Objects.equals(i.getKnowledgePointId(), first.getKnowledgePointId())) {
                throw BusinessException.badRequest("只能合并同一次练习、同一个知识点的卡片");
            }
        }
        Set<Long> students = new LinkedHashSet<>();
        int attempts = 0;
        int wrong = 0;
        for (ClassIssue i : issues) {
            students.addAll(ClassAnalysisService.split(i.getStudentIds()));
            attempts += i.getAttempts();
            wrong += i.getWrong();
        }
        ClassPush p = new ClassPush();
        p.setCourseId(first.getCourseId());
        p.setItemId(first.getItemId());
        p.setIssueIds(issues.stream().map(i -> String.valueOf(i.getId())).collect(Collectors.joining(",")));
        p.setKnowledgePointId(first.getKnowledgePointId());
        p.setAssessType(first.getAssessType());
        p.setStudentIds(ClassAnalysisService.join(new ArrayList<>(students)));
        p.setBeforeRate(attempts == 0 ? 0 : Math.round(100.0 * wrong / attempts) / 100.0);
        p.setStatus(ClassPush.DRAFT);
        ChapterItem item = itemMapper.selectById(first.getItemId());
        p.setDueAt(LocalDateTime.now().plusDays(7).withHour(23).withMinute(59).withSecond(0).withNano(0));
        if (req.aiExplanation()) {
            p.setExplanation(draftExplanation(issues, item));
            p.setExplanationBy("ai");
        } else {
            p.setExplanationBy("teacher");
        }
        p.setExerciseIds(ClassAnalysisService.join(newQuestions(issues, item)));
        pushMapper.insert(p);
        return view(p);
    }

    @Transactional
    public PushView update(Long id, PushUpdate req) {
        ClassPush p = requireDraft(id);
        if (req.explanation() != null) {
            p.setExplanation(req.explanation().strip());
            if (!"ai".equals(p.getExplanationBy()) || req.explanation().isBlank()) {
                p.setExplanationBy("teacher");
            }
        }
        if (req.dueAt() != null) {
            if (!req.dueAt().isAfter(LocalDateTime.now())) {
                throw BusinessException.badRequest("期限要晚于现在");
            }
            p.setDueAt(req.dueAt());
        }
        if (req.studentIds() != null && !req.studentIds().isEmpty()) {
            p.setStudentIds(ClassAnalysisService.join(req.studentIds().stream().distinct().toList()));
        }
        pushMapper.updateById(p);
        return view(p);
    }

    /** 重新出题：删掉草稿里的题，再出一批 */
    @Transactional
    public PushView regenerate(Long id) {
        ClassPush p = requireDraft(id);
        questions(p).forEach(e -> exerciseService.delete(e.id()));
        List<ClassIssue> issues = ClassAnalysisService.split(p.getIssueIds()).stream().map(issueMapper::selectById).toList();
        p.setExerciseIds(ClassAnalysisService.join(newQuestions(issues, itemMapper.selectById(p.getItemId()))));
        pushMapper.updateById(p);
        return view(p);
    }

    /** 推送：讲解和题目都确认了，题目变成正式，学生的小助手里出现 */
    @Transactional
    public PushView push(Long id) {
        ClassPush p = requireDraft(id);
        List<ExerciseView> qs = questions(p);
        if (p.getExplanation() == null || p.getExplanation().isBlank()) {
            throw BusinessException.badRequest("先写好补充讲解");
        }
        if (qs.isEmpty()) {
            throw BusinessException.badRequest("至少要有 1 道新题");
        }
        for (ExerciseView e : qs) {
            Exercise row = exerciseMapper.selectById(e.id());
            row.setDraft(false);
            exerciseMapper.updateById(row);
        }
        p.setExerciseIds(ClassAnalysisService.join(qs.stream().map(ExerciseView::id).toList()));
        p.setStatus(ClassPush.PUSHED);
        p.setPushedAt(LocalDateTime.now());
        pushMapper.updateById(p);
        for (Long issueId : ClassAnalysisService.split(p.getIssueIds())) {
            ClassIssue i = issueMapper.selectById(issueId);
            i.setStatus(ClassIssue.PUSHED);
            issueMapper.updateById(i);
        }
        return view(p);
    }

    public PushView get(Long id) {
        return view(requireOwned(id));
    }

    // ------------------------------------------------------------------ 老师：分析作废时

    /** 提醒没交的学生（显示在他们的小助手里） */
    @Transactional
    public int remind(Long itemId, String message) {
        ChapterItem it = requireOwnedItem(itemId);
        List<User> students = analysis.students(it.getCourseId());
        Set<Long> submitted = new LinkedHashSet<>();
        analysis.firstAttempts(it, students).values().forEach(m -> submitted.addAll(m.keySet()));
        String text = message == null || message.isBlank()
                ? "老师提醒：「" + it.getTitle() + "」你还没交，记得完成。" : message.strip();
        int n = 0;
        for (User s : students) {
            if (!submitted.contains(s.getId())) {
                AssistantNotice notice = new AssistantNotice();
                notice.setStudentId(s.getId());
                notice.setItemId(itemId);
                notice.setContent(text.length() > 500 ? text.substring(0, 500) : text);
                noticeMapper.insert(notice);
                n++;
            }
        }
        return n;
    }

    /** 延长截止时间（新的截止到了会重新分析，延期前交的都算） */
    @Transactional
    public void extend(Long itemId, LocalDateTime dueAt) {
        ChapterItem it = requireOwnedItem(itemId);
        if (dueAt == null || !dueAt.isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("新的截止时间要晚于现在");
        }
        it.setDueAt(dueAt);
        itemMapper.updateById(it);
    }

    // ------------------------------------------------------------------ 学生：小助手里

    public List<MyPush> mine(long studentId) {
        List<MyPush> out = new ArrayList<>();
        for (ClassPush p : pushMapper.selectList(new LambdaQueryWrapper<ClassPush>()
                .eq(ClassPush::getStatus, ClassPush.PUSHED).orderByDesc(ClassPush::getPushedAt))) {
            if (!ClassAnalysisService.split(p.getStudentIds()).contains(studentId)) {
                continue;
            }
            List<ExerciseView> qs = questions(p);
            boolean done = !qs.isEmpty() && answered(studentId, qs.stream().map(ExerciseView::id).toList());
            out.add(new MyPush(p.getId(), kpName(p), keyPoints(p), assessLabel(p), p.getExplanation(), p.getDueAt(),
                    done, qs.stream().map(v -> new QuizQuestion(v.id(), v.type(), v.stem(), v.options(), v.reasons()))
                    .toList()));
        }
        return out;
    }

    /** 学生做老师推送的新题：算进补练（不属于任何练习），只算第一次 */
    @Transactional
    public PushResult submit(long studentId, Long pushId, Map<Long, String> answers) {
        ClassPush p = pushMapper.selectById(pushId);
        if (p == null || !ClassPush.PUSHED.equals(p.getStatus())
                || !ClassAnalysisService.split(p.getStudentIds()).contains(studentId)) {
            throw BusinessException.notFound("老师的补充讲解");
        }
        List<ExerciseView> qs = questions(p);
        List<Long> ids = qs.stream().map(ExerciseView::id).toList();
        if (answered(studentId, ids)) {
            throw BusinessException.badRequest("这组题你已经做过了");
        }
        if (answers == null || !answers.keySet().containsAll(ids)) {
            throw BusinessException.badRequest("每道题都要作答");
        }
        List<QuestionResult> results = qs.stream().map(v -> Grader.grade(v, answers.get(v.id()))).toList();
        recordService.saveAnswers(p.getCourseId(), null, results);
        List<MasteryChange> changes = masteryService.update(studentId, ids);
        double score = Math.round(results.stream().mapToDouble(QuestionResult::score).average().orElse(0) * 100) / 100.0;
        return new PushResult(results, score, changes);
    }

    public List<String> notices(long studentId) {
        return noticeMapper.selectList(new LambdaQueryWrapper<AssistantNotice>()
                        .eq(AssistantNotice::getStudentId, studentId).orderByDesc(AssistantNotice::getId).last("LIMIT 10"))
                .stream().map(AssistantNotice::getContent).toList();
    }

    // ------------------------------------------------------------------ 起草讲解、出题

    String draftExplanation(List<ClassIssue> issues, ChapterItem item) {
        ClassIssue first = issues.get(0);
        KnowledgePoint kp = first.getKnowledgePointId() == null ? null : kpMapper.selectById(first.getKnowledgePointId());
        Course course = courseMapper.selectById(item.getCourseId());
        ChapterKp ckp = first.getKnowledgePointId() == null ? null : chapterKpMapper.selectOne(
                new LambdaQueryWrapper<ChapterKp>().eq(ChapterKp::getKnowledgePointId, first.getKnowledgePointId())
                        .last("LIMIT 1"));
        List<KeyPoint> points = ckp == null ? List.of() : read(ckp.getKeyPoints(), new TypeReference<List<KeyPoint>>() { });
        List<String> mis = ckp == null ? List.of() : read(ckp.getMisconceptions(), new TypeReference<List<String>>() { });
        Map<String, String> pointer = safePointers(item.getChapterId());
        Map<String, String> texts = new HashMap<>();
        try {
            recognizer.segments(item.getChapterId()).forEach(s -> texts.putIfAbsent(s.key(), s.text().getContent()));
        } catch (Exception e) {
            // 没有资料文字
        }
        StringBuilder point = new StringBuilder();
        StringBuilder material = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (ClassIssue i : issues) {
            Integer no = i.getKeyPointNo();
            if (no != null && no >= 1 && no <= points.size()) {
                KeyPoint kpnt = points.get(no - 1);
                point.append("要点 ").append(no).append("：").append(kpnt.text()).append('\n');
                for (String src : kpnt.sources()) {
                    if (seen.add(src) && texts.containsKey(src) && material.length() < MATERIAL_CHARS) {
                        material.append('[').append(pointer.getOrDefault(src, src)).append("] ")
                                .append(texts.get(src).replaceAll("\\s+", " ")).append('\n');
                    }
                }
            }
        }
        if (point.isEmpty()) {
            point.append("（这个知识点的全部要点）\n");
            points.forEach(x -> point.append("- ").append(x.text()).append('\n'));
        }
        String assess = first.getAssessType() == null ? "concept" : first.getAssessType();
        String[] m = ClassAnalysisService.METHODS.getOrDefault(assess, ClassAnalysisService.METHODS.get("concept"));
        String prompt = template.render(Map.of(
                "course", course == null ? "" : course.getName(),
                "name", kp == null ? "" : kp.getName(),
                "point", point.toString(),
                "assess", ClassAnalysisService.ASSESS.getOrDefault(assess, assess),
                "method", m[0] + "：" + m[1],
                "wrong", wrongQuestions(issues, item),
                "misconceptions", mis.isEmpty() ? "（无）" : String.join("；", mis),
                "material", material.isEmpty() ? "（没有资料文字）" : material.toString()));
        String text = callLog.call(AiCallLogService.TASK_CLASS_EXPLAIN, template.name(),
                new ChatRequest(List.of(ChatMessage.user(prompt)), 0.5, 1200, false)).result().content();
        return text == null ? "" : text.strip();
    }

    /** 学生做错的题：题干、正确答案、选得最多的错误选项和它的误解 */
    private String wrongQuestions(List<ClassIssue> issues, ChapterItem item) {
        StringBuilder b = new StringBuilder();
        for (Exercise e : cellQuestions(issues, item)) {
            ExerciseView v = exerciseService.toView(e);
            b.append("- ").append(v.stem()).append("（正确答案 ").append(v.answer()).append("）");
            Map<String, Integer> picks = new LinkedHashMap<>();
            answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExerciseId, e.getId()))
                    .stream().filter(a -> !Boolean.TRUE.equals(a.getIsCorrect()) && a.getAnswer() != null)
                    .forEach(a -> picks.merge(a.getAnswer(), 1, Integer::sum));
            picks.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(top -> {
                String note = Grader.grade(v, top.getKey()).note();
                b.append("；很多人选 ").append(top.getKey()).append(note == null ? "" : "（" + note + "）");
            });
            b.append('\n');
        }
        return b.isEmpty() ? "（无）" : b.toString();
    }

    private List<Long> newQuestions(List<ClassIssue> issues, ChapterItem item) {
        ClassIssue first = issues.get(0);
        if (first.getKnowledgePointId() == null) {
            return List.of();
        }
        String nos = issues.stream().map(ClassIssue::getKeyPointNo).filter(java.util.Objects::nonNull)
                .map(String::valueOf).distinct().collect(Collectors.joining("、"));
        StringBuilder req = new StringBuilder("这是老师推给全班做错的学生的新题。");
        if (!nos.isEmpty()) {
            req.append("都考第 ").append(nos).append(" 条要点。");
        }
        req.append("考查类型都用【要考查的类型】里的那一种。不要和下面这些题重复，换新的场景和说法：");
        cellQuestions(issues, item).forEach(e -> req.append("\n- ")
                .append(e.getStem().length() > 60 ? e.getStem().substring(0, 60) + "…" : e.getStem()));
        String assess = first.getAssessType() == null ? "concept" : first.getAssessType();
        return genService.generateForClass(first.getKnowledgePointId(), assess, req.toString(), QUESTIONS)
                .stream().map(ExerciseView::id).toList();
    }

    /** 这几张卡片对应的练习里的题（同一个知识点、要点、考查类型） */
    private List<Exercise> cellQuestions(List<ClassIssue> issues, ChapterItem item) {
        Set<String> keys = issues.stream().map(i -> ClassAnalysisService.cellKey(i.getKnowledgePointId(),
                i.getKeyPointNo(), i.getAssessType())).collect(Collectors.toSet());
        return exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>().eq(Exercise::getItemId, item.getId())
                        .eq(Exercise::getDraft, false))
                .stream().filter(e -> keys.contains(ClassAnalysisService.cellKey(kpOf(e.getId()), e.getKeyPointNo(),
                        e.getAssessType() == null ? "concept" : e.getAssessType()))).toList();
    }

    // ------------------------------------------------------------------ 给页面看、效果

    /** 一次练习已有的推送（干预页“已推送”） */
    List<PushView> pushesOf(Long itemId) {
        return pushMapper.selectList(new LambdaQueryWrapper<ClassPush>().eq(ClassPush::getItemId, itemId)
                .orderByDesc(ClassPush::getId)).stream().map(this::view).toList();
    }

    PushView view(ClassPush p) {
        List<Long> sids = ClassAnalysisService.split(p.getStudentIds());
        List<StudentRef> students = sids.isEmpty() ? List.of() : userMapper.selectByIds(sids).stream()
                .map(u -> new StudentRef(u.getId(), u.getName())).toList();
        String assess = p.getAssessType() == null ? "concept" : p.getAssessType();
        return new PushView(p.getId(), p.getItemId(), p.getKnowledgePointId(), kpName(p), keyPoints(p), assess,
                assessLabel(p), ClassAnalysisService.METHODS.getOrDefault(assess, ClassAnalysisService.METHODS.get("concept"))[0],
                p.getExplanation(), p.getExplanationBy(), questions(p), students, p.getBeforeRate() == null ? 0 : p.getBeforeRate(),
                p.getStatus(), p.getDueAt(), p.getPushedAt(), effect(p, sids));
    }

    /** 效果：推送对象做新题的第一次作答，错误率和推送前比；这个知识点已经不是红色的人数 */
    Effect effect(ClassPush p, List<Long> sids) {
        List<Long> ids = ClassAnalysisService.split(p.getExerciseIds());
        if (!ClassPush.PUSHED.equals(p.getStatus()) || ids.isEmpty() || sids.isEmpty()) {
            return new Effect(sids.size(), 0, 0, 0, null, 0);
        }
        Map<String, AnswerRecord> first = new LinkedHashMap<>();
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>().in(AnswerRecord::getExerciseId, ids)
                        .in(AnswerRecord::getStudentId, sids).orderByAsc(AnswerRecord::getId))
                .forEach(a -> first.putIfAbsent(a.getStudentId() + "|" + a.getExerciseId(), a));
        int attempts = first.size();
        int wrong = (int) first.values().stream().filter(a -> !Boolean.TRUE.equals(a.getIsCorrect())).count();
        int done = (int) sids.stream().filter(s -> ids.stream().allMatch(e -> first.containsKey(s + "|" + e))).count();
        int recovered = 0; // 做完新题的人里，这个知识点已经不是红色的
        if (p.getKnowledgePointId() != null) {
            for (Long s : sids.stream().filter(x -> ids.stream().allMatch(e -> first.containsKey(x + "|" + e))).toList()) {
                Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>().eq(Mastery::getStudentId, s)
                        .eq(Mastery::getKnowledgePointId, p.getKnowledgePointId()));
                if (!MasteryService.RED.equals(MasteryService.color(m))) {
                    recovered++;
                }
            }
        }
        return new Effect(sids.size(), done, attempts, wrong,
                attempts == 0 ? null : Math.round(100.0 * wrong / attempts) / 100.0, recovered);
    }

    // ------------------------------------------------------------------ 小工具

    private List<ExerciseView> questions(ClassPush p) {
        List<Long> ids = ClassAnalysisService.split(p.getExerciseIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        return exerciseMapper.selectByIds(ids).stream().map(exerciseService::toView).toList(); // 删掉的（逻辑删除）不会出现
    }

    private boolean answered(long studentId, List<Long> ids) {
        return !ids.isEmpty() && answerMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getStudentId, studentId).in(AnswerRecord::getExerciseId, ids)) > 0;
    }

    private String kpName(ClassPush p) {
        KnowledgePoint kp = p.getKnowledgePointId() == null ? null : kpMapper.selectById(p.getKnowledgePointId());
        return kp == null ? "" : kp.getName();
    }

    private List<String> keyPoints(ClassPush p) {
        return ClassAnalysisService.split(p.getIssueIds()).stream().map(issueMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .map(i -> analysis.keyPointText(i.getKnowledgePointId(), i.getKeyPointNo()))
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    private String assessLabel(ClassPush p) {
        String a = p.getAssessType() == null ? "concept" : p.getAssessType();
        return ClassAnalysisService.ASSESS.getOrDefault(a, a);
    }

    private Long kpOf(Long exerciseId) {
        return linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getExerciseId, exerciseId))
                .stream().map(ExerciseKnowledgePoint::getKnowledgePointId).findFirst().orElse(null);
    }

    private ClassPush requireOwned(Long id) {
        ClassPush p = id == null ? null : pushMapper.selectById(id);
        if (p == null) {
            throw BusinessException.notFound("推送");
        }
        courseService.requireOwned(p.getCourseId());
        return p;
    }

    private ClassPush requireDraft(Long id) {
        ClassPush p = requireOwned(id);
        if (!ClassPush.DRAFT.equals(p.getStatus())) {
            throw BusinessException.badRequest("已经推送了，不能再改");
        }
        return p;
    }

    private ChapterItem requireOwnedItem(Long itemId) {
        ChapterItem it = itemId == null ? null : itemMapper.selectById(itemId);
        if (it == null || !"exercise".equals(it.getType())) {
            throw BusinessException.notFound("练习");
        }
        courseService.requireOwned(it.getCourseId());
        return it;
    }

    private Map<String, String> safePointers(Long chapterId) {
        try {
            return feynmanDraft.pointers(chapterId);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private <T> List<T> read(String text, TypeReference<List<T>> type) {
        try {
            return text == null || text.isBlank() ? List.of() : json.readValue(text, type);
        } catch (Exception e) {
            return List.of();
        }
    }
}
