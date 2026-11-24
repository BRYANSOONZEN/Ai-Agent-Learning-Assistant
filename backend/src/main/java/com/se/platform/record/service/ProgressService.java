package com.se.platform.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.record.dto.ProgressDtos.ProgressView;
import com.se.platform.record.dto.ProgressDtos.ReadingReport;
import com.se.platform.record.dto.ProgressDtos.VideoReport;
import com.se.platform.record.entity.ItemProgress;
import com.se.platform.record.entity.LearningEvent;
import com.se.platform.record.mapper.ItemProgressMapper;
import com.se.platform.record.mapper.LearningEventMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频和文档怎么算"学完"（学完才解锁下一项，见 CompletionService）。
 *
 * <p><b>视频</b>：完成度
 * <pre>
 *   P = Σ (e_i − s_i) ÷ D        各段 [s_i, e_i] 先合并，互不重叠；D = 视频总时长
 *   学完 ⇔ P ≥ 90%
 * </pre>
 * 只算真正播放过的时间段：拖进度条跳过去的不算，同一段看几遍也只算一次。
 * 防改请求：两次上报之间新增的观看时长，不能超过实际经过的时间 × 2（最多 2 倍速）+ 5 秒，超过的这次上报不算。
 *
 * <p><b>文档</b>：
 * <pre>
 *   学完 ⇔ 累计阅读 t ≥ 30 秒  且  学生点了"我已读完"
 * </pre>
 * t 只算页面在前台的时间（前端计时、每 10 秒上报）；每次上报最多加"距离上次上报经过的秒数 + 3"。
 *
 * <p>判定学完时写一条 finish_item 学习事件，截止时间、学习助手、老师看板都接着用这条记录。
 */
@Service
public class ProgressService {

    /** 视频看到多少算学完 */
    public static final double VIDEO_PASS = 0.9;
    /** 文档至少读多少秒才能点"我已读完" */
    public static final int READ_MIN_SEC = 30;
    /** 最高倍速，用来检查上报的观看时长是否可信 */
    static final double MAX_RATE = 2.0;
    /** 网络延迟等的余量（秒） */
    static final double SLACK_SEC = 5;
    /** 第一次上报时，没有"上一次"可比，最多认这么多秒（前端第一次上报在播放 10 秒以内） */
    static final double FIRST_REPORT_SEC = 15;
    /** 两段相隔不到这么多秒就并成一段（播放时前端记录的时间点有零点几秒的空隙） */
    static final double MERGE_GAP = 0.5;
    /** 文档每次上报最多加多少秒 */
    static final int MAX_READ_REPORT = 60;

    private static final TypeReference<List<List<Double>>> RANGES = new TypeReference<>() {
    };

    private final ItemProgressMapper progressMapper;
    private final ChapterItemMapper itemMapper;
    private final ChapterMapper chapterMapper;
    private final LearningEventMapper eventMapper;
    private final CompletionService completionService;
    private final ObjectMapper json = new ObjectMapper();

    public ProgressService(ItemProgressMapper progressMapper, ChapterItemMapper itemMapper, ChapterMapper chapterMapper,
                           LearningEventMapper eventMapper, CompletionService completionService) {
        this.progressMapper = progressMapper;
        this.itemMapper = itemMapper;
        this.chapterMapper = chapterMapper;
        this.eventMapper = eventMapper;
        this.completionService = completionService;
    }

    // ------------------------------------------------------------------ 视频

