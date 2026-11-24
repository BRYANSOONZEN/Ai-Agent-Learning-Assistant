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
 * <p>解锁：一章里按学习顺序，第 k+1 项解锁 ⇔ 第 k 项学完；每章第 1 项默认解锁。
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

    /**
     * 锁住的项。chapters：每章的内容项 id，已按学习顺序排好。
     * 第 k 项锁住 ⇔ 不是第 1 项，且第 k-1 项没学完，且第 k 项自己也没学完。
     */
    public Set<Long> lockedItemIds(Set<Long> done, List<List<Long>> chapters) {
        Set<Long> locked = new HashSet<>();
        for (List<Long> items : chapters) {
            for (int k = 1; k < items.size(); k++) {
                if (!done.contains(items.get(k - 1)) && !done.contains(items.get(k))) {
                    locked.add(items.get(k));
                }
            }
        }
        return locked;
    }

    /** 学生要做这一项之前检查：锁住了就拒绝，告诉他先完成哪一项 */
    public void requireUnlocked(long studentId, ChapterItem item) {
        List<ChapterItem> items = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                .eq(ChapterItem::getChapterId, item.getChapterId())
                .orderByAsc(ChapterItem::getSortOrder)
                .orderByAsc(ChapterItem::getId));
        int k = 0;
        while (k < items.size() && !items.get(k).getId().equals(item.getId())) {
            k++;
        }
        if (k == 0 || k >= items.size()) {
            return; // 第 1 项，或者不在这一章里（不该发生）：不锁
        }
        ChapterItem prev = items.get(k - 1);
        Set<Long> done = doneItemIds(studentId, List.of(prev.getId(), item.getId()));
        if (!done.contains(prev.getId()) && !done.contains(item.getId())) {
            throw BusinessException.badRequest("这一项还没解锁，请先完成上一项：《" + prev.getTitle() + "》");
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
