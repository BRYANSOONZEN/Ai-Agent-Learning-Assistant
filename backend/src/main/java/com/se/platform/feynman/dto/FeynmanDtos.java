package com.se.platform.feynman.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 费曼式 AI 对话：返回给页面的数据 */
public final class FeynmanDtos {

    private FeynmanDtos() {
    }

    /** 老师可选的知识点（本章已识别的） */
    public record Candidate(Long id, String name, String importance) {
    }

    /**
     * 老师看到的设置：选了哪些知识点、每个的草稿。status：none 还没生成 / draft 待确认 / confirmed 已确认。
     * kpStatus：这一章知识点识别的状态，不是 confirmed 时不能生成草稿。
     * stale：草稿生成之后，老师又改过（或删了）的知识点，例如“边界测试值的选取：要点改过”；要重新生成草稿。
     */
    public record SetupView(Long itemId, String status, List<Long> kpIds, List<JsonNode> drafts,
                            List<Candidate> candidates, int maxKps, String kpStatus, List<String> stale) {
    }

    public record DraftRequest(List<Long> kpIds) {
    }

    public record SayRequest(String text) {
    }

    public record Message(String role, String content, int kpIdx) {
    }

    /** 一个知识点的进度和诊断（不打分）。point 的 status：clear / partial / wrong / none；how：self / hint1～3 / pointer / supplemented */
    public record PointView(int no, String status, String how) {
    }

    public record KpView(String name, String peer, String phase, int turns, int maxTurns, int clear, int total,
                         List<PointView> points, Boolean misconceptionFixed, String review) {
    }

    /** 学生看到的对话（小结也只给学生看）。status：open / done（done 后下一项解锁） */
    public record SessionView(Long id, String status, int kpIdx, List<KpView> kps, List<Message> messages) {
    }

    /** 学生端：这个对话项有没有可用的费曼对话（没有就用原来的对话） */
    public record Availability(boolean enabled, int kpCount, List<String> names) {
    }
}