    @Transactional
    public ProgressView video(long studentId, Long itemId, VideoReport r) {
        ChapterItem item = requireItem(itemId, "video");
        completionService.requireUnlocked(studentId, item);
        if (r == null || r.duration() == null || !(r.duration() > 0) || r.duration().isInfinite()) {
            throw BusinessException.badRequest("视频时长不对");
        }
        LocalDateTime now = LocalDateTime.now();
        ItemProgress p = loadOrNew(studentId, item);
        if (p.getDurationSec() == null) {
            p.setDurationSec(round1(r.duration())); // 第一次定下，之后不改，防止改小时长
        }
        double duration = p.getDurationSec().doubleValue();

        List<double[]> old = parse(p.getWatchedRanges());
        List<double[]> all = new ArrayList<>(old);
        if (r.ranges() != null) {
            for (List<Double> range : r.ranges()) {
                if (range == null || range.size() != 2 || range.get(0) == null || range.get(1) == null) {
                    continue;
                }
                double s = Math.max(0, range.get(0));
                double e = Math.min(duration, range.get(1));
                if (e > s) {
                    all.add(new double[] {s, e});
                }
            }
        }
        List<double[]> merged = merge(all);
        double added = total(merged) - total(old);
        double elapsed = p.getId() == null ? FIRST_REPORT_SEC
                : Duration.between(p.getUpdatedAt(), now).toMillis() / 1000.0;
        if (added <= elapsed * MAX_RATE + SLACK_SEC) {
            p.setWatchedRanges(write(merged));
            p.setWatchedSec(round1(total(merged)));
        }
        // 否则：这次上报的观看时长比实际经过的时间还多，不可信，不算
        if (r.position() != null && r.position() >= 0) {
            p.setLastPosition(round1(Math.min(r.position(), duration)));
        }
        p.setUpdatedAt(now);
        if (p.getCompletedAt() == null && videoProgress(p) >= VIDEO_PASS) {
            complete(p, item, now);
        }
        save(p);
        return toView(p, item.getType());
    }

    // ------------------------------------------------------------------ 文档

    @Transactional
    public ProgressView reading(long studentId, Long itemId, ReadingReport r) {
        ChapterItem item = requireItem(itemId, "document");
        completionService.requireUnlocked(studentId, item);
        int sec = r == null || r.seconds() == null ? 0 : r.seconds();
        ItemProgress p = loadOrNew(studentId, item);
        if (sec <= 0) {
            return toView(p, item.getType());
        }
        LocalDateTime now = LocalDateTime.now();
        double elapsed = p.getId() == null ? FIRST_REPORT_SEC
                : Duration.between(p.getUpdatedAt(), now).toMillis() / 1000.0;
        int add = (int) Math.min(Math.min(sec, MAX_READ_REPORT), Math.floor(elapsed + 3));
        p.setReadSec(p.getReadSec() + Math.max(0, add));
        p.setUpdatedAt(now);
        save(p);
        return toView(p, item.getType());
    }

    /** 学生点"我已读完"：读满 30 秒才算 */
    @Transactional
    public ProgressView finishReading(long studentId, Long itemId) {
        ChapterItem item = requireItem(itemId, "document");
        completionService.requireUnlocked(studentId, item);
        ItemProgress p = loadOrNew(studentId, item);
        if (p.getReadSec() < READ_MIN_SEC) {
            throw BusinessException.badRequest("至少要读 " + READ_MIN_SEC + " 秒，还需 "
                    + (READ_MIN_SEC - p.getReadSec()) + " 秒");
        }
        if (p.getCompletedAt() == null) {
            LocalDateTime now = LocalDateTime.now();
            p.setUpdatedAt(now);
            complete(p, item, now);
            save(p);
        }
        return toView(p, item.getType());
    }

    // ------------------------------------------------------------------ 读

    /** 这个学生在这些项上的进度（只有视频和文档有），学习页打开时用 */
    public Map<Long, ProgressView> views(long studentId, Collection<ChapterItem> items) {
        Map<Long, String> types = new HashMap<>();
        items.stream().filter(it -> "video".equals(it.getType()) || "document".equals(it.getType()))
                .forEach(it -> types.put(it.getId(), it.getType()));
        Map<Long, ProgressView> result = new HashMap<>();
        if (types.isEmpty()) {
            return result;
        }
        progressMapper.selectList(new LambdaQueryWrapper<ItemProgress>()
                        .eq(ItemProgress::getStudentId, studentId)
                        .in(ItemProgress::getItemId, types.keySet()))
                .forEach(p -> result.put(p.getItemId(), toView(p, types.get(p.getItemId()))));
        return result;
    }

