package com.se.platform.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.ai.client.ChatMessage;
import com.se.platform.ai.client.ChatRequest;
import com.se.platform.ai.log.AiCallLogService;
import com.se.platform.ai.prompt.PromptTemplate;
import com.se.platform.material.entity.MaterialJob;
import com.se.platform.material.entity.MaterialText;
import com.se.platform.material.mapper.MaterialJobMapper;
import com.se.platform.material.mapper.MaterialTextMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 视频识别文字的同音错字纠正（“测试用力” → “测试用例”），在识别知识点之前做，每个视频只做一次。
 *
 * <ol>
 *   <li>把本章课件的文字和还没检查过的视频文字交给大模型，只让它列出“错写法 → 课件里的正确写法”；</li>
 *   <li>程序逐条核对，全部满足才替换：正确写法在课件里原样出现；错写法不在课件里、在视频里出现；
 *       两个都是汉字、字数相同（3 个字以上），只差一个字；</li>
 *   <li>替换后原文存在 material_text.original，可以对照；没改的段落不动。</li>
 * </ol>
 * 本章没有课件（PDF、PPT、Word 转出的文字）时不做；出错只记日志，不影响识别知识点。
 */
@Slf4j
@Service
public class VideoTermFixer {

    /** 交给大模型的字数上限（课件、视频各自），控制费用 */
    static final int MAX_CHARS = 12000;
    private static final Pattern HAN = Pattern.compile("[\\u4e00-\\u9fff]{3,}");

    private final MaterialTextMapper textMapper;
    private final MaterialJobMapper jobMapper;
    private final AiCallLogService callLog;
    private final ObjectMapper json;
    private final PromptTemplate template = PromptTemplate.load("asr_term_fix_v1");

    public VideoTermFixer(MaterialTextMapper textMapper, MaterialJobMapper jobMapper, AiCallLogService callLog,
                          ObjectMapper json) {
        this.textMapper = textMapper;
        this.jobMapper = jobMapper;
        this.callLog = callLog;
        this.json = json;
    }

    /** 这一章：返回替换了几处 */
    public int fix(Long chapterId) {
        try {
            return doFix(chapterId);
        } catch (Exception e) {
            log.warn("视频同音字纠正失败（不影响识别知识点）chapter={}", chapterId, e);
            return 0;
        }
    }

    private int doFix(Long chapterId) throws Exception {
        List<MaterialText> all = textMapper.selectList(new LambdaQueryWrapper<MaterialText>()
                .eq(MaterialText::getChapterId, chapterId).orderByAsc(MaterialText::getItemId)
                .orderByAsc(MaterialText::getSortOrder));
        StringBuilder courseware = new StringBuilder();
        all.stream().filter(t -> !"video".equals(t.getKind())).forEach(t -> courseware.append(t.getContent()).append('\n'));
        List<MaterialJob> todo = jobMapper.selectList(new LambdaQueryWrapper<MaterialJob>()
                .eq(MaterialJob::getChapterId, chapterId).eq(MaterialJob::getStatus, MaterialJob.DONE)
                .and(w -> w.isNull(MaterialJob::getTermsChecked).or().eq(MaterialJob::getTermsChecked, false)));
        List<Long> videoItems = todo.stream().map(MaterialJob::getItemId).toList();
        List<MaterialText> videos = all.stream()
                .filter(t -> "video".equals(t.getKind()) && videoItems.contains(t.getItemId())).toList();
        if (videos.isEmpty() || courseware.isEmpty()) {
            return 0;
        }
        StringBuilder video = new StringBuilder();
        videos.forEach(t -> video.append(t.getContent()).append('\n'));
        String cw = courseware.length() > MAX_CHARS ? courseware.substring(0, MAX_CHARS) : courseware.toString();
        String vd = video.length() > MAX_CHARS ? video.substring(0, MAX_CHARS) : video.toString();

        String prompt = template.render(Map.of("courseware", cw, "video", vd));
        AiCallLogService.Logged logged = callLog.call(AiCallLogService.TASK_ASR_TERM_FIX, template.name(),
                new ChatRequest(List.of(ChatMessage.user(prompt)), 0.0, 800, true));
        JsonNode root = json.readTree(logged.result().content());
        Map<String, String> fixes = new LinkedHashMap<>();
        for (JsonNode f : root.path("fixes")) {
            String from = f.path("from").asText("").strip();
            String to = f.path("to").asText("").strip();
            if (accept(from, to, courseware.toString(), video.toString())) {
                fixes.put(from, to);
            }
        }
        callLog.markCheck(logged.logId(), true, fixes.isEmpty() ? null : "采用 " + fixes);

        int changed = 0;
        for (MaterialText t : videos) {
            String text = t.getContent();
            for (Map.Entry<String, String> e : fixes.entrySet()) {
                text = text.replace(e.getKey(), e.getValue());
            }
            if (!text.equals(t.getContent())) {
                if (t.getOriginal() == null) {
                    t.setOriginal(t.getContent());
                }
                t.setContent(text);
                textMapper.updateById(t);
                changed++;
            }
        }
        for (MaterialJob j : todo) {
            j.setTermsChecked(true);
            jobMapper.updateById(j);
        }
        log.info("视频同音字纠正 chapter={}：采用 {}，改了 {} 段", chapterId, fixes, changed);
        return changed;
    }

    /** 程序核对一条替换：只有满足全部条件才用 */
    static boolean accept(String from, String to, String courseware, String video) {
        if (!HAN.matcher(from).matches() || !HAN.matcher(to).matches() || from.length() != to.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < from.length(); i++) {
            if (from.charAt(i) != to.charAt(i)) {
                diff++;
            }
        }
        return diff == 1 && courseware.contains(to) && !courseware.contains(from) && video.contains(from);
    }
}
