package com.se.platform.diagnosis.controller;

import com.se.platform.common.Result;
import com.se.platform.diagnosis.dto.DiagnosisDtos.CourseOverview;
import com.se.platform.diagnosis.dto.DiagnosisDtos.StudentDiagnosis;
import com.se.platform.diagnosis.service.DiagnosisService;
import com.se.platform.record.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学情诊断，前缀 /api/diagnosis：学生看自己的诊断；老师看课程的全班诊断和单个学生。
 * 掌握度在交卷时已经算好（MasteryService），这里只读；全班指加入了这门课的学生。
 */
@RestController
@RequestMapping("/api/diagnosis")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;
    private final RecordService recordService;

    public DiagnosisController(DiagnosisService diagnosisService, RecordService recordService) {
        this.diagnosisService = diagnosisService;
        this.recordService = recordService;
    }

    /** GET /api/diagnosis/me：当前登录学生自己的诊断 */
    @GetMapping("/me")
    public Result<StudentDiagnosis> me() {
        return Result.ok(diagnosisService.student(recordService.currentStudentId()));
    }

    /** GET /api/diagnosis/students/{id}：某个学生的诊断（老师看） */
    @GetMapping("/students/{id}")
    public Result<StudentDiagnosis> student(@PathVariable Long id) {
        return Result.ok(diagnosisService.student(id));
    }

    /** GET /api/diagnosis/courses/{id}：一门课所有学生的知识点汇总（只能看自己的课） */
    @GetMapping("/courses/{id}")
    public Result<CourseOverview> course(@PathVariable Long id) {
        return Result.ok(diagnosisService.course(id));
    }
}
