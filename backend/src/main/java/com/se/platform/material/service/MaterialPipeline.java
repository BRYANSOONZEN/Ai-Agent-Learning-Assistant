package com.se.platform.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.course.entity.ChapterItem;
import com.se.platform.course.entity.Material;
import com.se.platform.course.mapper.ChapterItemMapper;
import com.se.platform.course.service.MaterialService;
import com.se.platform.material.entity.ChapterKpState;
import com.se.platform.material.entity.MaterialJob;
import com.se.platform.material.entity.MaterialText;
import com.se.platform.material.mapper.ChapterKpStateMapper;
import com.se.platform.material.mapper.MaterialJobMapper;
import com.se.platform.material.mapper.MaterialTextMapper;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 资料 → 文字 → 知识点的流水线（设计见 docs/模块说明/设计方案-费曼式AI对话.md）。
 *
 * <ol>
 *   <li>老师上传一份视频或 PDF，<b>马上</b>排队转文字（{@link #onUpload}），一次只处理一份</li>
 *   <li>老师点“上传完毕”（{@link #uploadDone}）</li>
 *   <li>这一章的资料都处理好了，就<b>自动</b>识别知识点（{@link #maybeRecognize}）</li>
 *   <li>识别完状态变成“待确认”，老师在“知识点”里看到；确认后学生能看到知识点地图</li>
 * </ol>
 * 老师之后又上传或换了文件：状态退回“没上传完”，提示老师传齐后再点“上传完毕”，重新识别时保留老师改过的知识点。
 *
 * <p>处理和识别都在后台线程里跑（几分钟），不让老师的请求干等。测试里 platform.material.async=false，直接在当前线程跑。
 */
@Slf4j
@Service
public class MaterialPipeline {

    private final OfficeToPdf officeToPdf;
    private final VideoTermFixer termFixer;

    private final ChapterItemMapper itemMapper;
    private final MaterialService materialService;
    private final MaterialJobMapper jobMapper;
    private final MaterialTextMapper textMapper;
    private final ChapterKpStateMapper stateMapper;
    private final MaterialExtractor extractor;
    private final KpRecognizer recognizer;
    private final boolean async;
    /** 一次只转一份资料：视频识别很吃 CPU */
    private final ExecutorService extractPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "material-extract"));
    private final ExecutorService recognizePool = Executors.newSingleThreadExecutor(r -> new Thread(r, "kp-recognize"));

    public MaterialPipeline(ChapterItemMapper itemMapper, MaterialService materialService, MaterialJobMapper jobMapper,
                            MaterialTextMapper textMapper, ChapterKpStateMapper stateMapper,
                            MaterialExtractor extractor, KpRecognizer recognizer,
                            @Value("${platform.material.async:true}") boolean async, OfficeToPdf officeToPdf,
                            VideoTermFixer termFixer) {
        this.officeToPdf = officeToPdf;
        this.termFixer = termFixer;
        this.itemMapper = itemMapper;
        this.materialService = materialService;
        this.jobMapper = jobMapper;
        this.textMapper = textMapper;
        this.stateMapper = stateMapper;
        this.extractor = extractor;
        this.recognizer = recognizer;
        this.async = async;
    }

    /**
     * 后端重启（或服务器崩了再启动）后接着做：处理到一半、排队中的资料重新排队；识别到一半的章节重新识别。
     * 已经处理完、识别完的结果都在数据库里，不用重做。
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void resume() {
        List<MaterialJob> unfinished = jobMapper.selectList(new LambdaQueryWrapper<MaterialJob>()
                .in(MaterialJob::getStatus, MaterialJob.QUEUED, MaterialJob.PROCESSING));
        List<ChapterKpState> recognizing = stateMapper.selectList(new LambdaQueryWrapper<ChapterKpState>()
                .in(ChapterKpState::getStatus, ChapterKpState.RECOGNIZING, ChapterKpState.WAITING));
        if (unfinished.isEmpty() && recognizing.isEmpty()) {
            return;
        }
        log.info("接着上次没做完的：{} 份资料，{} 章识别", unfinished.size(), recognizing.size());
        for (MaterialJob j : unfinished) {
            j.setStatus(MaterialJob.QUEUED);
            jobMapper.updateById(j);
            Long id = j.getId();
            run(extractPool, () -> process(id));
        }
        for (ChapterKpState st : recognizing) {
            st.setStatus(ChapterKpState.WAITING);
            stateMapper.updateById(st);
            maybeRecognize(st.getChapterId());
        }
    }

    @PreDestroy
    void shutdown() {
        extractPool.shutdownNow();
        recognizePool.shutdownNow();
    }

    // ------------------------------------------------------------------ 第 1 步：上传就转文字

    /** 上传或替换了文件。在上传的事务提交之后才开始，保证后台线程读得到新文件 */
    public void onUpload(Long itemId) {
        afterCommit(() -> enqueue(itemId));
    }

    /** 删掉了文件或整个内容项：它的文字不再参与识别 */
    public void onRemoved(Long itemId, Long chapterId) {
        afterCommit(() -> {
            jobMapper.delete(new LambdaQueryWrapper<MaterialJob>().eq(MaterialJob::getItemId, itemId));
            textMapper.delete(new LambdaQueryWrapper<MaterialText>().eq(MaterialText::getItemId, itemId));
            markChanged(chapterId);
        });
    }

    void enqueue(Long itemId) {
        ChapterItem item = itemMapper.selectById(itemId);
        if (item == null || item.getMaterialId() == null) {
            return;
        }
        MaterialJob job = jobMapper.selectOne(new LambdaQueryWrapper<MaterialJob>().eq(MaterialJob::getItemId, itemId));
        if (job == null) {
            job = new MaterialJob();
            job.setItemId(itemId);
        }
        job.setChapterId(item.getChapterId());
        job.setMaterialId(item.getMaterialId());
        job.setStatus(MaterialJob.QUEUED);
        job.setMessage(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        if (job.getId() == null) {
            jobMapper.insert(job);
        } else {
            jobMapper.updateById(job);
        }
        textMapper.delete(new LambdaQueryWrapper<MaterialText>().eq(MaterialText::getItemId, itemId));
        markChanged(item.getChapterId());
        Long jobId = job.getId();
        run(extractPool, () -> process(jobId));
    }

    void process(Long jobId) {
        MaterialJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus(MaterialJob.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        try {
            ChapterItem item = itemMapper.selectById(job.getItemId());
            Material m = materialService.require(job.getMaterialId());
            Path file = materialService.file(m);
            List<MaterialExtractor.Segment> segs;
            String meta = null;
            String kind = kindOf(item, m);
            if ("text".equals(kind)) {   // 纯文本、Markdown：直接读，不用交给处理服务
                segs = List.of(new MaterialExtractor.Segment("T1", "text", null, null, null,
                        Files.readString(file, StandardCharsets.UTF_8)));
            } else if (kind == null) {
                throw new MaterialExtractor.ExtractException("暂时只能识别 PDF、PPT、Word、文本和视频，这个文件（"
                        + m.getOriginalName() + "）请转成 PDF 再上传");
            } else if ("office".equals(kind)) {  // PPT、Word：先用本机 Office 转成 PDF，再按 PDF 处理
                MaterialExtractor.Result r = extractor.extract(officeToPdf.convert(file), "pdf");
                segs = r.segments();
                meta = r.meta();
            } else {
                MaterialExtractor.Result r = extractor.extract(file, kind);
                segs = r.segments();
                meta = r.meta();
            }
            int n = 0;
            for (MaterialExtractor.Segment s : segs) {
                if (s.text() == null || s.text().isBlank()) {
                    continue;
                }
                MaterialText t = new MaterialText();
                t.setItemId(job.getItemId());
                t.setChapterId(job.getChapterId());
                t.setSegKey(s.key());
                t.setKind(s.kind());
                t.setPage(s.page());
                t.setStartSec(s.start());
                t.setEndSec(s.end());
                t.setContent(s.text().strip());
                t.setSortOrder(n++);
                textMapper.insert(t);
            }
            job.setStatus(MaterialJob.DONE);
            job.setMessage(summary(segs));
            job.setMeta(meta);
        } catch (MaterialExtractor.ExtractException e) {
            job.setStatus(MaterialJob.FAILED);
            job.setMessage(e.getMessage());
        } catch (Exception e) {
            log.warn("资料处理失败 job={}", jobId, e);
            job.setStatus(MaterialJob.FAILED);
            job.setMessage("处理失败：" + e.getMessage());
        }
        job.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        maybeRecognize(job.getChapterId());
    }

    /** video / pdf / office（PPT、Word）/ text；不支持的返回 null */
    static String kindOf(ChapterItem item, Material m) {
        if ("video".equals(item.getType())) {
            return "video";
        }
        String name = m.getOriginalName() == null ? "" : m.getOriginalName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return "pdf";
        }
        if (OfficeToPdf.isOffice(name)) {
            return "office";
        }
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return "text";
        }
        return null;
    }

    private static String summary(List<MaterialExtractor.Segment> segs) {
        long pages = segs.stream().filter(s -> "pdf".equals(s.kind())).count();
        long images = segs.stream().filter(s -> "image".equals(s.kind())).count();
        long video = segs.stream().filter(s -> "video".equals(s.kind())).count();
        if (video > 0) {
            return "视频转出 " + video + " 段文字";
        }
        if (pages + images > 0) {
            return "取到 " + pages + " 页文字、" + images + " 张图的内容";
        }
        return "取到 " + segs.size() + " 段文字";
    }

    // ------------------------------------------------------------------ 第 2、3 步：上传完毕 → 自动识别

    /** 老师点“上传完毕”：资料都处理好就马上识别，还在处理的等处理完自动开始 */
    public void uploadDone(Long chapterId) {
        ChapterKpState s = state(chapterId);
        s.setUploadDone(true);
        s.setStatus(ChapterKpState.WAITING);
        s.setMessage(null);
        stateMapper.updateById(s);
        maybeRecognize(chapterId);
    }

    /** 资料都处理好了、老师也点了上传完毕，就开始识别；否则只更新进度 */
    public synchronized void maybeRecognize(Long chapterId) {
        ChapterKpState s = state(chapterId);
        if (!Boolean.TRUE.equals(s.getUploadDone()) || ChapterKpState.RECOGNIZING.equals(s.getStatus())
                || ChapterKpState.DRAFT.equals(s.getStatus()) || ChapterKpState.CONFIRMED.equals(s.getStatus())) {
            return;
        }
        List<MaterialJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<MaterialJob>()
                .eq(MaterialJob::getChapterId, chapterId));
        long pending = jobs.stream().filter(j -> MaterialJob.QUEUED.equals(j.getStatus())
                || MaterialJob.PROCESSING.equals(j.getStatus())).count();
        if (pending > 0) {
            s.setStatus(ChapterKpState.WAITING);
            s.setMessage("正在处理资料：" + (jobs.size() - pending) + "/" + jobs.size() + " 份完成，处理完会自动识别知识点");
            stateMapper.updateById(s);
            return;
        }
        s.setStatus(ChapterKpState.RECOGNIZING);
        s.setMessage("正在识别知识点，约 1 分钟");
        stateMapper.updateById(s);
        run(recognizePool, () -> {
            termFixer.fix(chapterId);       // 先用课件里的专业词纠正视频的同音错字（每个视频只做一次）
            recognizer.recognize(chapterId);
        });
    }

    /** 老师点“重新识别”：保留老师改过的知识点 */
    public void rerun(Long chapterId) {
        ChapterKpState s = state(chapterId);
        if (ChapterKpState.RECOGNIZING.equals(s.getStatus())) {
            return;
        }
        s.setUploadDone(true);
        s.setStatus(ChapterKpState.WAITING);
        stateMapper.updateById(s);
        maybeRecognize(chapterId);
    }

    /** 资料变了：已经识别过的，退回“没上传完”，等老师传齐后再点上传完毕 */
    private void markChanged(Long chapterId) {
        ChapterKpState s = state(chapterId);
        if (ChapterKpState.DRAFT.equals(s.getStatus()) || ChapterKpState.CONFIRMED.equals(s.getStatus())
                || ChapterKpState.FAILED.equals(s.getStatus())) {
            s.setUploadDone(false);
            s.setStatus(ChapterKpState.NONE);
            // 学生在重新识别之前仍然看到上一次确认的知识点；重新识别后要老师再确认一次
            s.setMessage("资料有更新。传齐后请点“上传完毕”重新识别，你改过的知识点会保留");
            stateMapper.updateById(s);
        }
    }

    /** 这一章的状态行，没有就建一行 */
    public synchronized ChapterKpState state(Long chapterId) {
        ChapterKpState s = stateMapper.selectOne(new LambdaQueryWrapper<ChapterKpState>()
                .eq(ChapterKpState::getChapterId, chapterId));
        if (s == null) {
            s = new ChapterKpState();
            s.setChapterId(chapterId);
            s.setUploadDone(false);
            s.setStatus(ChapterKpState.NONE);
            stateMapper.insert(s);
        }
        return s;
    }

    // ------------------------------------------------------------------ 工具

    private void run(ExecutorService pool, Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("后台任务失败", e);
            }
        };
        if (async) {
            pool.submit(safe);
        } else {
            safe.run();
        }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
