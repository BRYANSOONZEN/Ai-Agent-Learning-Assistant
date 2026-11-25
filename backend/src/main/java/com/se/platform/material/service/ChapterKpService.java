package com.se.platform.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.KnowledgePoint;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.KnowledgePointMapper;
import com.se.platform.course.service.ChapterService;
import com.se.platform.course.service.CourseService;
import com.se.platform.material.dto.KpDtos.ChapterBadge;
import com.se.platform.material.dto.KpDtos.KeyPoint;
import com.se.platform.material.dto.KpDtos.KpMap;
import com.se.platform.material.dto.KpDtos.KpSaveRequest;
import com.se.platform.material.dto.KpDtos.KpStatus;
import com.se.platform.material.dto.KpDtos.KpView;
import com.se.platform.material.dto.KpDtos.MapColumn;
import com.se.platform.material.dto.KpDtos.MapRow;
import com.se.platform.material.dto.KpDtos.MapSpot;
import com.se.platform.material.dto.KpDtos.MaterialStatus;
import com.se.platform.material.entity.ChapterKp;
import com.se.platform.material.entity.ChapterKpState;
import com.se.platform.material.entity.MaterialJob;
import com.se.platform.material.mapper.ChapterKpMapper;
import com.se.platform.material.mapper.ChapterKpStateMapper;
import com.se.platform.material.mapper.MaterialJobMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 老师看识别进度、改知识点、确认；老师和学生看知识点地图。
 * 学生只看得到老师确认过的（confirmed_at 不为空），并且章节、课程都已发布。
 */
@Service
public class ChapterKpService {

    private static final TypeReference<List<KeyPoint>> POINTS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final ChapterService chapterService;
    private final CourseService courseService;
    private final ChapterMapper chapterMapper;
    private final ChapterItemMapper itemMapper;
    private final MaterialJobMapper jobMapper;
    private final ChapterKpMapper kpMapper;
    private final ChapterKpStateMapper stateMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final MaterialPipeline pipeline;
    private final KpRecognizer recognizer;
    private final ObjectMapper json;

    public ChapterKpService(ChapterService chapterService, CourseService courseService, ChapterMapper chapterMapper,
                            ChapterItemMapper itemMapper, MaterialJobMapper jobMapper, ChapterKpMapper kpMapper,
                            ChapterKpStateMapper stateMapper, KnowledgePointMapper knowledgePointMapper,
                            MaterialPipeline pipeline, KpRecognizer recognizer, ObjectMapper json) {
        this.chapterService = chapterService;
        this.courseService = courseService;
        this.chapterMapper = chapterMapper;
        this.itemMapper = itemMapper;
        this.jobMapper = jobMapper;
        this.kpMapper = kpMapper;
        this.stateMapper = stateMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.pipeline = pipeline;
        this.recognizer = recognizer;
        this.json = json;
    }

    // ------------------------------------------------------------------ 老师：进度和草稿

