package com.se.platform.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.common.DemoIdentity;
import com.se.platform.course.dto.LearnDtos.QuestionResult;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.dialogue.entity.DialogueMessage;
import com.se.platform.dialogue.entity.DialogueSession;
import com.se.platform.dialogue.mapper.DialogueMessageMapper;
import com.se.platform.dialogue.mapper.DialogueSessionMapper;
import com.se.platform.record.dto.RecordDtos.CourseProgress;
import com.se.platform.record.dto.RecordDtos.DialogueRecord;
import com.se.platform.record.dto.RecordDtos.QuizAttempt;
import com.se.platform.record.dto.RecordDtos.RecordOverview;
import com.se.platform.record.dto.RecordDtos.Summary;
import com.se.platform.record.dto.RecordDtos.TimelineEntry;
import com.se.platform.record.dto.RecordDtos.WrongAnswer;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.mapper.LearningEventMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 「记」：把学生的学习过程存下来，再整理成学习记录页要看的样子。
 * <ul>
 *   <li>答题：交卷时 LearnService 调 {@link #saveAnswers}，每题一条 answer_record</li>
 *   <li>看内容 / 学完一项：前端调 POST /api/learn/events，存成 learning_event</li>
 *   <li>AI 对话：本来就存在 dialogue_session / dialogue_message 里，这里直接读</li>
 * </ul>
 * 登录还没做，学生固定为对话用的同一个演示学生。
 */
@Service
public class RecordService {

    /** 一次停留最多算 30 分钟，开着页面去干别的不能都算学习时间 */
    private static final int MAX_VIEW_SEC = 30 * 60;
    private static final int TIMELINE_SIZE = 40;

    private final AnswerRecordMapper answerMapper;
    private final LearningEventMapper eventMapper;
    private final ChapterItemMapper itemMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final ExerciseMapper exerciseMapper;
    private final DialogueSessionMapper sessionMapper;
    private final DialogueMessageMapper messageMapper;

    public RecordService(AnswerRecordMapper answerMapper, LearningEventMapper eventMapper,
                         ChapterItemMapper itemMapper, ChapterMapper chapterMapper, CourseMapper courseMapper,
                         ExerciseMapper exerciseMapper, DialogueSessionMapper sessionMapper,
                         DialogueMessageMapper messageMapper) {
        this.answerMapper = answerMapper;
        this.eventMapper = eventMapper;
        this.itemMapper = itemMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.exerciseMapper = exerciseMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    public long currentStudentId() {
        return DemoIdentity.studentId();
    }

    // ------------------------------------------------------------------ 写

    /** 一次交卷：每题一条，几题用同一个时间，读的时候按它分组 */
    public void saveAnswers(ChapterItem item, List<QuestionResult> results) {
        saveAnswers(item.getCourseId(), item.getId(), results);
    }

    /** itemId 为空 = 不属于哪次练习的题（学习推荐里做的题库题） */
    public void saveAnswers(Long courseId, Long itemId, List<QuestionResult> results) {
        LocalDateTime now = LocalDateTime.now();
        for (QuestionResult r : results) {
            AnswerRecord a = new AnswerRecord();
            a.setStudentId(currentStudentId());
            a.setExerciseId(r.id());
            a.setCourseId(courseId);
            a.setItemId(itemId);
            a.setAnswer(r.chosen() == null ? "" : r.chosen());
            a.setIsCorrect(r.correct());
            a.setSubmittedAt(now);
            answerMapper.insert(a);
        }
    }

    /** 看了一项内容多久 / 学完一项 */
    public void logEvent(String type, Long itemId, Integer durationSec) {
        if (!LearningEvent.VIEW_ITEM.equals(type) && !LearningEvent.FINISH_ITEM.equals(type)) {
            throw BusinessException.badRequest("不认识的学习行为：" + type);
        }
        ChapterItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BusinessException.notFound("内容");
        }
        LearningEvent e = new LearningEvent();
        e.setStudentId(currentStudentId());
        e.setEventType(type);
        e.setTargetId(itemId);
        e.setCourseId(item.getCourseId());
        if (LearningEvent.VIEW_ITEM.equals(type)) {
            int sec = durationSec == null ? 0 : durationSec;
            if (sec <= 0) {
                return; // 没停留就不记
            }
            e.setDurationSec(Math.min(sec, MAX_VIEW_SEC));
        }
        eventMapper.insert(e);
    }

    /** 一门课里学完了哪些内容项，学习页打开时用来打勾 */
    public List<Long> doneItemIds(Long courseId) {
        return eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getStudentId, currentStudentId())
                        .eq(LearningEvent::getCourseId, courseId)
                        .eq(LearningEvent::getEventType, LearningEvent.FINISH_ITEM))
                .stream().map(LearningEvent::getTargetId).distinct().toList();
    }

    // ------------------------------------------------------------------ 读

    public RecordOverview overview(long student) {
        List<LearningEvent> events = eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getStudentId, student)
                .orderByDesc(LearningEvent::getCreatedAt));
        List<AnswerRecord> answers = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getStudentId, student)
                .orderByDesc(AnswerRecord::getSubmittedAt)
                .orderByAsc(AnswerRecord::getId));
        List<DialogueSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<DialogueSession>()
                .eq(DialogueSession::getStudentId, student)
                .orderByDesc(DialogueSession::getUpdatedAt));

        Names names = new Names(events, answers, sessions);
        List<QuizAttempt> quizzes = quizzes(answers, names);
        List<DialogueRecord> dialogues = dialogues(sessions, names);

        return new RecordOverview(summary(events, answers, quizzes, dialogues), courses(events, names),
                quizzes, dialogues, timeline(events, quizzes, dialogues, names));
    }

    private Summary summary(List<LearningEvent> events, List<AnswerRecord> answers, List<QuizAttempt> quizzes,
                            List<DialogueRecord> dialogues) {
        Set<LocalDate> days = new HashSet<>();
        events.forEach(e -> days.add(e.getCreatedAt().toLocalDate()));
        answers.forEach(a -> days.add(a.getSubmittedAt().toLocalDate()));
        dialogues.forEach(d -> days.add(d.startedAt().toLocalDate()));
        int finished = (int) events.stream().filter(e -> LearningEvent.FINISH_ITEM.equals(e.getEventType()))
                .map(LearningEvent::getTargetId).distinct().count();
        int correct = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
        return new Summary(minutes(events), days.size(), finished, quizzes.size(), answers.size(), correct,
                dialogues.size());
    }

    /** 学过的每门课：学完几项（只数现在还在、已发布章节里的项）、学了多久 */
    private List<CourseProgress> courses(List<LearningEvent> events, Names names) {
        Map<Long, List<LearningEvent>> byCourse = events.stream()
                .collect(Collectors.groupingBy(LearningEvent::getCourseId, LinkedHashMap::new, Collectors.toList()));
        List<CourseProgress> list = new ArrayList<>();
        byCourse.forEach((courseId, courseEvents) -> {
            List<Long> chapterIds = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                    .eq(Chapter::getCourseId, courseId)
                    .eq(Chapter::getStatus, "published")).stream().map(Chapter::getId).toList();
            Set<Long> itemIds = chapterIds.isEmpty() ? Set.of()
                    : itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                            .in(ChapterItem::getChapterId, chapterIds))
                    .stream().map(ChapterItem::getId).collect(Collectors.toSet());
            int done = (int) courseEvents.stream().filter(e -> LearningEvent.FINISH_ITEM.equals(e.getEventType()))
                    .map(LearningEvent::getTargetId).filter(itemIds::contains).distinct().count();
            list.add(new CourseProgress(courseId, names.course(courseId), done, itemIds.size(), minutes(courseEvents)));
        });
        return list;
    }

    /** 同一练习、同一时间交的几题算一次交卷，最新的在前 */
    private List<QuizAttempt> quizzes(List<AnswerRecord> answers, Names names) {
        Map<String, List<AnswerRecord>> groups = answers.stream().collect(Collectors.groupingBy(
                a -> a.getItemId() + "@" + a.getSubmittedAt(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, Exercise> exercises = byId(exerciseMapper,
                answers.stream().map(AnswerRecord::getExerciseId).collect(Collectors.toSet()), Exercise::getId);
        List<QuizAttempt> list = new ArrayList<>();
        for (List<AnswerRecord> g : groups.values()) {
            AnswerRecord first = g.get(0);
            List<WrongAnswer> wrong = g.stream().filter(a -> !Boolean.TRUE.equals(a.getIsCorrect())).map(a -> {
                Exercise ex = exercises.get(a.getExerciseId());
                return new WrongAnswer(ex == null ? "（题目已删除）" : ex.getStem(),
                        a.getAnswer().isEmpty() ? null : a.getAnswer(), ex == null ? null : ex.getAnswer());
            }).toList();
            ChapterItem item = names.item(first.getItemId());
            list.add(new QuizAttempt(first.getItemId(), names.itemTitle(first.getItemId()),
                    names.course(first.getCourseId()), item == null ? null : names.chapter(item.getChapterId()),
                    first.getSubmittedAt(), g.size() - wrong.size(), g.size(), wrong));
        }
        return list;
    }

    private List<DialogueRecord> dialogues(List<DialogueSession> sessions, Names names) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> studentMessages = messageMapper.selectList(new LambdaQueryWrapper<DialogueMessage>()
                        .in(DialogueMessage::getSessionId, sessions.stream().map(DialogueSession::getId).toList())
                        .eq(DialogueMessage::getRole, "STUDENT"))
                .stream().collect(Collectors.groupingBy(DialogueMessage::getSessionId, Collectors.counting()));
        return sessions.stream().map(s -> {
            Chapter ch = names.chapterOf(s.getChapterId());
            return new DialogueRecord(s.getId(),
                    s.getItemId() == null ? "章节巩固对话" : names.itemTitle(s.getItemId()),
                    ch == null ? null : names.course(ch.getCourseId()), ch == null ? null : ch.getTitle(),
                    s.getStatus(), studentMessages.getOrDefault(s.getId(), 0L).intValue(),
                    s.getCreatedAt(), s.getUpdatedAt());
        }).toList();
    }

    /** 最近的动态，几种记录按时间合在一起 */
    private List<TimelineEntry> timeline(List<LearningEvent> events, List<QuizAttempt> quizzes,
                                         List<DialogueRecord> dialogues, Names names) {
        List<TimelineEntry> list = new ArrayList<>();
        for (LearningEvent e : events) {
            ChapterItem item = names.item(e.getTargetId());
            String chapter = item == null ? null : names.chapter(item.getChapterId());
            boolean finish = LearningEvent.FINISH_ITEM.equals(e.getEventType());
            list.add(new TimelineEntry(e.getCreatedAt(), finish ? "finish" : "study",
                    names.itemTitle(e.getTargetId()), names.course(e.getCourseId()), chapter,
                    finish ? "学完了这一项" : "学习了 " + minutesText(e.getDurationSec())));
        }
        for (QuizAttempt q : quizzes) {
            list.add(new TimelineEntry(q.submittedAt(), "quiz", q.title(), q.courseName(), q.chapterTitle(),
                    "交卷，答对 " + q.correct() + " / " + q.total() + " 题"));
        }
        for (DialogueRecord d : dialogues) {
            String state = switch (d.status()) {
                case DialogueSession.FINISHED -> "完成了对话";
                case DialogueSession.PAUSED -> "对话暂停";
                default -> "开始了对话";
            };
            list.add(new TimelineEntry(d.updatedAt(), "dialogue", d.title(), d.courseName(), d.chapterTitle(),
                    state + "，回答了 " + d.studentMessages() + " 次"));
        }
        list.sort(Comparator.comparing(TimelineEntry::time).reversed());
        return list.size() > TIMELINE_SIZE ? list.subList(0, TIMELINE_SIZE) : list;
    }

    // ------------------------------------------------------------------ 小工具

    private static int minutes(List<LearningEvent> events) {
        int sec = events.stream().map(LearningEvent::getDurationSec).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        return (int) Math.round(sec / 60.0);
    }

    private static String minutesText(Integer sec) {
        int s = sec == null ? 0 : sec;
        return s < 60 ? s + " 秒" : Math.round(s / 60.0) + " 分钟";
    }

    private static <T> Map<Long, T> byId(BaseMapper<T> mapper,
                                         Collection<Long> ids, Function<T, Long> id) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectByIds(ids).stream().collect(Collectors.toMap(id, Function.identity()));
    }

    /** 记录里出现的内容项、章节、课程，一次查出来按 id 取名字（删掉的也要能显示） */
    private class Names {
        private final Map<Long, ChapterItem> items;
        private final Map<Long, Chapter> chapters;
        private final Map<Long, Course> courses;

        Names(List<LearningEvent> events, List<AnswerRecord> answers, List<DialogueSession> sessions) {
            Set<Long> itemIds = new HashSet<>();
            events.forEach(e -> itemIds.add(e.getTargetId()));
            answers.forEach(a -> itemIds.add(a.getItemId()));
            sessions.forEach(s -> itemIds.add(s.getItemId()));
            itemIds.remove(null);
            items = byId(itemMapper, itemIds, ChapterItem::getId);

            Set<Long> chapterIds = new HashSet<>();
            items.values().forEach(i -> chapterIds.add(i.getChapterId()));
            sessions.forEach(s -> chapterIds.add(s.getChapterId()));
            chapters = byId(chapterMapper, chapterIds, Chapter::getId);

            Set<Long> courseIds = new HashSet<>();
            events.forEach(e -> courseIds.add(e.getCourseId()));
            answers.forEach(a -> courseIds.add(a.getCourseId()));
            chapters.values().forEach(c -> courseIds.add(c.getCourseId()));
            courseIds.remove(null);
            courses = byId(courseMapper, courseIds, Course::getId);
        }

        ChapterItem item(Long id) {
            return id == null ? null : items.get(id);
        }

        String itemTitle(Long id) {
            if (id == null) {
                return "推荐练习";
            }
            ChapterItem i = item(id);
            return i == null ? "（已删除的内容）" : i.getTitle();
        }

        Chapter chapterOf(Long id) {
            return id == null ? null : chapters.get(id);
        }

        String chapter(Long id) {
            Chapter c = chapterOf(id);
            return c == null ? null : c.getTitle();
        }

        String course(Long id) {
            Course c = id == null ? null : courses.get(id);
            return c == null ? "（已删除的课程）" : c.getName();
        }
    }
}
