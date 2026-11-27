package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.common.BusinessException;
import com.se.platform.course.entity.Course;
import com.se.platform.course.entity.Enrollment;
import com.se.platform.course.mapper.CourseMapper;
import com.se.platform.course.mapper.EnrollmentMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 选课：学生在“课程学习”里点“加入课程”，加入后才能学这门课。
 * 老师那边的“全班”（全班分析、截止情况、课程学生人数）= 选了这门课、账号在用的学生。
 */
@Service
public class EnrollmentService {

    private final EnrollmentMapper enrollmentMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    public EnrollmentService(EnrollmentMapper enrollmentMapper, CourseMapper courseMapper, UserMapper userMapper) {
        this.enrollmentMapper = enrollmentMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    /** 学生加入一门已发布的课；已经加入的直接返回 */
    public void join(long studentId, Long courseId) {
        Course c = courseId == null ? null : courseMapper.selectById(courseId);
        if (c == null || !"published".equals(c.getStatus())) {
            throw BusinessException.notFound("课程");
        }
        if (enrolled(studentId, courseId)) {
            return;
        }
        Enrollment e = new Enrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentId);
        e.setCreatedAt(LocalDateTime.now());
        enrollmentMapper.insert(e);
    }

    public boolean enrolled(long studentId, Long courseId) {
        return enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId).eq(Enrollment::getCourseId, courseId)) > 0;
    }

    /** 学生要学这门课的内容之前检查 */
    public void requireEnrolled(long studentId, Long courseId) {
        if (!enrolled(studentId, courseId)) {
            throw BusinessException.forbidden("还没加入这门课，先在“课程学习”里点“加入课程”");
        }
    }

    /** 这个学生加入了哪些课 */
    public Set<Long> courseIds(long studentId) {
        return enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getStudentId, studentId))
                .stream().map(Enrollment::getCourseId).collect(Collectors.toSet());
    }

    /** 全班 = 选了这门课、账号在用的学生（按 id 排） */
    public List<User> students(Long courseId) {
        List<Long> ids = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId)).stream().map(Enrollment::getStudentId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, ids)
                .eq(User::getRole, "student").eq(User::getStatus, User.STATUS_ACTIVE).orderByAsc(User::getId));
    }

    public long count(Long courseId) {
        return enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getCourseId, courseId));
    }
}
