package com.se.platform.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回格式：所有接口都返回 {"code": 200, "message": "ok", "data": ...}
 * 前端 src/api/request.js 依赖这个格式：code == 200 就取出 data，否则弹出 message。
 *
 * <p>{@code <T>} 是泛型：data 的类型在使用时才确定。
 * 例如 {@code Result<Course>} 的 data 是一门课，{@code Result<PageResult<Course>>} 的 data 是一页课程。
 *
 * <p>Lombok 注解：
 * {@code @Data} 生成 getter/setter/toString；
 * {@code @NoArgsConstructor} / {@code @AllArgsConstructor} 生成无参 / 全参构造方法。
 * Jackson 正是通过 getter 把对象转成 JSON 的，没有 getter 返回的就是 {}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    /** 成功并返回数据。static 方法不用 new 就能调用：Result.ok(course) */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "ok", data);
    }

    /** 成功但没有数据，例如删除接口 */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /** 失败。错误码的含义见 docs/api.md 1.3 节 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
