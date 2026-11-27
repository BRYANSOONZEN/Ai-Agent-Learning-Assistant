package com.se.platform.feynman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.feynman.entity.FeynmanSession;
import com.se.platform.feynman.mapper.FeynmanSessionMapper;
import com.se.platform.feynman.service.FeynmanEngine.Kp;
import com.se.platform.feynman.service.FeynmanEngine.State;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.mapper.ChapterKpMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 对话的学习情况，给小助手和每章画像用（只是文字说明，不打分、不改颜色）。
 *
 * <p>对话结束时 FeynmanEngine 已经记下每条要点是怎么讲清的（自己讲清 / 第几级提示后 / 看资料后 / 补充后才知道），
 * 这里把一个学生在一章里的结果按知识点取出来，拼成一句话，例如
 * “AI 对话：要点 1 自己讲清楚了；要点 2 第 2 级提示后讲清楚了”。
 * 每个 AI 对话项只看这个学生最近一次结束的对话。
 */
@Slf4j
@Service
public class FeynmanDiagnosis {

    private final FeynmanSessionMapper sessionMapper;
    private final ChapterItemMapper itemMapper;
    private final ChapterKpMapper kpMapper;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public FeynmanDiagnosis(FeynmanSessionMapper sessionMapper, ChapterItemMapper itemMapper, ChapterKpMapper kpMapper) {
        this.sessionMapper = sessionMapper;
        this.itemMapper = itemMapper;
        this.kpMapper = kpMapper;
    }

    /** 这一章：知识点（knowledge_point.id）→ 一句话的 AI 对话情况；没对话过的知识点不在里面 */
    public Map<Long, String> byKnowledgePoint(long studentId, Long chapterId) {
        Map<Long, String> out = new LinkedHashMap<>();
        List<ChapterItem> dialogues = itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                .eq(ChapterItem::getChapterId, chapterId).eq(ChapterItem::getType, "dialogue"));
        Map<Long, Long> kpToPoint = new HashMap<>();
        for (ChapterItem item : dialogues) {
            FeynmanSession s = sessionMapper.selectOne(new LambdaQueryWrapper<FeynmanSession>()
                    .eq(FeynmanSession::getStudentId, studentId).eq(FeynmanSession::getItemId, item.getId())
                    .eq(FeynmanSession::getStatus, FeynmanSession.DONE)
                    .orderByDesc(FeynmanSession::getId).last("LIMIT 1"));
            if (s == null || s.getState() == null) {
                continue;
            }
            State st;
            try {
                st = json.readValue(s.getState(), State.class);
            } catch (Exception e) {
                log.warn("对话进度读不出来：session {}", s.getId(), e);
                continue;
            }
            for (Kp k : st.kps) {
                Long pointId = kpToPoint.computeIfAbsent(k.kpId, id -> {
                    ChapterKp ck = kpMapper.selectById(id);
                    return ck == null || ck.getKnowledgePointId() == null ? -1L : ck.getKnowledgePointId();
                });
                if (pointId < 0 || k.points.isEmpty()) {
                    continue;
                }
                out.put(pointId, line(k));
            }
        }
        return out;
    }

    static String line(Kp k) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < k.points.size(); i++) {
            parts.add("要点 " + (i + 1) + " " + FeynmanEngine.label(k.points.get(i), k.peer));
        }
        return "AI 对话：" + String.join("；", parts);
    }
}
