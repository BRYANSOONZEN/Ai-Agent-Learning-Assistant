package com.se.platform.intervention.controller;

import com.se.platform.common.Result;
import com.se.platform.intervention.dto.ClassAnalysisDtos.ClassBoard;
import com.se.platform.intervention.dto.ClassAnalysisDtos.Extend;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushCreate;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushUpdate;
import com.se.platform.intervention.dto.ClassAnalysisDtos.PushView;
import com.se.platform.intervention.dto.ClassAnalysisDtos.Remind;
import com.se.platform.intervention.service.ClassAnalysisService;
import com.se.platform.intervention.service.ClassPushService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 老师干预：全班分析（docs/模块说明/老师干预-全班分析.md）。老师接口 */
@RestController
public class ClassAnalysisController {

    private final ClassAnalysisService service;
    private final ClassPushService pushService;

    public ClassAnalysisController(ClassAnalysisService service, ClassPushService pushService) {
        this.service = service;
        this.pushService = pushService;
    }

    /** GET /api/class-analysis?courseId=：这门课每次练习的分析（到截止或全交完才分析；交卷不到 80% 作废） */
    @GetMapping("/api/class-analysis")
    public Result<ClassBoard> board(@RequestParam Long courseId) {
        return Result.ok(service.board(courseId));
    }

    /** POST /api/class-issues/{id}/ok：老师检查后，这道题没问题（算回要点 × 考查类型） */
    @PostMapping("/api/class-issues/{id}/ok")
    public Result<Void> ok(@PathVariable Long id) {
        service.questionOk(id);
        return Result.ok(null);
    }

    /** POST /api/class-issues/{id}/fixed：老师已经修改了这道题 */
    @PostMapping("/api/class-issues/{id}/fixed")
    public Result<Void> fixed(@PathVariable Long id) {
        service.questionFixed(id);
        return Result.ok(null);
    }

    /** POST /api/class-issues/{id}/void：作废这道题（不算进任何人的掌握度，重算） */
    @PostMapping("/api/class-issues/{id}/void")
    public Result<Void> voidQuestion(@PathVariable Long id) {
        service.questionVoid(id);
        return Result.ok(null);
    }

    /** POST /api/class-issues/{id}/ignore：先不处理 */
    @PostMapping("/api/class-issues/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id) {
        service.ignore(id);
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 处理卡片：补充讲解 + 新题 → 推送

    /** POST /api/class-pushes { issueIds, aiExplanation }：处理卡片，起草讲解（可选）+ 出 3 道新题，存成草稿 */
    @PostMapping("/api/class-pushes")
    public Result<PushView> createPush(@RequestBody PushCreate body) {
        return Result.ok(pushService.create(body));
    }

    @GetMapping("/api/class-pushes/{id}")
    public Result<PushView> getPush(@PathVariable Long id) {
        return Result.ok(pushService.get(id));
    }

    /** PUT /api/class-pushes/{id} { explanation, dueAt, studentIds }：老师修改（题目用 /api/exercises/{id} 改、删） */
    @PutMapping("/api/class-pushes/{id}")
    public Result<PushView> updatePush(@PathVariable Long id, @RequestBody PushUpdate body) {
        return Result.ok(pushService.update(id, body));
    }

    /** POST /api/class-pushes/{id}/regenerate：重新出一批新题 */
    @PostMapping("/api/class-pushes/{id}/regenerate")
    public Result<PushView> regenerate(@PathVariable Long id) {
        return Result.ok(pushService.regenerate(id));
    }

    /** POST /api/class-pushes/{id}/push：推送给学生 */
    @PostMapping("/api/class-pushes/{id}/push")
    public Result<PushView> push(@PathVariable Long id) {
        return Result.ok(pushService.push(id));
    }

    // ------------------------------------------------------------------ 分析作废时

    /** POST /api/class-analysis/items/{itemId}/remind { message }：提醒没交的学生；返回提醒了几人 */
    @PostMapping("/api/class-analysis/items/{itemId}/remind")
    public Result<Integer> remind(@PathVariable Long itemId, @RequestBody(required = false) Remind body) {
        return Result.ok(pushService.remind(itemId, body == null ? null : body.message()));
    }

    /** POST /api/class-analysis/items/{itemId}/extend { dueAt }：延长截止时间 */
    @PostMapping("/api/class-analysis/items/{itemId}/extend")
    public Result<Void> extend(@PathVariable Long itemId, @RequestBody Extend body) {
        pushService.extend(itemId, body.dueAt());
        return Result.ok(null);
    }
}