    public KpStatus status(Long chapterId) {
        chapterService.requireOwned(chapterId);
        ChapterKpState s = pipeline.state(chapterId);
        Map<Long, MaterialJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<MaterialJob>()
                        .eq(MaterialJob::getChapterId, chapterId))
                .stream().collect(Collectors.toMap(MaterialJob::getItemId, Function.identity(), (a, b) -> a));
        List<MaterialStatus> materials = new ArrayList<>();
        for (ChapterItem it : materialItems(chapterId)) {
            MaterialJob j = jobs.get(it.getId());
            String st = j != null ? j.getStatus() : it.getMaterialId() == null
                    ? ("document".equals(it.getType()) && it.getDescription() != null && !it.getDescription().isBlank()
                    ? MaterialJob.DONE : "none") : MaterialJob.QUEUED;
            String msg = j != null ? j.getMessage()
                    : MaterialJob.DONE.equals(st) ? "老师写的文字，不用处理" : "none".equals(st) ? "还没有上传文件" : null;
            materials.add(new MaterialStatus(it.getId(), it.getType(), it.getTitle(), st, msg));
        }
        int processed = (int) materials.stream().filter(m -> MaterialJob.DONE.equals(m.status())
                || MaterialJob.FAILED.equals(m.status())).count();
        int total = (int) materials.stream().filter(m -> !"none".equals(m.status())).count();
        return new KpStatus(chapterId, s.getStatus(), Boolean.TRUE.equals(s.getUploadDone()), s.getMessage(),
                processed, total, materials, kps(chapterId), s.getRecognizedAt(), s.getConfirmedAt());
    }

    /** 课程大纲：每章知识点入口的标签 */
    public List<ChapterBadge> badges(Long courseId) {
        courseService.requireOwned(courseId);
        List<Chapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>().eq(Chapter::getCourseId, courseId));
        if (chapters.isEmpty()) {
            return List.of();
        }
        Map<Long, ChapterKpState> states = stateMapper.selectList(new LambdaQueryWrapper<ChapterKpState>()
                        .in(ChapterKpState::getChapterId, chapters.stream().map(Chapter::getId).toList()))
                .stream().collect(Collectors.toMap(ChapterKpState::getChapterId, Function.identity()));
        return chapters.stream().filter(c -> states.containsKey(c.getId())).map(c -> new ChapterBadge(c.getId(),
                states.get(c.getId()).getStatus(),
                Math.toIntExact(kpMapper.selectCount(new LambdaQueryWrapper<ChapterKp>().eq(ChapterKp::getChapterId, c.getId())))))
                .toList();
    }

    public KpStatus uploadDone(Long chapterId) {
        chapterService.requireOwned(chapterId);
        pipeline.uploadDone(chapterId);
        return status(chapterId);
    }

    public KpStatus rerun(Long chapterId) {
        chapterService.requireOwned(chapterId);
        pipeline.rerun(chapterId);
        return status(chapterId);
    }

    @Transactional
    public KpView create(Long chapterId, KpSaveRequest req) {
        chapterService.requireOwned(chapterId);
        ChapterKp kp = new ChapterKp();
        kp.setChapterId(chapterId);
        kp.setSortOrder(Math.toIntExact(kpMapper.selectCount(new LambdaQueryWrapper<ChapterKp>()
                .eq(ChapterKp::getChapterId, chapterId))));
        apply(kp, req);
        kpMapper.insert(kp);
        touched(chapterId);
        return view(kp);
    }

    @Transactional
    public KpView update(Long kpId, KpSaveRequest req) {
        ChapterKp kp = requireKp(kpId);
        apply(kp, req);
        kpMapper.updateById(kp);
        touched(kp.getChapterId());
        return view(kp);
    }

    @Transactional
    public void delete(Long kpId) {
        ChapterKp kp = requireKp(kpId);
        kpMapper.deleteById(kpId);
        touched(kp.getChapterId());
    }

    /**
     * 老师确认：学生从此看得到知识点地图；同时同步到 knowledge_point（练习、诊断、对话话题都用它）。
     * 删掉的知识点不从 knowledge_point 里删：可能已经有题目连着它，老师在下面的列表里自己删。
     */
    @Transactional
    public KpStatus confirm(Long chapterId) {
        Chapter chapter = chapterService.requireOwned(chapterId);
        List<ChapterKp> list = kpMapper.selectList(new LambdaQueryWrapper<ChapterKp>()
                .eq(ChapterKp::getChapterId, chapterId).orderByAsc(ChapterKp::getSortOrder));
        if (list.isEmpty()) {
            throw BusinessException.badRequest("还没有知识点，不能确认");
        }
        ChapterKpState s = pipeline.state(chapterId);
        if (ChapterKpState.RECOGNIZING.equals(s.getStatus())) {
            throw BusinessException.badRequest("正在识别，等识别完再确认");
        }
        for (ChapterKp k : list) {
            KnowledgePoint p = k.getKnowledgePointId() == null ? null : knowledgePointMapper.selectById(k.getKnowledgePointId());
            boolean fresh = p == null;
            if (fresh) {
                p = new KnowledgePoint();
                p.setCourseId(chapter.getCourseId());
                p.setChapterId(chapterId);
                p.setDifficulty(2);
                p.setIsDifficult(false);
            }
            p.setName(k.getName());
            p.setContent(k.getSummary() == null || k.getSummary().isBlank() ? k.getName() : k.getSummary());
            p.setIsKey("重点".equals(k.getImportance()));
            p.setSortOrder(k.getSortOrder());
            if (fresh) {
                knowledgePointMapper.insert(p);
                k.setKnowledgePointId(p.getId());
                kpMapper.updateById(k);
            } else {
                knowledgePointMapper.updateById(p);
            }
        }
        s.setStatus(ChapterKpState.CONFIRMED);
        s.setConfirmedAt(LocalDateTime.now());
        s.setMessage("已确认 " + list.size() + " 个知识点");
        stateMapper.updateById(s);
        return status(chapterId);
    }

    // ------------------------------------------------------------------ 知识点地图

    /** 老师：草稿也能看 */
    public KpMap teacherMap(Long chapterId) {
        chapterService.requireOwned(chapterId);
        return map(chapterId);
    }

    /** 学生：只看已确认的，章节和课程都要已发布 */
    public KpMap studentMap(Long chapterId) {
        Chapter chapter = chapterService.require(chapterId);
        Course course = courseService.require(chapter.getCourseId());
        if (!"published".equals(chapter.getStatus()) || !"published".equals(course.getStatus())) {
            throw BusinessException.notFound("章节");
        }
        ChapterKpState s = stateMapper.selectOne(new LambdaQueryWrapper<ChapterKpState>()
                .eq(ChapterKpState::getChapterId, chapterId));
        if (s == null || s.getConfirmedAt() == null) {
            return new KpMap(chapterId, "none", List.of(), List.of());
        }
        return map(chapterId);
    }

    private KpMap map(Long chapterId) {
        List<KpRecognizer.Seg> segs = recognizer.segments(chapterId);
        Map<String, KpRecognizer.Seg> byKey = segs.stream()
                .collect(Collectors.toMap(KpRecognizer.Seg::key, Function.identity(), (a, b) -> a));
        List<MapColumn> columns = materialItems(chapterId).stream()
                .map(it -> new MapColumn(it.getId(), it.getType(), it.getTitle())).toList();
        List<MapRow> rows = new ArrayList<>();
        for (KpView k : kps(chapterId)) {
            Set<String> keys = new LinkedHashSet<>(k.sources());
            k.keyPoints().forEach(p -> keys.addAll(p.sources()));
            Map<Long, List<MapSpot>> spots = new LinkedHashMap<>();
            for (String key : keys) {
                KpRecognizer.Seg seg = byKey.get(key);
                if (seg == null) {
                    continue;   // 资料删掉或换过了，这个出处已经不存在
                }
                Integer start = seg.text().getStartSec() == null ? null : seg.text().getStartSec().intValue();
                Integer end = seg.text().getEndSec() == null ? null : seg.text().getEndSec().intValue();
                spots.computeIfAbsent(seg.item().getId(), x -> new ArrayList<>())
                        .add(new MapSpot(key, seg.text().getPage(), start, end, seg.label()));
            }
            spots.values().forEach(l -> l.sort((a, b) -> Integer.compare(
                    a.page() != null ? a.page() : a.startSec() != null ? a.startSec() : 0,
                    b.page() != null ? b.page() : b.startSec() != null ? b.startSec() : 0)));
            rows.add(new MapRow(k.id(), k.name(), k.summary(), k.importance(), k.keyPoints().size(), spots));
        }
        ChapterKpState s = pipeline.state(chapterId);
        return new KpMap(chapterId, s.getStatus(), columns, rows);
    }

    // ------------------------------------------------------------------ 内部

    private List<ChapterItem> materialItems(Long chapterId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ChapterItem>()
                .eq(ChapterItem::getChapterId, chapterId).in(ChapterItem::getType, "document", "video")
                .orderByAsc(ChapterItem::getSortOrder).orderByAsc(ChapterItem::getId));
    }

    private List<KpView> kps(Long chapterId) {
        return kpMapper.selectList(new LambdaQueryWrapper<ChapterKp>()
                        .eq(ChapterKp::getChapterId, chapterId).orderByAsc(ChapterKp::getSortOrder))
                .stream().map(this::view).toList();
    }

    private KpView view(ChapterKp k) {
        return new KpView(k.getId(), k.getSortOrder(), k.getName(), k.getSummary(), k.getImportance(),
                read(k.getKeyPoints(), POINTS), read(k.getMisconceptions(), STRINGS), read(k.getSources(), STRINGS),
                Boolean.TRUE.equals(k.getEdited()));
    }

    private <T> List<T> read(String text, TypeReference<List<T>> type) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private void apply(ChapterKp kp, KpSaveRequest req) {
        kp.setName(req.name().strip());
        kp.setSummary(req.summary());
        kp.setImportance("一般".equals(req.importance()) ? "一般" : "重点");
        try {
            List<KeyPoint> points = req.keyPoints() == null ? List.of() : req.keyPoints().stream()
                    .filter(p -> p.text() != null && !p.text().isBlank())
                    .map(p -> new KeyPoint(p.text().strip(), p.sources() == null ? List.of() : p.sources())).toList();
            if (points.size() > KpRecognizer.MAX_KEY_POINTS) {
                throw BusinessException.badRequest("每个知识点最多 " + KpRecognizer.MAX_KEY_POINTS
                        + " 条要点；内容多的拆成两个知识点");
            }
            kp.setKeyPoints(json.writeValueAsString(points));
            kp.setMisconceptions(json.writeValueAsString(req.misconceptions() == null ? List.of()
                    : req.misconceptions().stream().filter(m -> m != null && !m.isBlank()).toList()));
            if (kp.getSources() == null) {
                Set<String> all = new LinkedHashSet<>();
                points.forEach(p -> all.addAll(p.sources()));
                kp.setSources(json.writeValueAsString(all));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        kp.setEdited(true);
    }

    /** 老师改了已确认的知识点：要再确认一次，学生才看到改动 */
    private void touched(Long chapterId) {
        ChapterKpState s = pipeline.state(chapterId);
        if (ChapterKpState.CONFIRMED.equals(s.getStatus())) {
            s.setStatus(ChapterKpState.DRAFT);
            s.setMessage("你改了知识点，确认后学生才看到改动");
            stateMapper.updateById(s);
        } else if (ChapterKpState.NONE.equals(s.getStatus()) || ChapterKpState.FAILED.equals(s.getStatus())) {
            s.setStatus(ChapterKpState.DRAFT);
            s.setMessage("待你确认");
            stateMapper.updateById(s);
        }
    }

    private ChapterKp requireKp(Long kpId) {
        ChapterKp kp = kpMapper.selectById(kpId);
        if (kp == null) {
            throw BusinessException.notFound("知识点");
        }
        chapterService.requireOwned(kp.getChapterId());
        return kp;
    }
}
