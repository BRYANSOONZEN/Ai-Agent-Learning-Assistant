package com.se.platform.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.dialogue.entity.DialogueSession;
import com.se.platform.dialogue.mapper.DialogueSessionMapper;
import com.se.platform.record.entity.AnswerRecord;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.AnswerRecordMapper;
import com.se.platform.record.mapper.LearningEventMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 一项内容"学完了没有"，以及按顺序解锁。
 *
 * <p>学完，三种来源任取其一：
 * <ul>
 *   <li>learning_event 里的 finish_item：视频完成度 ≥ 90%、文档读满 30 秒并点了"我已读完"时由后端写入（ProgressService）；
 *       练习交卷、对话结束时前端也会记一条</li>
 *   <li>练习交过卷（answer_record）</li>
 *   <li>AI 对话结束过（dialogue_session 状态 FINISHED）</li>
 * </ul>
 *
 * <p>解锁（2026-11-26 定，按内容项类型，不看排列顺序）：
 * <ul>
 *   <li>视频、文档：一直解锁，学生可以按自己的顺序看、来回看</li>
 *   <li>AI 对话：本章视频、文档全部学完才解锁</li>
 *   <li>练习：本章 AI 对话全部完成才解锁；这一章没有 AI 对话的，视频、文档全部学完就解锁</li>
 * </ul>
 * 自己已经学完的项永远是解锁的（老数据不会被锁回去）。
 */
@Service
public class CompletionService {

    private final ChapterItemMapper itemMapper;
    private final LearningEventMapper eventMapper;
    private final AnswerRecordMapper answerMapper;
    private final DialogueSessionMapper sessionMapper;

    public CompletionService(ChapterItemMapper itemMapper, LearningEventMapper eventMapper,
                             AnswerRecordMapper answerMapper, DialogueSessionMapper sessionMapper) {
        this.itemMapper = itemMapper;
        this.eventMapper = eventMapper;
        this.answerMapper = answerMapper;
        this.sessionMapper = sessionMapper;
    }

    /** 每一项、每个学生最早一次学完的时间：itemId → (studentId → 时间) */
    public Map<Long, Map<Long, LocalDateTime>> finishedAt(Collection<Long> itemIds, Collection<Long> studentIds) {
        Map<Long, Map<Long, LocalDateTime>> result = new HashMap<>();
        if (itemIds.isEmpty() || studentIds.isEmpty()) {
            return result;
        }
        eventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getEventType, LearningEvent.FINISH_ITEM)
                        .in(LearningEvent::getTargetId, itemIds)
                        .in(LearningEvent::getStudentId, studentIds))
                .forEach(e -> keepEarliest(result, e.getTargetId(), e.getStudentId(), e.getCreatedAt()));
        answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                        .in(AnswerRecord::getItemId, itemIds)
                        .in(AnswerRecord::getStudentId, studentIds))
                .forEach(a -> keepEarliest(result, a.getItemId(), a.getStudentId(), a.getSubmittedAt()));
        sessionMapper.selectList(new LambdaQueryWrapper<DialogueSession>()
                        .eq(DialogueSession::getStatus, DialogueSession.FINISHED)
                        .in(DialogueSession::getItemId, itemIds)
                        .in(DialogueSession::getStudentId, studentIds))
                .forEach(s -> keepEarliest(result, s.getItemId(), s.getStudentId(), s.getUpdatedAt()));
        return result;
    }

    /** 这个学生学完了其中哪些项 */
    public Set<Long> doneItemIds(long studentId, Collection<Long> itemIds) {
        Set<Long> done = new HashSet<>();
        finishedAt(itemIds, List.of(studentId)).forEach((itemId, byStudent) -> {
            if (byStudent.containsKey(studentId)) {
                done.add(itemId);
            }
        });
        return done;
    }

    /** 判断解锁只需要内容项的 id、类型和标题 */
    public record Node(Long id, String type, String title) {
        static Node of(ChapterItem i) {
            return new Node(i.getId(), i.getType(), i.getTitle());
        }
    }

    private static boolean material(String type) {
        return "video".equals(type) || "document".equals(type);
    }

    /**
     * 这一项要先完成哪些项（按学习顺序）；空 = 已解锁。chapter：这一章的全部内容项，已按学习顺序排好。
     * 自己已经学完的项不锁。
     */
    public static List<Node> blockers(List<Node> chapter, Node item, Set<Long> done) {
        if (done.contains(item.id()) || material(item.type())) {
            return List.of();
        }
        List<Node> materials = chapter.stream().filter(n -> material(n.type())).toList();
        List<Node> dialogues = chapter.stream().filter(n -> "dialogue".equals(n.type())).toList();
        List<Node> need = switch (item.type()) {
            case "dialogue" -> materials;
            case "exercise" -> dialogues.isEmpty() ? materials : dialogues;
            default -> List.of();
        };
        return need.stream().filter(n -> !done.contains(n.id())).toList();
    }

    /** 锁住的项。chapters：每章的内容项，已按学习顺序排好 */
    public Set<Long> lockedItemIds(Set<Long> done, List<List<Node>> chapters) {
        Set<Long> locked = new HashSet<>();
        for (List<Node> items : chapters) {
            for (Node it : items) {
                if (!blockers(items, it, done).isEmpty()) {
                    locked.add(it.id());
                }
            }
        }
        return locked;
    }

    /** 学生要做这一项之前检查：锁住了就拒绝，告诉他先完成哪些项 */
    public void requireUnlocked(long studentId, ChapterItem item) {
        List<Node> items = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                        .eq(ChapterItem::getChapterId, item.getChapterId())
                        .orderByAsc(ChapterItem::getSortOrder)
                        .orderByAsc(ChapterItem::getId))
                .stream().map(Node::of).toList();
        Set<Long> done = doneItemIds(studentId, items.stream().map(Node::id).toList());
        List<Node> need = blockers(items, Node.of(item), done);
        if (!need.isEmpty()) {
            String names = need.stream().map(n -> "《" + n.title() + "》").collect(Collectors.joining("、"));
            throw BusinessException.badRequest("这一项还没解锁，请先完成：" + names);
        }
    }

    private static void keepEarliest(Map<Long, Map<Long, LocalDateTime>> result, Long itemId, Long studentId,
                                     LocalDateTime time) {
        if (itemId == null || time == null) {
            return;
        }
        result.computeIfAbsent(itemId, k -> new HashMap<>()).merge(studentId, time, (a, b) -> a.isBefore(b) ? a : b);
    }
}
