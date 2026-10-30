package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.KnowledgePointSaveRequest;
import com.se.platform.course.dto.KnowledgePointView;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 知识点的增删改查 */
@Service
public class KnowledgePointService {

    private final KnowledgePointMapper kpMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final ChapterService chapterService;

    public KnowledgePointService(KnowledgePointMapper kpMapper, ExerciseKnowledgePointMapper linkMapper,
                                 ChapterService chapterService) {
        this.kpMapper = kpMapper;
        this.linkMapper = linkMapper;
        this.chapterService = chapterService;
    }

    public List<KnowledgePointView> list(Long chapterId) {
        chapterService.require(chapterId);
        return kpMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getChapterId, chapterId)
                        .orderByAsc(KnowledgePoint::getSortOrder)
                        .orderByAsc(KnowledgePoint::getId))
                .stream().map(this::toView).toList();
    }

    public KnowledgePointView create(KnowledgePointSaveRequest req) {
        Chapter chapter = chapterService.requireOwnedWithModule(req.chapterId(), "knowledge");
        KnowledgePoint kp = new KnowledgePoint();
        kp.setCourseId(chapter.getCourseId());
        kp.setChapterId(chapter.getId());
        kp.setSortOrder(Math.toIntExact(kpMapper.selectCount(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getChapterId, chapter.getId()))));
        copy(req, kp);
        kpMapper.insert(kp);
        return toView(kp);
    }

    public KnowledgePointView update(Long id, KnowledgePointSaveRequest req) {
        KnowledgePoint kp = require(id);
        chapterService.requireOwned(kp.getChapterId());
        copy(req, kp);
        kpMapper.updateById(kp);
        return toView(kp);
    }

    /** 还有题目关联它时不能删，否则那些题就没有知识点了 */
    public void delete(Long id) {
        chapterService.requireOwned(require(id).getChapterId());
        long linked = countExercises(id);
        if (linked > 0) {
            throw BusinessException.conflict("还有 " + linked + " 道题目关联这个知识点，请先修改这些题目");
        }
        kpMapper.deleteById(id);
    }

    public KnowledgePoint require(Long id) {
        KnowledgePoint kp = id == null ? null : kpMapper.selectById(id);
        if (kp == null) {
            throw BusinessException.notFound("知识点");
        }
        return kp;
    }

    private void copy(KnowledgePointSaveRequest req, KnowledgePoint kp) {
        kp.setName(req.name().trim());
        kp.setContent(req.content());
        kp.setDifficulty(req.difficulty() == null ? 2 : req.difficulty());
        kp.setIsKey(Boolean.TRUE.equals(req.isKey()));
        kp.setIsDifficult(Boolean.TRUE.equals(req.isDifficult()));
    }

    private long countExercises(Long kpId) {
        return linkMapper.selectCount(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                .eq(ExerciseKnowledgePoint::getKnowledgePointId, kpId));
    }

    private KnowledgePointView toView(KnowledgePoint k) {
        return new KnowledgePointView(k.getId(), k.getChapterId(), k.getName(), k.getContent(), k.getDifficulty(),
                k.getIsKey(), k.getIsDifficult(), k.getSortOrder(), countExercises(k.getId()));
    }
}
