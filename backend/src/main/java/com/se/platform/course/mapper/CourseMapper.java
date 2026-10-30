package com.se.platform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.se.platform.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 查教师姓名。user 表的实体类属于登录模块，还没写，这里先用一句 SQL 直接查。
     * {@code #{id}} 是 MyBatis 的参数占位符，会变成预编译的 ?，不会有 SQL 注入问题。
     */
    @Select("SELECT name FROM `user` WHERE id = #{id}")
    String findUserName(Long id);
}
