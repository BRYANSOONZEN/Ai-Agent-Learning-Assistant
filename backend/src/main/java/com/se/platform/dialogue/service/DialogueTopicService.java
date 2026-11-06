package com.se.platform.dialogue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.service.ChapterItemService;
import com.se.platform.dialogue.dto.TopicSaveRequest;
import com.se.platform.dialogue.dto.TopicView;
import com.se.platform.dialogue.entity.DialogueTopic;
import com.se.platform.dialogue.mapper.DialogueTopicMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 话题的增删改查。keyPoints、hints 在这里做 List ↔ JSON 字符串的转换 */
@Service
public class DialogueTopicService {

    private final DialogueTopicMapper mapper;
    private final ObjectMapper objectMapper;
    private final ChapterItemService itemService;

    public DialogueTopicService(DialogueTopicMapper mapper, ObjectMapper objectMapper,
                                ChapterItemService itemService) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.itemService = itemService;
    }

    /** 某章的全部话题，按对话顺序 */
    public List<TopicView> list(Long chapterId) {
        return list(chapterId, null);
    }

    /** 某章的话题；itemId 不为空时只要这个 AI 对话项里的 */
    public List<TopicView> list(Long chapterId, Long itemId) {
        // LambdaQueryWrapper：用 DialogueTopic::getChapterId 指定列，写错字段名编译时就会报错
        List<DialogueTopic> rows = mapper.selectList(new LambdaQueryWrapper<DialogueTopic>()
                .eq(DialogueTopic::getChapterId, chapterId)
                .eq(itemId != null, DialogueTopic::getItemId, itemId)
                .orderByAsc(DialogueTopic::getSortOrder)
                .orderByAsc(DialogueTopic::getId));
        // stream().map(...).toList()：把每个实体转成 TopicView，得到新列表
        return rows.stream().map(this::toView).toList();
    }

    /** 某章已启用的话题，学生对话按这个顺序进行 */
    public List<TopicView> listEnabled(Long chapterId) {
        return listEnabled(chapterId, null);
    }

    /** 同上；itemId 不为空时只要这个 AI 对话项里的 */
    public List<TopicView> listEnabled(Long chapterId, Long itemId) {
        return list(chapterId, itemId).stream().filter(t -> Boolean.TRUE.equals(t.enabled())).toList();
    }

    public TopicView get(Long id) {
        DialogueTopic topic = mapper.selectById(id);
        if (topic == null) {
            throw BusinessException.notFound("话题");
        }
        return toView(topic);
    }

    /** itemId：放进哪个 AI 对话项（建课页用）；为空 = 老的独立话题页，只挂在章节下 */
    public TopicView create(Long chapterId, Long itemId, TopicSaveRequest req) {
        DialogueTopic topic = new DialogueTopic();
        topic.setChapterId(chapterId);
        if (itemId != null) {
            topic.setItemId(itemService.requireOfType(itemId, chapterId, "dialogue").getId());
        }
        // 新话题排在最后：现有数量就是新话题的序号
        topic.setSortOrder(Math.toIntExact(mapper.selectCount(new LambdaQueryWrapper<DialogueTopic>()
                .eq(DialogueTopic::getChapterId, chapterId))));
        topic.setEnabled(true);
        copy(req, topic);
        mapper.insert(topic);
        return toView(topic);
    }

    public TopicView update(Long id, TopicSaveRequest req) {
        DialogueTopic topic = mapper.selectById(id);
        if (topic == null) {
            throw BusinessException.notFound("话题");
        }
        copy(req, topic);
        mapper.updateById(topic);
        return toView(topic);
    }

    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw BusinessException.notFound("话题");
        }
    }

    private void copy(TopicSaveRequest req, DialogueTopic topic) {
        topic.setTitle(req.title().trim());
        topic.setQuestion(req.question().trim());
        topic.setSimpleQuestion(req.simpleQuestion());
        topic.setKeyPoints(toJson(clean(req.keyPoints())));
        topic.setHints(toJson(clean(req.hints())));
        topic.setRevealText(req.revealText().trim());
        topic.setSourceRef(req.sourceRef());
        topic.setSourceText(req.sourceText());
        topic.setCreatedByAi(Boolean.TRUE.equals(req.createdByAi()));
        topic.setKnowledgePointId(req.knowledgePointId());
        copyTransfer(req, topic);
    }

    /** 变式题三项要么都填，要么都不填：只有题没有要点就没法判 */
    private void copyTransfer(TopicSaveRequest req, DialogueTopic topic) {
        String question = req.transferQuestion() == null ? "" : req.transferQuestion().trim();
        String reveal = req.transferReveal() == null ? "" : req.transferReveal().trim();
        List<String> points = clean(req.transferPoints());
        boolean none = question.isEmpty() && reveal.isEmpty() && points.isEmpty();
        if (!none && (question.isEmpty() || reveal.isEmpty() || points.isEmpty())) {
            throw BusinessException.badRequest("变式题、变式要点、变式参考答案要一起填");
        }
        topic.setTransferQuestion(question);
        topic.setTransferPoints(toJson(points));
        topic.setTransferReveal(reveal);
    }

    /** 去掉空白项；null 当成空列表 */
    private List<String> clean(List<String> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
    }

    private TopicView toView(DialogueTopic t) {
        return new TopicView(t.getId(), t.getItemId(), t.getKnowledgePointId(), t.getSortOrder(), t.getTitle(), t.getQuestion(), t.getSimpleQuestion(),
                fromJson(t.getKeyPoints()), fromJson(t.getHints()), t.getRevealText(), t.getSourceRef(),
                t.getSourceText(), t.getCreatedByAi(), t.getEnabled(), t.getCreatedAt(),
                t.getTransferQuestion(), fromJson(t.getTransferPoints()), t.getTransferReveal());
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
            // TypeReference：告诉 Jackson 要转成 List<String>（泛型信息运行时会丢失，要这样显式写出来）
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("话题数据格式损坏：" + json, e);
        }
    }
}
