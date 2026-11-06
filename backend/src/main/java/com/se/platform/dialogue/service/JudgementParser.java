package com.se.platform.dialogue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.AiCallException;
import com.se.platform.dialogue.model.JudgedType;
import com.se.platform.dialogue.model.Judgement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 把模型返回的 {"type": ..., "hit_points": [...], "misconception": ..., "reply": ...} 变成 Judgement；格式不对就抛 AiCallException */
@Component
public class JudgementParser {

    private final ObjectMapper objectMapper;

    public JudgementParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Judgement parse(String content) {
        JsonNode json;
        try {
            json = objectMapper.readTree(stripCodeFence(content));
        } catch (JsonProcessingException e) {
            throw new AiCallException("格式不对：不是 JSON");
        }
        JudgedType type;
        try {
            type = JudgedType.valueOf(json.path("type").asText("").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AiCallException("格式不对：type 不在 7 种之内");
        }
        List<Integer> hits = new ArrayList<>();
        for (JsonNode n : json.path("hit_points")) {
            if (n.canConvertToInt()) {
                hits.add(n.asInt());
            }
        }
        return new Judgement(type, hits, json.path("misconception").asText("").trim(), json.path("reply").asText("").trim());
    }

    /** 有的模型会把 JSON 包在 ```json ... ``` 里 */
    private String stripCodeFence(String content) {
        String s = content == null ? "" : content.trim();
        if (s.startsWith("```")) {
            s = s.substring(s.indexOf('\n') + 1);
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
        }
        return s.trim();
    }
}
