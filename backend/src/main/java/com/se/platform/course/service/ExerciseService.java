package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ChapterItemView;
import com.se.platform.course.dto.ExerciseSaveRequest;
import com.se.platform.course.dto.ExerciseView;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 题目的增删改查，以及题目和知识点的关联。
 * 6 种题型（Grader 的常量），每种要填的字段不同，copy() 里按题型检查。
 * AI 生成、上传识别的题先是草稿（draft），老师确认后学生才看得到。
 */
@Service
public class ExerciseService {

    private final ExerciseMapper exerciseMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final KnowledgePointMapper kpMapper;
    private final ChapterService chapterService;
    private final ChapterItemService itemService;
    private final ObjectMapper objectMapper;

    public ExerciseService(ExerciseMapper exerciseMapper, ExerciseKnowledgePointMapper linkMapper,
                           KnowledgePointMapper kpMapper, ChapterService chapterService,
                           ChapterItemService itemService, ObjectMapper objectMapper) {
        this.exerciseMapper = exerciseMapper;
        this.linkMapper = linkMapper;
        this.kpMapper = kpMapper;
        this.chapterService = chapterService;
        this.itemService = itemService;
        this.objectMapper = objectMapper;
    }

    /** 本章题目；itemId 不为空时只要这一次练习的 */
    public List<ExerciseView> list(Long chapterId, Long itemId) {
        chapterService.require(chapterId);
        return exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getChapterId, chapterId)
                        .eq(itemId != null, Exercise::getItemId, itemId)
                        .orderByAsc(Exercise::getSortOrder)
                        .orderByAsc(Exercise::getId))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public ExerciseView create(ExerciseSaveRequest req) {
        Chapter chapter = chapterService.requireOwnedWithModule(req.chapterId(), "exercise");
        Exercise exercise = new Exercise();
        exercise.setCourseId(chapter.getCourseId());
        exercise.setChapterId(chapter.getId());
        exercise.setItemId(itemService.requireOfType(req.itemId(), chapter.getId(), "exercise").getId());
        exercise.setSource("manual");
        exercise.setDraft(false);
        exercise.setCreatedBy(chapterService.currentTeacherId());
        // 新题排在这次练习的最后
        exercise.setSortOrder(Math.toIntExact(exerciseMapper.selectCount(
                new LambdaQueryWrapper<Exercise>().eq(Exercise::getItemId, exercise.getItemId()))));
        copy(req, exercise);
        exerciseMapper.insert(exercise);
        saveLinks(exercise, req.knowledgePointIds());
        return toView(exercise);
    }

    @Transactional
    public ExerciseView update(Long id, ExerciseSaveRequest req) {
        Exercise exercise = require(id);
        chapterService.requireOwned(exercise.getChapterId());
        copy(req, exercise);
        exerciseMapper.updateById(exercise);
        saveLinks(exercise, req.knowledgePointIds());
        return toView(exercise);
    }

    /** 题目是逻辑删除；关联表没有 deleted 列，直接删掉关联 */
    @Transactional
    public void delete(Long id) {
        chapterService.requireOwned(require(id).getChapterId());
        linkMapper.delete(new LambdaQueryWrapper<ExerciseKnowledgePoint>().eq(ExerciseKnowledgePoint::getExerciseId, id));
        exerciseMapper.deleteById(id);
    }

    public Exercise require(Long id) {
        Exercise exercise = id == null ? null : exerciseMapper.selectById(id);
        if (exercise == null) {
            throw BusinessException.notFound("题目");
        }
        return exercise;
    }

    private void copy(ExerciseSaveRequest req, Exercise exercise) {
        String type = req.type() == null || req.type().isBlank() ? Grader.SINGLE : req.type();
        if (!Grader.TYPES.contains(type)) {
            throw BusinessException.badRequest("不认识的题型：" + type);
        }
        String assess = req.assessType() == null || req.assessType().isBlank() ? "concept" : req.assessType();
        if (!Grader.ASSESS.contains(assess)) {
            throw BusinessException.badRequest("不认识的考查类型：" + assess);
        }
        List<String> options = List.of();
        List<String> notes = List.of();
        String answer = "";
        List<String> reasons = List.of();
        List<String> reasonNotes = List.of();
        String reasonAnswer = null;
        List<String> accepts = List.of();
        List<String> keyPoints = List.of();
        String reference = null;
        switch (type) {
            case Grader.FILL -> {
                accepts = clean(req.accepts());
                if (accepts.isEmpty() || accepts.size() > 5) {
                    throw BusinessException.badRequest("填空题要写 1～5 个都算对的说法");
                }
            }
            case Grader.SHORT -> {
                keyPoints = clean(req.keyPoints());
                reference = req.reference() == null ? "" : req.reference().strip();
                if (reference.isEmpty()) {
                    throw BusinessException.badRequest("简答题要写参考答案");
                }
                if (keyPoints.size() < 1 || keyPoints.size() > 5) {
                    throw BusinessException.badRequest("简答题要写 1～5 条判分要点");
                }
            }
            default -> {
                options = Grader.JUDGE.equals(type) ? Grader.JUDGE_OPTIONS : trimAll(req.options());
                if (options.size() < 2 || options.size() > 6) {
                    throw BusinessException.badRequest("选项为 2～6 个");
                }
                if (options.stream().anyMatch(String::isEmpty)) {
                    throw BusinessException.badRequest("选项内容不能为空");
                }
                answer = req.answer() == null ? "" : req.answer().strip().toUpperCase();
                if (Grader.MULTIPLE.equals(type)) {
                    answer = Grader.sortLetters(answer);
                    if (answer.length() < 2 || answer.chars().anyMatch(c -> c - 'A' >= req.options().size())) {
                        throw BusinessException.badRequest("多选题至少要有 2 个正确选项，而且都在选项范围内");
                    }
                } else {
                    letterIndex(answer, options.size(), "正确答案");
                }
                notes = notesFor(req.optionNotes(), options.size(), Grader.MULTIPLE.equals(type) ? -1
                        : answer.charAt(0) - 'A');
                if (Grader.TWO_TIER.equals(type)) {
                    reasons = trimAll(req.reasons());
                    if (reasons.size() < 2 || reasons.size() > 6 || reasons.stream().anyMatch(String::isEmpty)) {
                        throw BusinessException.badRequest("两段式题要写 2～6 个理由选项");
                    }
                    reasonAnswer = req.reasonAnswer() == null ? "" : req.reasonAnswer().strip().toUpperCase();
                    int ri = letterIndex(reasonAnswer, reasons.size(), "正确理由");
                    reasonNotes = notesFor(req.reasonNotes(), reasons.size(), ri);
                }
            }
        }
        exercise.setType(type);
        exercise.setAssessType(assess);
        exercise.setKeyPointNo(req.keyPointNo());
        exercise.setStem(req.stem().trim());
        exercise.setOptions(toJson(options));
        exercise.setAnswer(answer);
        exercise.setAnalysis(req.analysis());
        exercise.setOptionNotes(toJson(notes));
        exercise.setReasons(toJson(reasons));
        exercise.setReasonAnswer(reasonAnswer);
        exercise.setReasonNotes(toJson(reasonNotes));
        exercise.setAccepts(toJson(accepts));
        exercise.setReference(reference);
        exercise.setKeyPoints(toJson(keyPoints));
        exercise.setDifficulty(req.difficulty() == null ? 2 : req.difficulty());
    }

    private static List<String> trimAll(List<String> list) {
        return list == null ? List.of() : list.stream().map(x -> x == null ? "" : x.strip()).toList();
    }

    private static List<String> clean(List<String> list) {
        return trimAll(list).stream().filter(x -> !x.isEmpty()).distinct().toList();
    }

    private static int letterIndex(String letter, int size, String what) {
        int index = letter.length() == 1 ? letter.charAt(0) - 'A' : -1;
        if (index < 0 || index >= size) {
            throw BusinessException.badRequest(what + "要在 A～" + (char) ('A' + size - 1) + " 之间");
        }
        return index;
    }

    /** 误解说明补齐到和选项一样多，正确选项留空 */
    private static List<String> notesFor(List<String> given, int size, int answerIndex) {
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String note = given != null && i < given.size() ? given.get(i) : null;
            notes.add(note == null || i == answerIndex ? "" : note.trim());
        }
        return notes;
    }

    /** 新建一道题（AI 生成、上传识别用）：直接存成草稿，带来源和检查结果 */
    @Transactional
    public ExerciseView createDraft(Long itemId, ExerciseSaveRequest req, String source, String checkNote) {
        ExerciseView v = create(new ExerciseSaveRequest(req.chapterId(), itemId, req.type(), req.assessType(),
                req.keyPointNo(), req.stem(), req.options(), req.answer(), req.analysis(), req.optionNotes(),
                req.reasons(), req.reasonAnswer(), req.reasonNotes(), req.accepts(), req.reference(),
                req.keyPoints(), req.difficulty(), req.knowledgePointIds()));
        Exercise e = require(v.id());
        e.setSource(source);
        e.setDraft(true);
        e.setCheckNote(checkNote == null || checkNote.isBlank() ? null : checkNote);
        exerciseMapper.updateById(e);
        return toView(e);
    }

    /** 小助手生成的补练题：不属于任何一次练习（item_id 为空），不是草稿，不经过老师审核，来源 assistant */
    public static final String SOURCE_ASSISTANT = "assistant";

    /** 老师全班干预推送用的新题：先是草稿，老师确认后推送才变成正式（来源 class，不属于任何练习） */
    public static final String SOURCE_CLASS = "class";
    /** 小助手给已掌握的学生出的拓展题（E1 拓展）：只给他自己，不算进掌握度 */
    public static final String SOURCE_EXTEND = "extend";

    public ExerciseView createRemedy(Long courseId, Long teacherId, ExerciseSaveRequest req) {
        return createOutside(courseId, teacherId, req, SOURCE_ASSISTANT, false);
    }

    public ExerciseView createOutside(Long courseId, Long teacherId, ExerciseSaveRequest req, String source,
                                      boolean draft) {
        Exercise exercise = new Exercise();
        exercise.setCourseId(courseId);
        exercise.setChapterId(req.chapterId());
        exercise.setItemId(null);
        exercise.setSource(source);
        exercise.setDraft(draft);
        exercise.setCreatedBy(teacherId);
        exercise.setSortOrder(1000);
        copy(req, exercise);
        exerciseMapper.insert(exercise);
        saveLinks(exercise, req.knowledgePointIds());
        return toView(exercise);
    }

    /** 检查一道题的写法是否合格，不保存（AI 生成、上传识别时先检查一遍） */
    public void validate(ExerciseSaveRequest req) {
        copy(req, new Exercise());
    }

    /** 老师确认这次练习里的草稿题：学生从此看得到 */
    @Transactional
    public int confirmDrafts(Long itemId) {
        ChapterItemView it = itemService.get(itemId);
        chapterService.requireOwned(it.chapterId());
        List<Exercise> drafts = exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>()
                .eq(Exercise::getItemId, itemId).eq(Exercise::getDraft, true));
        for (Exercise e : drafts) {
            e.setDraft(false);
            exerciseMapper.updateById(e);
        }
        return drafts.size();
    }

    /** 关联知识点：先删后插（docs/database.md 的约定）。知识点必须属于本章 */
    private void saveLinks(Exercise exercise, List<Long> kpIds) {
        for (Long kpId : kpIds) {
            KnowledgePoint kp = kpMapper.selectById(kpId);
            if (kp == null || !kp.getChapterId().equals(exercise.getChapterId())) {
                throw BusinessException.badRequest("关联的知识点不属于本章");
            }
        }
        linkMapper.delete(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                .eq(ExerciseKnowledgePoint::getExerciseId, exercise.getId()));
        // LinkedHashSet：去掉重复的 id，同时保持原来的顺序
        for (Long kpId : new LinkedHashSet<>(kpIds)) {
            linkMapper.insert(ExerciseKnowledgePoint.of(exercise.getId(), kpId));
        }
    }

    /** 题目（带答案）连同关联的知识点；学习推荐挑出题以后也用它判分 */
    public ExerciseView toView(Exercise e) {
        List<ExerciseView.KpRef> kps = linkMapper.selectList(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                        .eq(ExerciseKnowledgePoint::getExerciseId, e.getId()))
                .stream()
                .map(link -> kpMapper.selectById(link.getKnowledgePointId()))
                .filter(kp -> kp != null)
                .map(kp -> new ExerciseView.KpRef(kp.getId(), kp.getName()))
                .toList();
        return new ExerciseView(e.getId(), e.getChapterId(), e.getItemId(),
                e.getType() == null ? Grader.SINGLE : e.getType(),
                e.getAssessType() == null ? "concept" : e.getAssessType(), e.getKeyPointNo(), e.getStem(),
                fromJson(e.getOptions()), e.getAnswer(), e.getAnalysis(), fromJson(e.getOptionNotes()),
                fromJson(e.getReasons()), e.getReasonAnswer(), fromJson(e.getReasonNotes()), fromJson(e.getAccepts()),
                e.getReference(), fromJson(e.getKeyPoints()), e.getSource() == null ? "manual" : e.getSource(),
                Boolean.TRUE.equals(e.getDraft()), e.getCheckNote(), e.getDifficulty(), e.getSortOrder(), kps);
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("题目数据格式损坏：" + json, e);
        }
    }
}
