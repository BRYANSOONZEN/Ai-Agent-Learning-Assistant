package com.se.platform.profile.controller;

import com.se.platform.common.Result;
import com.se.platform.profile.dto.ProfileDtos.StudentProfile;
import com.se.platform.profile.dto.ProfileDtos.CourseOption;
import com.se.platform.profile.dto.ProfileDtos.CourseProfile;
import com.se.platform.profile.service.CourseProfileService;
import com.se.platform.profile.service.ProfileService;
import java.util.List;
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
    private final CourseProfileService courseProfileService;
    private final com.se.platform.course.mapper.CourseMapper courseMapper;

    public ProfileController(ProfileService profileService, RecordService recordService,
                             CourseProfileService courseProfileService,
                             com.se.platform.course.mapper.CourseMapper courseMapper) {
        this.profileService = profileService;
        this.recordService = recordService;
        this.courseProfileService = courseProfileService;
        this.courseMapper = courseMapper;
    }

    /** GET /api/profiles/me/courses：可以看画像的课程（学完几章） */
    @GetMapping("/me/courses")
    public Result<List<CourseOption>> myCourses() {
        return Result.ok(courseProfileService.courses(recordService.currentStudentId()));
    }

    /** GET /api/profiles/me/courses/{id}：这门课的每章画像 + 课程四维（全部章节学完才开放四维） */
    @GetMapping("/me/courses/{id}")
    public Result<CourseProfile> myCourse(@PathVariable Long id) {
        long s = recordService.currentStudentId();
        return Result.ok(courseProfileService.profile(s, courseMapper.findUserName(s), id));
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
