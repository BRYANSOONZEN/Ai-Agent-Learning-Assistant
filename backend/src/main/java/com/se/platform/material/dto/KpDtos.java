package com.se.platform.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 资料 → 知识点：返回给页面的数据 */
public final class KpDtos {

    private KpDtos() {
    }

    /** 一份资料（视频 / 文档项）转文字的进度。status：none 还没上传文件 / queued / processing / done / failed */
    public record MaterialStatus(Long itemId, String type, String title, String status, String message) {
    }

    /** 一条要点和它的出处编号 */
    public record KeyPoint(String text, List<String> sources) {
    }

    /** 一个知识点（草稿或已确认） */
    public record KpView(Long id, int sortOrder, String name, String summary, String importance,
                         List<KeyPoint> keyPoints, List<String> misconceptions, List<String> sources, boolean edited) {
    }

    /**
     * 老师在“知识点”里看到的全部：识别走到哪一步、每份资料的进度、知识点草稿。
     * status：none 还没点上传完毕 / waiting 等资料处理完 / recognizing 识别中 / draft 待确认 / confirmed 已确认 / failed
     */
    public record KpStatus(Long chapterId, String status, boolean uploadDone, String message, int processed, int total,
                           List<MaterialStatus> materials, List<KpView> kps,
                           LocalDateTime recognizedAt, LocalDateTime confirmedAt) {
    }

    /** 课程大纲里每章“知识点”入口的小标签 */
    public record ChapterBadge(Long chapterId, String status, int count) {
    }

    /** 老师新增或修改一个知识点 */
    public record KpSaveRequest(@NotBlank(message = "知识点名称不能为空") @Size(max = 100) String name,
                                @Size(max = 500) String summary, String importance,
                                List<KeyPoint> keyPoints, List<String> misconceptions) {
    }

    // ------------------------------------------------------------------ 知识点地图

    /** 地图的一列：一份资料 */
    public record MapColumn(Long itemId, String type, String title) {
    }

    /** 一格：这个知识点在这份资料的哪里。课件给页码，视频给开始、结束秒数（点了跳到开始） */
    public record MapSpot(String key, Integer page, Integer startSec, Integer endSec, String label) {
    }

    /** 一行：一个知识点，每份资料一格（itemId → 位置） */
    public record MapRow(Long kpId, String name, String summary, String importance, int keyPointCount,
                         java.util.Map<Long, List<MapSpot>> spots) {
    }

    public record KpMap(Long chapterId, String status, List<MapColumn> columns, List<MapRow> rows) {
    }
}
