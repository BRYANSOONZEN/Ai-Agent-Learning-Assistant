package com.se.platform.course.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 返回给前端的课程。teacher、chapterCount、studentCount 不在表里，查询时算出来 */
public record CourseView(
        Long id,
        String name,
        String description,
        Long teacherId,
        String teacher,
        String status,
        List<String> modules,
        long chapterCount,
        long studentCount,
        LocalDateTime createdAt) {
}
