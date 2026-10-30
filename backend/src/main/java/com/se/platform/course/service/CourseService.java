package com.se.platform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.se.platform.common.BusinessException;
import com.se.platform.common.PageResult;
import com.se.platform.course.dto.CourseSaveRequest;
import com.se.platform.course.dto.CourseView;
import com.se.platform.course.entity.Chapter;
import com.se.platform.course.entity.Course;
import com.se.platform.course.mapper.ChapterMapper;
import com.se.platform.course.mapper.CourseMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 课程的增删改查 */
@Service
public class CourseService {

    /** 登录功能还没做，当前老师先固定为 2 号用户（李老师），见 currentTeacherId */
    private static final long DEFAULT_TEACHER_ID = 2L;

    /** 老师建课时可选的模块（key → 中文名），顺序就是建课页标签的顺序。章节介绍和预览发布每门课都有，不在这里 */
    public static final Map<String, String> MODULES = new LinkedHashMap<>();

    static {
        MODULES.put("video", "视频");
        MODULES.put("document", "文档");
        MODULES.put("knowledge", "知识点");
        MODULES.put("dialogue", "AI 对话");
        MODULES.put("exercise", "题目");
    }

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;

    public CourseService(CourseMapper courseMapper, ChapterMapper chapterMapper) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
    }

    /**
     * 当前登录的老师。登录功能做好后，只改这里：从 token 里取用户 id。
     * 现在固定为 2 号用户（李老师），和前端模拟登录的老师一致。
     */
    public long currentTeacherId() {
        return DEFAULT_TEACHER_ID;
    }

    /** 分页列表，按名称模糊搜索，新的在前（全部课程，学生端浏览用） */
    public PageResult<CourseView> page(int page, int size, String keyword) {
        return page(page, size, keyword, null, null);
    }

    /** 当前老师自己的课程（老师端「课程管理」用） */
    public PageResult<CourseView> pageMine(int page, int size, String keyword) {
        return page(page, size, keyword, currentTeacherId(), null);
    }

    /** 已发布的课程（学生端「课程学习」用） */
    public PageResult<CourseView> pagePublished(int page, int size, String keyword) {
        return page(page, size, keyword, null, "published");
    }

    private PageResult<CourseView> page(int page, int size, String keyword, Long teacherId, String status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                // eq / like 的第一个参数是条件：为 true 才加这个条件
                .eq(teacherId != null, Course::getTeacherId, teacherId)
                .eq(status != null, Course::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), Course::getName, keyword)
                .orderByDesc(Course::getId);
        Page<Course> result = courseMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, this::toView);
    }

    public CourseView get(Long id) {
        return toView(require(id));
    }

    /** 老师进入建课页时用：不是自己的课返回 403 */
    public CourseView getOwned(Long id) {
        return toView(requireOwned(id));
    }

    public CourseView create(CourseSaveRequest req) {
        Course course = new Course();
        course.setTeacherId(currentTeacherId());
        copy(req, course);
        courseMapper.insert(course);
        return toView(course);
    }

    public CourseView update(Long id, CourseSaveRequest req) {
        Course course = requireOwned(id);
        copy(req, course);
        courseMapper.updateById(course);
        return toView(course);
    }

    /** 课程下还有章节时不能删，避免误删整门课的内容 */
    public void delete(Long id) {
        requireOwned(id);
        long chapters = countChapters(id);
        if (chapters > 0) {
            throw BusinessException.conflict("这门课还有 " + chapters + " 个章节，请先删除章节");
        }
        courseMapper.deleteById(id);
    }

    /** 取课程，不存在就抛 404。其他 Service 也用它检查 courseId */
    public Course require(Long id) {
        Course course = id == null ? null : courseMapper.selectById(id);
        if (course == null) {
            throw BusinessException.notFound("课程");
        }
        return course;
    }

    /** 取课程并检查是不是当前老师的。改课程、改章节和章节里的内容，都要先过这一关 */
    public Course requireOwned(Long id) {
        Course course = require(id);
        if (course.getTeacherId() != currentTeacherId()) {
            throw BusinessException.forbidden("这不是你的课程，不能修改");
        }
        return course;
    }

    private void copy(CourseSaveRequest req, Course course) {
        course.setName(req.name().trim());
        course.setDescription(req.description());
        course.setStatus(req.status() == null ? "draft" : req.status());
        course.setModules(String.join(",", normalizeModules(req.modules())));
    }

    /** 不传 = 全部；去掉重复、按 MODULES 的顺序排；题目要关联知识点，所以选了题目就自动带上知识点 */
    private List<String> normalizeModules(List<String> requested) {
        if (requested == null) {
            return List.copyOf(MODULES.keySet());
        }
        for (String m : requested) {
            if (!MODULES.containsKey(m)) {
                throw BusinessException.badRequest("没有这个模块：" + m);
            }
        }
        if (requested.isEmpty()) {
            throw BusinessException.badRequest("至少选一个模块");
        }
        return MODULES.keySet().stream()
                .filter(m -> requested.contains(m) || (m.equals("knowledge") && requested.contains("exercise")))
                .toList();
    }

    public List<String> modulesOf(Course course) {
        if (course.getModules() == null || course.getModules().isBlank()) {
            return List.copyOf(MODULES.keySet());
        }
        return Arrays.asList(course.getModules().split(","));
    }

    /** 这门课没开这个模块就抛 400，例如没开视频却上传视频 */
    public void requireModule(Course course, String module) {
        if (!modulesOf(course).contains(module)) {
            throw BusinessException.badRequest("这门课没有开启「" + MODULES.get(module) + "」模块，可以在课程设置里打开");
        }
    }

    private long countChapters(Long courseId) {
        return chapterMapper.selectCount(new LambdaQueryWrapper<Chapter>().eq(Chapter::getCourseId, courseId));
    }

    private CourseView toView(Course c) {
        // 选课表还没建，studentCount 先返回 0
        return new CourseView(c.getId(), c.getName(), c.getDescription(), c.getTeacherId(),
                courseMapper.findUserName(c.getTeacherId()), c.getStatus(), modulesOf(c), countChapters(c.getId()), 0,
                c.getCreatedAt());
    }
}
