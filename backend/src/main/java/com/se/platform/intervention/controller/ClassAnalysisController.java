package com.se.platform.intervention.controller;

import com.se.platform.common.Result;
import com.se.platform.intervention.dto.ClassAnalysisDtos.ClassBoard;
import com.se.platform.intervention.service.ClassAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 老师干预：全班分析（docs/模块说明/老师干预-全班分析.md）。老师接口 */
@RestController
public class ClassAnalysisController {

    private final ClassAnalysisService service;

    public ClassAnalysisController(ClassAnalysisService service) {
        this.service = service;
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
}
