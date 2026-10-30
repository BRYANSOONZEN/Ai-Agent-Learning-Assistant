package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.common.BusinessException;
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

/** 单选题的增删改查，以及题目和知识点的关联 */
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
        exercise.setType("single_choice");
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
        List<String> options = req.options().stream().map(s -> s == null ? "" : s.trim()).toList();
        if (options.stream().anyMatch(String::isEmpty)) {
            throw BusinessException.badRequest("选项内容不能为空");
        }
        // 正确答案必须是 A 开始、选项数量以内的字母
        String answer = req.answer().trim().toUpperCase();
        int index = answer.length() == 1 ? answer.charAt(0) - 'A' : -1;
        if (index < 0 || index >= options.size()) {
            throw BusinessException.badRequest("正确答案要在 A～" + (char) ('A' + options.size() - 1) + " 之间");
        }
        // 误解说明补齐到和选项一样多，多出来的截掉
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String note = req.optionNotes() != null && i < req.optionNotes().size() ? req.optionNotes().get(i) : null;
            notes.add(note == null || i == index ? "" : note.trim());
        }
        exercise.setStem(req.stem().trim());
        exercise.setOptions(toJson(options));
        exercise.setAnswer(answer);
        exercise.setAnalysis(req.analysis());
        exercise.setOptionNotes(toJson(notes));
        exercise.setDifficulty(req.difficulty() == null ? 2 : req.difficulty());
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
        return new ExerciseView(e.getId(), e.getChapterId(), e.getItemId(), e.getType(), e.getStem(), fromJson(e.getOptions()),
                e.getAnswer(), e.getAnalysis(), fromJson(e.getOptionNotes()), e.getDifficulty(), e.getSortOrder(),
                kps);
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
