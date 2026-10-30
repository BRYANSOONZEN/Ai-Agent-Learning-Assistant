package com.se.platform.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.se.platform.diagnosis.dto.DiagnosisDtos.ChapterDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.ClassPoint;
import com.se.platform.diagnosis.dto.DiagnosisDtos.CourseDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.CourseOverview;
import com.se.platform.diagnosis.dto.DiagnosisDtos.Misconception;
import com.se.platform.diagnosis.dto.DiagnosisDtos.PointDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.StudentDiagnosis;
import com.se.platform.diagnosis.dto.DiagnosisDtos.Summary;
import com.se.platform.diagnosis.dto.DiagnosisDtos.TopicOutcome;
import com.se.platform.diagnosis.entity.Mastery;
import com.se.platform.diagnosis.mapper.MasteryMapper;
import com.se.platform.dialogue.entity.DialogueSession;
import com.se.platform.dialogue.entity.DialogueTopic;
import com.se.platform.dialogue.entity.DialogueTopicState;
import com.se.platform.dialogue.mapper.DialogueSessionMapper;
import com.se.platform.dialogue.mapper.DialogueTopicMapper;
import com.se.platform.dialogue.mapper.DialogueTopicStateMapper;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.mapper.LearningEventMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 学情诊断：把掌握度（mastery 表）按课程 → 章节 → 知识点排好，再补上"为什么"：
 * 最近几次的对错、答错时暴露的误解（老师给错误选项写的说明），以及 AI 对话里每个话题是自己答出、
 * 提示后答出还是看了讲解。老师看的是整门课所有学生的汇总。
 */
@Service
public class DiagnosisService {

    private static final int RECENT = 5;
    private static final int TOP_MISCONCEPTIONS = 3;

    private final MasteryMapper masteryMapper;
    private final AnswerRecordMapper answerMapper;
    private final LearningEventMapper eventMapper;
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final KnowledgePointMapper kpMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final ExerciseMapper exerciseMapper;
    private final DialogueSessionMapper sessionMapper;
    private final DialogueTopicStateMapper stateMapper;
    private final DialogueTopicMapper topicMapper;
    private final CourseService courseService;
    private final ObjectMapper objectMapper;

