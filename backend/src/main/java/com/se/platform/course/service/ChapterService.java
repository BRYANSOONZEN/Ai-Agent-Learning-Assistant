package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ChapterSaveRequest;
import com.se.platform.course.dto.ChapterView;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.dialogue.entity.DialogueTopic;
import com.se.platform.dialogue.mapper.DialogueTopicMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 章节的增删改查和排序 */
@Service
public class ChapterService {

    private final ChapterMapper chapterMapper;
    private final CourseService courseService;
    private final ChapterItemMapper itemMapper;
    private final KnowledgePointMapper kpMapper;
    private final ExerciseMapper exerciseMapper;
    private final DialogueTopicMapper topicMapper;

    public ChapterService(ChapterMapper chapterMapper, CourseService courseService, ChapterItemMapper itemMapper,
                          KnowledgePointMapper kpMapper, ExerciseMapper exerciseMapper,
                          DialogueTopicMapper topicMapper) {
        this.chapterMapper = chapterMapper;
        this.courseService = courseService;
        this.itemMapper = itemMapper;
        this.kpMapper = kpMapper;
        this.exerciseMapper = exerciseMapper;
        this.topicMapper = topicMapper;
    }

    public List<ChapterView> list(Long courseId) {
        courseService.require(courseId);
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getCourseId, courseId)
                        .orderByAsc(Chapter::getSortOrder)
                        .orderByAsc(Chapter::getId))
                .stream().map(this::toView).toList();
    }

    public ChapterView get(Long id) {
        return toView(require(id));
    }

    public ChapterView create(ChapterSaveRequest req) {
        courseService.requireOwned(req.courseId());
        Chapter chapter = new Chapter();
        chapter.setCourseId(req.courseId());
        // 新章节排在最后
        chapter.setSortOrder(Math.toIntExact(chapterMapper.selectCount(
                new LambdaQueryWrapper<Chapter>().eq(Chapter::getCourseId, req.courseId()))));
        copy(req, chapter);
        chapterMapper.insert(chapter);
        return toView(chapter);
    }

    public ChapterView update(Long id, ChapterSaveRequest req) {
        Chapter chapter = requireOwned(id);
        copy(req, chapter);
        chapterMapper.updateById(chapter);
        return toView(chapter);
    }

    /** 章节下还有内容时不能删，返回 409 并说明还剩什么 */
    public void delete(Long id) {
        ChapterView view = toView(requireOwned(id));
        List<String> left = new ArrayList<>();
        if (view.itemCount() > 0) left.add(view.itemCount() + " 项内容");
        if (view.knowledgePointCount() > 0) left.add(view.knowledgePointCount() + " 个知识点");
        if (!left.isEmpty()) {
            throw BusinessException.conflict("本章还有 " + String.join("、", left) + "，请先删除");
        }
        chapterMapper.deleteById(id);
    }

    /** 按 ids 的顺序重写 sort_order。@Transactional：中途出错就全部回滚，不会只改一半 */
    @Transactional
    public List<ChapterView> reorder(Long courseId, List<Long> ids) {
        courseService.requireOwned(courseId);
        for (int i = 0; i < ids.size(); i++) {
            Chapter chapter = require(ids.get(i));
            if (!chapter.getCourseId().equals(courseId)) {
                throw BusinessException.badRequest("章节不属于这门课");
            }
            chapter.setSortOrder(i);
            chapterMapper.updateById(chapter);
        }
        return list(courseId);
    }

    /** 取章节，不存在就抛 404。知识点、题目、资料的 Service 都用它检查 chapterId */
    public Chapter require(Long id) {
        Chapter chapter = id == null ? null : chapterMapper.selectById(id);
        if (chapter == null) {
            throw BusinessException.notFound("章节");
        }
        return chapter;
    }

    /** 取章节并检查它所属的课程是不是当前老师的。章节里的资料、知识点、题目在增删改前都调用它 */
    public Chapter requireOwned(Long id) {
        Chapter chapter = require(id);
        courseService.requireOwned(chapter.getCourseId());
        return chapter;
    }

    /** requireOwned，再检查课程开了这个模块（见 CourseService.MODULES） */
    public Chapter requireOwnedWithModule(Long id, String module) {
        Chapter chapter = require(id);
        courseService.requireModule(courseService.requireOwned(chapter.getCourseId()), module);
        return chapter;
    }

    /** 当前登录的老师，见 CourseService.currentTeacherId */
    public long currentTeacherId() {
        return courseService.currentTeacherId();
    }

    private void copy(ChapterSaveRequest req, Chapter chapter) {
        chapter.setTitle(req.title().trim());
        chapter.setIntro(req.intro());
        chapter.setStatus(req.status() == null ? "draft" : req.status());
    }

    private ChapterView toView(Chapter c) {
        Long id = c.getId();
        return new ChapterView(id, c.getCourseId(), c.getTitle(), c.getIntro(), c.getStatus(), c.getSortOrder(),
                itemMapper.selectCount(new LambdaQueryWrapper<ChapterItem>().eq(ChapterItem::getChapterId, id)),
                kpMapper.selectCount(new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getChapterId, id)),
                exerciseMapper.selectCount(new LambdaQueryWrapper<Exercise>().eq(Exercise::getChapterId, id)),
                topicMapper.selectCount(new LambdaQueryWrapper<DialogueTopic>().eq(DialogueTopic::getChapterId, id)));
    }
}
