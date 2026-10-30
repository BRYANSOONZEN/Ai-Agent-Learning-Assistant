package com.se.platform.profile.controller;

import com.se.platform.common.Result;
import com.se.platform.profile.dto.ProfileDtos.StudentProfile;
import com.se.platform.profile.service.ProfileService;
import com.se.platform.record.service.RecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学生画像，前缀 /api/profiles（docs/api.md 第 5 节）。打开时现算，不存快照 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final RecordService recordService;

    public ProfileController(ProfileService profileService, RecordService recordService) {
        this.profileService = profileService;
        this.recordService = recordService;
    }

    /** GET /api/profiles/me：当前学生的画像（登录没做，是演示学生） */
    @GetMapping("/me")
    public Result<StudentProfile> me() {
        return Result.ok(profileService.student(recordService.currentStudentId()));
    }

    /** GET /api/profiles/students/{id}：某个学生的画像 */
    @GetMapping("/students/{id}")
    public Result<StudentProfile> student(@PathVariable Long id) {
        return Result.ok(profileService.student(id));
    }
}
