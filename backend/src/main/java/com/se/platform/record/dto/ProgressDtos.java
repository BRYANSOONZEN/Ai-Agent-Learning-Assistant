package com.se.platform.record.dto;

import java.util.List;

/** 视频和文档的学习进度 */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    /**
     * 视频上报（播放时每 10 秒一次，暂停、拖动、离开页面时再报一次）。
     * duration：视频总时长（秒）；ranges：真正播放过的时间段 [[开始秒, 结束秒], ...]；position：现在播到哪里。
     */
    public record VideoReport(Double duration, List<List<Double>> ranges, Double position) {
    }

    /** 文档上报：距离上一次上报，页面在前台又过了几秒 */
    public record ReadingReport(Integer seconds) {
    }

    /**
     * 一项的进度。
     * 视频：progress = watchedSec ÷ durationSec，≥ videoPass（0.9）算学完；ranges 是合并后看过的时间段。
     * 文档：readSec ≥ readMinSec（30）以后才能点"我已读完"，点了才算学完。
     */
    public record ProgressView(Long itemId, String type, double progress, double watchedSec, Double durationSec,
                               List<List<Double>> ranges, Double lastPosition, int readSec, boolean completed,
                               double videoPass, int readMinSec) {
    }
}
