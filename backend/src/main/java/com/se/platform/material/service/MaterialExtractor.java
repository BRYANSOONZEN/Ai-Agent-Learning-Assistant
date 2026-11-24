package com.se.platform.material.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

/**
 * 把一份资料转成文字。正式实现是 {@link HttpMaterialExtractor}（调本机常驻的 Python 资料处理服务）；
 * 测试里换成假的，不用真的跑语音识别和看图。
 */
public interface MaterialExtractor {

    /** kind：pdf / video */
    Result extract(Path file, String kind);

    /** 一段文字。key：P4、P13-图1、01；kind：pdf / image / video */
    record Segment(String key, String kind, Integer page, BigDecimal start, BigDecimal end, String text) {
    }

    /** meta：处理服务返回的统计（JSON 原文），存档用 */
    record Result(List<Segment> segments, String meta) {
    }

    /** 处理失败：message 是给老师看的原因 */
    class ExtractException extends RuntimeException {
        public ExtractException(String message) {
            super(message);
        }
    }
}
