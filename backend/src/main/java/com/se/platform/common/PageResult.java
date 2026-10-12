package com.se.platform.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.function.Function;

/**
 * 分页返回格式：{"records": [...], "total": 123}，和前端约定一致（docs/api.md 1.2 节）。
 *
 * <p>为什么不直接返回 MyBatis-Plus 的 Page 对象？Page 里还有 current、pages、orders 等十几个字段，
 * 前端用不到，而且会把内部实现暴露成接口契约。只返回约定好的两个字段更清楚。
 *
 * <p>这里用 Java 的 record：一种只装数据的简写类，自动生成构造方法、getter（名字就是 records()、total()）、
 * equals、toString。Jackson 能直接把 record 转成 JSON。
 */
public record PageResult<T>(List<T> records, long total) {

    /** 从 MyBatis-Plus 的分页结果直接转换 */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 转换的同时把每条记录换个类型，例如把 Course（实体）转成 CourseVO（返回给前端的对象）。
     * 用法：PageResult.of(page, CourseVO::from)
     */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        return new PageResult<>(page.getRecords().stream().map(converter).toList(), page.getTotal());
    }
}
