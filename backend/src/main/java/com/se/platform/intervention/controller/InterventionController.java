package com.se.platform.intervention.controller;

import com.se.platform.common.BusinessException;
import com.se.platform.common.Result;
import com.se.platform.intervention.dto.InterventionDtos.Board;
import com.se.platform.intervention.dto.InterventionDtos.InterventionView;
import com.se.platform.intervention.dto.InterventionDtos.MyAssignments;
import com.se.platform.intervention.dto.InterventionDtos.ReviewRequest;
import com.se.platform.intervention.service.InterventionService;
import com.se.platform.record.service.RecordService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预警与干预（docs/计划-干预A和B.md）。老师端检查"是不是自己的课"；
 * 登录还没做，学生端是演示学生（见 DemoIdentity），"学生不能调老师接口"等登录做好再加。
 */
@RestController
@RequestMapping("/api")
public class InterventionController {

    private final InterventionService service;
    private final RecordService recordService;

    public InterventionController(InterventionService service, RecordService recordService) {
        this.service = service;
        this.recordService = recordService;
    }

    /** GET /api/interventions/board?courseId=1：扫描生成新预警，返回待处理预警（带系统建议）和干预列表 */
    @GetMapping("/interventions/board")
    public Result<Board> board(@RequestParam Long courseId) {
        return Result.ok(service.board(courseId));
    }

    /** GET /api/interventions/open-count：侧边栏「干预管理」后面的数字 */
    @GetMapping("/interventions/open-count")
    public Result<Integer> openCount() {
        return Result.ok(service.openCount());
    }

    /** POST /api/interventions/{id}/review {approve: true, exerciseIds: [3, 5], note: "..."} */
    @PostMapping("/interventions/{id}/review")
    public Result<InterventionView> review(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return Result.ok(service.review(id, req));
    }

    /** PUT /api/alerts/{id} {status: "ignored"}：忽略预警 */
    @PutMapping("/alerts/{id}")
    public Result<Void> updateAlert(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!"ignored".equals(body.get("status"))) {
            throw BusinessException.badRequest("status 只能是 ignored");
        }
        service.ignoreAlert(id);
        return Result.ok();
    }

    /** GET /api/interventions/mine：学生看老师布置给自己的 */
    @GetMapping("/interventions/mine")
    public Result<MyAssignments> mine() {
        return Result.ok(service.mine(recordService.currentStudentId()));
    }
}
