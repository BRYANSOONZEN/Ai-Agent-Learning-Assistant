package com.se.platform.ai.log;

import com.se.platform.common.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 查看 AI 调用记录：GET /api/ai/logs?limit=100 */
@RestController
@RequestMapping("/api/ai/logs")
public class AiCallLogController {

    private final AiCallLogService service;

    public AiCallLogController(AiCallLogService service) {
        this.service = service;
    }

    /** 汇总 + 最近的记录（新的在前） */
    public record LogPage(AiCallLogService.Totals totals, List<AiCallLog> records) {
    }

    @GetMapping
    public Result<LogPage> list(@RequestParam(defaultValue = "100") int limit) {
        return Result.ok(new LogPage(service.totals(), service.recent(limit)));
    }
}
