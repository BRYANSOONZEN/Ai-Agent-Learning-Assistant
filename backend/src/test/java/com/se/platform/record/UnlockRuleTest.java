package com.se.platform.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.se.platform.record.service.CompletionService;
import com.se.platform.record.service.CompletionService.Node;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 解锁规则（2026-11-26 定）：视频、文档一直解锁；AI 对话要本章视频、文档全部学完；
 * 练习要本章 AI 对话全部完成，没有 AI 对话的章节资料学完就解锁；学完的项永远解锁。不看排列顺序。
 */
class UnlockRuleTest {

    private static final Node V1 = new Node(1L, "video", "视频一");
    private static final Node V2 = new Node(2L, "video", "视频二");
    private static final Node D1 = new Node(3L, "document", "文档");
    private static final Node AI = new Node(4L, "dialogue", "AI 对话");
    private static final Node EX = new Node(5L, "exercise", "练习");
    private static final List<Node> CHAPTER = List.of(V1, V2, D1, AI, EX);

    private static List<Node> need(List<Node> chapter, Node it, Long... done) {
        return CompletionService.blockers(chapter, it, Set.of(done));
    }

    @Test
    void materials_areNeverLocked_anyOrder() {
        // 什么都没学：第二个视频、文档也能直接打开
        assertTrue(need(CHAPTER, V2).isEmpty());
        assertTrue(need(CHAPTER, D1).isEmpty());
    }

    @Test
    void dialogue_needsAllMaterials() {
        assertEquals(List.of(V1, V2, D1), need(CHAPTER, AI));
        // 看完两个视频，文档没读：还差文档
        assertEquals(List.of(D1), need(CHAPTER, AI, 1L, 2L));
        assertTrue(need(CHAPTER, AI, 1L, 2L, 3L).isEmpty());
    }

    @Test
    void exercise_needsDialogue_evenIfMaterialsDone() {
        assertEquals(List.of(AI), need(CHAPTER, EX, 1L, 2L, 3L));
        assertTrue(need(CHAPTER, EX, 1L, 2L, 3L, 4L).isEmpty());
    }

    @Test
    void exercise_withoutDialogue_needsMaterials() {
        List<Node> noDialogue = List.of(V1, D1, EX);
        assertEquals(List.of(V1, D1), need(noDialogue, EX));
        assertTrue(need(noDialogue, EX, 1L, 3L).isEmpty());
    }

    @Test
    void finishedItem_staysUnlocked() {
        // 老数据：练习已经交过卷，资料没学完也不锁回去
        assertTrue(need(CHAPTER, EX, 5L).isEmpty());
    }

    @Test
    void lockedItemIds_perChapter() {
        CompletionService svc = new CompletionService(null, null, null, null);
        Set<Long> locked = svc.lockedItemIds(Set.of(1L, 2L, 3L), List.of(CHAPTER, List.of(new Node(9L, "exercise", "第二章练习"))));
        // 第一章资料学完：对话解锁，练习还锁；第二章只有练习、没有资料和对话：不锁
        assertEquals(Set.of(5L), locked);
    }
}