    public DiagnosisService(MasteryMapper masteryMapper, AnswerRecordMapper answerMapper,
                            LearningEventMapper eventMapper, CourseMapper courseMapper, ChapterMapper chapterMapper,
                            ChapterItemMapper itemMapper, KnowledgePointMapper kpMapper,
                            ExerciseKnowledgePointMapper linkMapper, ExerciseMapper exerciseMapper,
                            DialogueSessionMapper sessionMapper, DialogueTopicStateMapper stateMapper,
                            DialogueTopicMapper topicMapper, CourseService courseService, ObjectMapper objectMapper) {
        this.masteryMapper = masteryMapper;
        this.answerMapper = answerMapper;
        this.eventMapper = eventMapper;
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.kpMapper = kpMapper;
        this.linkMapper = linkMapper;
        this.exerciseMapper = exerciseMapper;
        this.sessionMapper = sessionMapper;
        this.stateMapper = stateMapper;
        this.topicMapper = topicMapper;
        this.courseService = courseService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ 学生

    /** 学生诊断：学过的每门课（有答题或学习行为的），已发布章节里的全部知识点 */
    public StudentDiagnosis student(long studentId) {
        List<AnswerRecord> answers = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getStudentId, studentId)
                .orderByAsc(AnswerRecord::getSubmittedAt)
                .orderByAsc(AnswerRecord::getId));
        Set<Long> courseIds = new TreeSet<>();
        answers.forEach(a -> courseIds.add(a.getCourseId()));
        eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>().eq(LearningEvent::getStudentId, studentId))
                .stream().map(LearningEvent::getCourseId).filter(Objects::nonNull).forEach(courseIds::add);

        Map<Long, Mastery> masteries = masteryMapper.selectList(new LambdaQueryWrapper<Mastery>()
                        .eq(Mastery::getStudentId, studentId))
                .stream().collect(Collectors.toMap(Mastery::getKnowledgePointId, m -> m));

        List<CourseDiagnosis> courses = new ArrayList<>();
        List<PointDiagnosis> all = new ArrayList<>();
        for (Long courseId : courseIds) {
            Course course = courseMapper.selectById(courseId);
            if (course == null) {
                continue;
            }
            List<Chapter> chapters = chapters(courseId, true);
            Map<Long, List<KnowledgePoint>> kpsByChapter = kpsByChapter(chapters);
            Links links = links(kpsByChapter.values().stream().flatMap(List::stream).map(KnowledgePoint::getId).toList());

            List<ChapterDiagnosis> chapterList = new ArrayList<>();
            for (Chapter ch : chapters) {
                List<PointDiagnosis> points = kpsByChapter.getOrDefault(ch.getId(), List.of()).stream()
                        .map(kp -> point(kp, ch, masteries.get(kp.getId()), answers, links))
                        .toList();
                if (!points.isEmpty()) {
                    chapterList.add(new ChapterDiagnosis(ch.getId(), ch.getTitle(), points));
                    all.addAll(points);
                }
            }
            courses.add(new CourseDiagnosis(courseId, course.getName(), chapterList));
        }

        List<PointDiagnosis> weak = all.stream().filter(p -> "weak".equals(p.level()))
                .sorted(Comparator.comparing(PointDiagnosis::mastery)).toList();
        Map<String, Long> byLevel = all.stream().collect(Collectors.groupingBy(PointDiagnosis::level,
                Collectors.counting()));
        Summary summary = new Summary(all.size(), count(byLevel, "good"), count(byLevel, "medium"),
                count(byLevel, "weak"), count(byLevel, "none"));
        return new StudentDiagnosis(studentId, courseMapper.findUserName(studentId), summary, weak, courses,
                dialogueTopics(studentId));
    }

    private PointDiagnosis point(KnowledgePoint kp, Chapter ch, Mastery m, List<AnswerRecord> answers, Links links) {
        Set<Long> exerciseIds = Set.copyOf(links.exercisesOf(kp.getId()));
        List<AnswerRecord> mine = answers.stream().filter(a -> exerciseIds.contains(a.getExerciseId())).toList();
        List<Boolean> recent = mine.subList(Math.max(0, mine.size() - RECENT), mine.size()).stream()
                .map(AnswerRecord::getIsCorrect).toList();
        // 最近一次答错时选的那个错误选项，老师写的误解说明
        String misconception = null;
        for (int i = mine.size() - 1; i >= 0 && misconception == null; i--) {
            AnswerRecord a = mine.get(i);
            if (!Boolean.TRUE.equals(a.getIsCorrect())) {
                misconception = noteOf(links.exercise(a.getExerciseId()), a.getAnswer());
            }
        }
        Long practiceItemId = links.exercisesOf(kp.getId()).stream().map(links::exercise).filter(Objects::nonNull)
                .map(Exercise::getItemId).filter(Objects::nonNull).findFirst().orElse(null);
        Double mastery = m == null ? null : m.getMastery();
        return new PointDiagnosis(kp.getId(), kp.getName(), kp.getCourseId(), ch.getId(), ch.getTitle(),
                Boolean.TRUE.equals(kp.getIsKey()), Boolean.TRUE.equals(kp.getIsDifficult()), mastery,
                MasteryService.level(mastery), m == null ? 0 : m.getAttemptCount(),
                m == null ? 0 : m.getCorrectCount(), recent, misconception, practiceItemId);
    }

    /** AI 对话里已经有结果的话题，最近的在前 */
    private List<TopicOutcome> dialogueTopics(long studentId) {
        List<DialogueSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<DialogueSession>()
                .eq(DialogueSession::getStudentId, studentId)
                .orderByDesc(DialogueSession::getUpdatedAt));
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, DialogueSession> sessionById = sessions.stream()
                .collect(Collectors.toMap(DialogueSession::getId, s -> s));
        List<DialogueTopicState> states = stateMapper.selectList(new LambdaQueryWrapper<DialogueTopicState>()
                .in(DialogueTopicState::getSessionId, sessionById.keySet())
                .isNotNull(DialogueTopicState::getResult));
        Map<Long, DialogueTopic> topics = byId(topicMapper,
                states.stream().map(DialogueTopicState::getTopicId).filter(Objects::nonNull).toList(),
                DialogueTopic::getId);
        Map<Long, ChapterItem> items = byId(itemMapper,
                sessions.stream().map(DialogueSession::getItemId).filter(Objects::nonNull).toList(),
                ChapterItem::getId);
        return states.stream().map(st -> {
            DialogueSession s = sessionById.get(st.getSessionId());
            DialogueTopic t = topics.get(st.getTopicId());
            ChapterItem item = s.getItemId() == null ? null : items.get(s.getItemId());
            return new TopicOutcome(t == null ? "（话题已删除）" : t.getTitle(),
                    item == null ? "章节巩固对话" : item.getTitle(), st.getResult(),
                    st.getWrongCount(), st.getStuckCount(), s.getUpdatedAt());
        }).sorted(Comparator.comparing(TopicOutcome::time).reversed()).toList();
    }

    // ------------------------------------------------------------------ 老师

    /** 课程诊断：这门课所有学生在每个知识点上的平均掌握度、薄弱人数、最常见的误解 */
    public CourseOverview course(Long courseId) {
        Course course = courseService.requireOwned(courseId);
        List<Chapter> chapters = chapters(courseId, false);
        Map<Long, List<KnowledgePoint>> kpsByChapter = kpsByChapter(chapters);
        List<Long> kpIds = kpsByChapter.values().stream().flatMap(List::stream).map(KnowledgePoint::getId).toList();
        Map<Long, List<Mastery>> masteries = kpIds.isEmpty() ? Map.of()
                : masteryMapper.selectList(new LambdaQueryWrapper<Mastery>().in(Mastery::getKnowledgePointId, kpIds))
                .stream().collect(Collectors.groupingBy(Mastery::getKnowledgePointId));
        int students = (int) masteries.values().stream().flatMap(List::stream).map(Mastery::getStudentId)
                .distinct().count();
        Links links = links(kpIds);
        List<AnswerRecord> wrong = links.exercises.isEmpty() ? List.of()
                : answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .in(AnswerRecord::getExerciseId, links.exercises.keySet())
                .eq(AnswerRecord::getIsCorrect, false));

        List<ClassPoint> points = new ArrayList<>();
        for (Chapter ch : chapters) {
            for (KnowledgePoint kp : kpsByChapter.getOrDefault(ch.getId(), List.of())) {
                List<Mastery> rows = masteries.getOrDefault(kp.getId(), List.of());
                Double avg = rows.isEmpty() ? null
                        : Math.round(rows.stream().mapToDouble(Mastery::getMastery).average().orElse(0) * 10000)
                        / 10000.0;
                int weakStudents = (int) rows.stream()
                        .filter(r -> "weak".equals(MasteryService.level(r.getMastery()))).count();
                Set<Long> exerciseIds = Set.copyOf(links.exercisesOf(kp.getId()));
                List<Misconception> top = wrong.stream().filter(a -> exerciseIds.contains(a.getExerciseId()))
                        .map(a -> noteOf(links.exercise(a.getExerciseId()), a.getAnswer()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()))
                        .entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(TOP_MISCONCEPTIONS)
                        .map(e -> new Misconception(e.getKey(), e.getValue().intValue()))
                        .toList();
                points.add(new ClassPoint(kp.getId(), kp.getName(), ch.getTitle(),
                        Boolean.TRUE.equals(kp.getIsKey()), Boolean.TRUE.equals(kp.getIsDifficult()), avg,
                        MasteryService.level(avg), rows.size(), weakStudents, top));
            }
        }
        return new CourseOverview(courseId, course.getName(), students, points);
    }

    // ------------------------------------------------------------------ 小工具

    private List<Chapter> chapters(Long courseId, boolean publishedOnly) {
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCourseId, courseId)
                .eq(publishedOnly, Chapter::getStatus, "published")
                .orderByAsc(Chapter::getSortOrder)
                .orderByAsc(Chapter::getId));
    }

    private Map<Long, List<KnowledgePoint>> kpsByChapter(List<Chapter> chapters) {
        if (chapters.isEmpty()) {
            return Map.of();
        }
        return kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                        .in(KnowledgePoint::getChapterId, chapters.stream().map(Chapter::getId).toList())
                        .orderByAsc(KnowledgePoint::getSortOrder)
                        .orderByAsc(KnowledgePoint::getId))
                .stream().collect(Collectors.groupingBy(KnowledgePoint::getChapterId));
    }

    /** 选了这个答案（"B"）的误解说明：老师给每个错误选项写的 option_notes */
    private String noteOf(Exercise e, String answer) {
        if (e == null || answer == null || answer.length() != 1 || e.getOptionNotes() == null) {
            return null;
        }
        try {
            List<String> notes = objectMapper.readValue(e.getOptionNotes(), new TypeReference<List<String>>() { });
            int idx = answer.charAt(0) - 'A';
            String note = idx >= 0 && idx < notes.size() ? notes.get(idx) : null;
            return note == null || note.isBlank() ? null : note;
        } catch (Exception ex) {
            return null;
        }
    }

    private Links links(List<Long> kpIds) {
        if (kpIds.isEmpty()) {
            return new Links(List.of(), Map.of());
        }
        List<ExerciseKnowledgePoint> links = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                .in(ExerciseKnowledgePoint::getKnowledgePointId, kpIds));
        return new Links(links, byId(exerciseMapper,
                links.stream().map(ExerciseKnowledgePoint::getExerciseId).distinct().toList(), Exercise::getId));
    }

    /** 知识点和题目的关联，加上这些题目本身（删掉的题目查不到，自动跳过） */
    private record Links(List<ExerciseKnowledgePoint> links, Map<Long, Exercise> exercises) {

        List<Long> exercisesOf(Long kpId) {
            return links.stream().filter(l -> l.getKnowledgePointId().equals(kpId))
                    .map(ExerciseKnowledgePoint::getExerciseId).filter(exercises::containsKey).toList();
        }

        Exercise exercise(Long id) {
            return exercises.get(id);
        }
    }

    private static int count(Map<String, Long> byLevel, String level) {
        return byLevel.getOrDefault(level, 0L).intValue();
    }

    private static <T> Map<Long, T> byId(BaseMapper<T> mapper,
                                         Collection<Long> ids, Function<T, Long> id) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectByIds(ids).stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
