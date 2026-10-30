package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.dto.ChapterItemSaveRequest;
import com.se.platform.course.dto.ChapterItemView;
import com.se.platform.course.dto.MaterialView;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Exercise;
import com.se.platform.course.entity.ExerciseKnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ExerciseKnowledgePointMapper;
import com.se.platform.course.mapper.ExerciseMapper;
import com.se.platform.dialogue.entity.DialogueTopic;
import com.se.platform.dialogue.mapper.DialogueTopicMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 章节内容项：视频、文档、练习、AI 对话。一章可以有任意多项，顺序由老师排。
 * 视频 / 文档项各带一个文件；练习项下挂题目（exercise.item_id）；对话项下挂话题（dialogue_topic.item_id）。
 */
@Service
public class ChapterItemService {

    /** 内容项类型的中文名，报错时用。类型名和课程模块名（CourseService.MODULES）是同一套 */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "video", "视频", "document", "文档", "exercise", "练习", "dialogue", "AI 对话");

    private final ChapterItemMapper itemMapper;
    private final ChapterService chapterService;
    private final MaterialService materialService;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseKnowledgePointMapper linkMapper;
    private final DialogueTopicMapper topicMapper;

    public ChapterItemService(ChapterItemMapper itemMapper, ChapterService chapterService,
                              MaterialService materialService, ExerciseMapper exerciseMapper,
                              ExerciseKnowledgePointMapper linkMapper, DialogueTopicMapper topicMapper) {
        this.itemMapper = itemMapper;
        this.chapterService = chapterService;
        this.materialService = materialService;
        this.exerciseMapper = exerciseMapper;
        this.linkMapper = linkMapper;
        this.topicMapper = topicMapper;
    }

    public List<ChapterItemView> list(Long chapterId) {
        chapterService.require(chapterId);
        return itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                        .eq(ChapterItem::getChapterId, chapterId)
                        .orderByAsc(ChapterItem::getSortOrder)
                        .orderByAsc(ChapterItem::getId))
                .stream().map(this::toView).toList();
    }

    public ChapterItemView get(Long id) {
        return toView(require(id));
    }

    /** 新内容项排在本章最后；内容项的类型名和课程模块名一样，课程没开就 400 */
    public ChapterItemView create(ChapterItemSaveRequest req) {
        if (req.type() == null) {
            throw BusinessException.badRequest("请选择内容类型");
        }
        Chapter chapter = chapterService.requireOwnedWithModule(req.chapterId(), req.type());
        ChapterItem item = new ChapterItem();
        item.setCourseId(chapter.getCourseId());
        item.setChapterId(chapter.getId());
        item.setType(req.type());
        item.setSortOrder(Math.toIntExact(itemMapper.selectCount(
                new LambdaQueryWrapper<ChapterItem>().eq(ChapterItem::getChapterId, chapter.getId()))));
        copy(req, item);
        itemMapper.insert(item);
        return toView(item);
    }

    public ChapterItemView update(Long id, ChapterItemSaveRequest req) {
        ChapterItem item = requireOwned(id);
        copy(req, item);
        itemMapper.updateById(item);
        return toView(item);
    }

    /** 给视频 / 文档项上传文件；已有文件就替换掉旧的 */
    @Transactional
    public ChapterItemView uploadFile(Long id, MultipartFile file) {
        ChapterItem item = requireOwned(id);
        if (!item.getType().equals("video") && !item.getType().equals("document")) {
            throw BusinessException.badRequest("只有视频和文档可以上传文件");
        }
        Chapter chapter = chapterService.require(item.getChapterId());
        MaterialView saved = materialService.save(chapter, file, item.getType().equals("video"));
        Long old = item.getMaterialId();
        item.setMaterialId(saved.id());
        itemMapper.updateById(item);
        if (old != null) {
            materialService.delete(old);
        }
        return toView(item);
    }

    public ChapterItemView removeFile(Long id) {
        ChapterItem item = requireOwned(id);
        if (item.getMaterialId() != null) {
            materialService.delete(item.getMaterialId());
            item.setMaterialId(null);
            itemMapper.updateById(item);
        }
        return toView(item);
    }

    /** 删除内容项，连同它的文件、题目（及知识点关联）、话题一起删（都是逻辑删除，关联表除外） */
    @Transactional
    public void delete(Long id) {
        ChapterItem item = requireOwned(id);
        if (item.getMaterialId() != null) {
            materialService.delete(item.getMaterialId());
        }
        List<Long> exerciseIds = exerciseMapper.selectList(new LambdaQueryWrapper<Exercise>()
                .eq(Exercise::getItemId, id)).stream().map(Exercise::getId).toList();
        if (!exerciseIds.isEmpty()) {
            linkMapper.delete(new LambdaQueryWrapper<ExerciseKnowledgePoint>()
                    .in(ExerciseKnowledgePoint::getExerciseId, exerciseIds));
            exerciseMapper.deleteByIds(exerciseIds);
        }
        topicMapper.delete(new LambdaQueryWrapper<DialogueTopic>().eq(DialogueTopic::getItemId, id));
        itemMapper.deleteById(id);
    }

    /** 按 ids 的顺序重写 sort_order */
    @Transactional
    public List<ChapterItemView> reorder(Long chapterId, List<Long> ids) {
        chapterService.requireOwned(chapterId);
        for (int i = 0; i < ids.size(); i++) {
            ChapterItem item = require(ids.get(i));
            if (!item.getChapterId().equals(chapterId)) {
                throw BusinessException.badRequest("内容项不属于本章");
            }
            item.setSortOrder(i);
            itemMapper.updateById(item);
        }
        return list(chapterId);
    }

    public ChapterItem require(Long id) {
        ChapterItem item = id == null ? null : itemMapper.selectById(id);
        if (item == null) {
            throw BusinessException.notFound("内容项");
        }
        return item;
    }

    /** 题目、话题保存前用：itemId 必须是本章的、指定类型的内容项 */
    public ChapterItem requireOfType(Long itemId, Long chapterId, String type) {
        if (itemId == null) {
            throw BusinessException.badRequest("请先选择要放进哪个" + TYPE_LABELS.get(type));
        }
        ChapterItem item = require(itemId);
        if (!item.getChapterId().equals(chapterId) || !item.getType().equals(type)) {
            throw BusinessException.badRequest("所选内容项不是本章的" + TYPE_LABELS.get(type));
        }
        return item;
    }

    private ChapterItem requireOwned(Long id) {
        ChapterItem item = require(id);
        chapterService.requireOwned(item.getChapterId());
        return item;
    }

    private void copy(ChapterItemSaveRequest req, ChapterItem item) {
        item.setTitle(req.title().trim());
        item.setDescription(req.description());
    }

    private ChapterItemView toView(ChapterItem i) {
        MaterialView material = i.getMaterialId() == null ? null : materialService.view(i.getMaterialId());
        long exercises = i.getType().equals("exercise")
                ? exerciseMapper.selectCount(new LambdaQueryWrapper<Exercise>().eq(Exercise::getItemId, i.getId()))
                : 0;
        long topics = i.getType().equals("dialogue")
                ? topicMapper.selectCount(new LambdaQueryWrapper<DialogueTopic>().eq(DialogueTopic::getItemId, i.getId()))
                : 0;
        boolean hasText = i.getDescription() != null && !i.getDescription().isBlank();
        boolean ready = switch (i.getType()) {
            case "video" -> material != null;
            case "document" -> material != null || hasText;
            case "exercise" -> exercises > 0;
            case "dialogue" -> topics > 0;
            default -> false;
        };
        return new ChapterItemView(i.getId(), i.getChapterId(), i.getType(), i.getTitle(), i.getDescription(),
                i.getSortOrder(), material, exercises, topics, ready);
    }
}
