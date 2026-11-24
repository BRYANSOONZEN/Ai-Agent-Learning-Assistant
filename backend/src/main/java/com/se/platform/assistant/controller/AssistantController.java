package com.se.platform.assistant.controller;

import com.se.platform.assistant.dto.AssistantDtos.Summary;
import com.se.platform.assistant.service.AssistantService;
import com.se.platform.common.Result;
import com.se.platform.intervention.service.InterventionService;
import com.se.platform.record.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人学习助手，前缀 /api/assistant（docs/计划-个人学习助手.md）。
 * 登录还没做，学生是演示学生（见 DemoIdentity）。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final InterventionService interventionService;
    private final RecordService recordService;

    public AssistantController(AssistantService assistantService, InterventionService interventionService,
                               RecordService recordService) {
        this.assistantService = assistantService;
        this.interventionService = interventionService;
        this.recordService = recordService;
    }

    /** GET /api/assistant/summary：学过的章节、进度，学完的章节带个人总结 */
    @GetMapping("/summary")
    public Result<Summary> summary() {
        long student = recordService.currentStudentId();
        synchronized (InterventionService.SCAN_LOCK) {
            // 先扫描这个学生：有新问题时干预自动执行，助手里马上能看到"老师布置"
            interventionService.scanStudent(student);
        }
        return Result.ok(assistantService.summary(student));
    }
}