    // ------------------------------------------------------------------ 计算

    /** 把时间段按开始时间排好，重叠或相隔不到 0.5 秒的并成一段 */
    static List<double[]> merge(List<double[]> ranges) {
        List<double[]> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingDouble(a -> a[0]));
        List<double[]> out = new ArrayList<>();
        for (double[] r : sorted) {
            double[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && r[0] <= last[1] + MERGE_GAP) {
                last[1] = Math.max(last[1], r[1]);
            } else {
                out.add(new double[] {r[0], r[1]});
            }
        }
        return out;
    }

    /** Σ (e_i − s_i) */
    static double total(List<double[]> ranges) {
        return ranges.stream().mapToDouble(r -> r[1] - r[0]).sum();
    }

    private static double videoProgress(ItemProgress p) {
        if (p.getDurationSec() == null || p.getDurationSec().signum() <= 0) {
            return 0;
        }
        return Math.min(1, p.getWatchedSec().doubleValue() / p.getDurationSec().doubleValue());
    }

    // ------------------------------------------------------------------ 内部

    private ChapterItem requireItem(Long itemId, String type) {
        ChapterItem item = itemMapper.selectById(itemId);
        Chapter chapter = item == null ? null : chapterMapper.selectById(item.getChapterId());
        if (chapter == null || !"published".equals(chapter.getStatus())) {
            throw BusinessException.notFound("内容");
        }
        if (!type.equals(item.getType())) {
            throw BusinessException.badRequest("video".equals(type) ? "这一项不是视频" : "这一项不是文档");
        }
        return item;
    }

    private ItemProgress loadOrNew(long studentId, ChapterItem item) {
        ItemProgress p = progressMapper.selectOne(new LambdaQueryWrapper<ItemProgress>()
                .eq(ItemProgress::getStudentId, studentId)
                .eq(ItemProgress::getItemId, item.getId()));
        if (p != null) {
            return p;
        }
        p = new ItemProgress();
        p.setStudentId(studentId);
        p.setItemId(item.getId());
        p.setCourseId(item.getCourseId());
        p.setWatchedSec(BigDecimal.ZERO);
        p.setReadSec(0);
        return p;
    }

    private void save(ItemProgress p) {
        if (p.getId() == null) {
            progressMapper.insert(p);
        } else {
            progressMapper.updateById(p);
        }
    }

    /** 判定学完：记完成时间，再写一条 finish_item 学习事件 */
    private void complete(ItemProgress p, ChapterItem item, LocalDateTime now) {
        p.setCompletedAt(now);
        LearningEvent e = new LearningEvent();
        e.setStudentId(p.getStudentId());
        e.setEventType(LearningEvent.FINISH_ITEM);
        e.setTargetId(item.getId());
        e.setCourseId(item.getCourseId());
        eventMapper.insert(e);
    }

    private ProgressView toView(ItemProgress p, String type) {
        List<double[]> ranges = parse(p.getWatchedRanges());
        boolean video = "video".equals(type);
        double progress = video ? videoProgress(p) : Math.min(1, p.getReadSec() / (double) READ_MIN_SEC);
        return new ProgressView(p.getItemId(), type, Math.round(progress * 10000) / 10000.0,
                p.getWatchedSec().doubleValue(), p.getDurationSec() == null ? null : p.getDurationSec().doubleValue(),
                ranges.stream().map(r -> List.of(r[0], r[1])).toList(),
                p.getLastPosition() == null ? null : p.getLastPosition().doubleValue(),
                p.getReadSec(), p.getCompletedAt() != null, VIDEO_PASS, READ_MIN_SEC);
    }

    private List<double[]> parse(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<double[]> out = new ArrayList<>();
            for (List<Double> r : json.readValue(text, RANGES)) {
                out.add(new double[] {r.get(0), r.get(1)});
            }
            return out;
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String write(List<double[]> ranges) {
        try {
            return json.writeValueAsString(ranges.stream()
                    .map(r -> List.of(round1(r[0]).doubleValue(), round1(r[1]).doubleValue())).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }
}
