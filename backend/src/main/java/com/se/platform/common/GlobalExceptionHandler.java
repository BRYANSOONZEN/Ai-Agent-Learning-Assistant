package com.se.platform.common;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：任何 Controller 抛出的异常都会到这里，被转换成统一的 {code, message} 格式。
 * 这样 Controller 和 Service 里不需要到处写 try/catch。
 *
 * <p>{@code @RestControllerAdvice}：对所有 Controller 生效，返回值自动转成 JSON。
 * {@code @ExceptionHandler(X.class)}：抛出 X 类型（或它的子类）的异常时，调用这个方法。
 * Spring 会选择「最匹配」的那个方法，所以最后的 Exception.class 只兜底处理没预料到的异常。
 *
 * <p>按 docs/api.md 1.3 节的约定，这些错误的 HTTP 状态码都是 200，错误信息放在 code 和 message 里，
 * 前端 request.js 才能读到 message 并弹出提示。只有 401（未登录）用 HTTP 状态码，登录功能里再处理。
 *
 * <p>{@code @Slf4j}（Lombok）：自动生成一个 log 对象，用来写日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 我们自己抛的业务异常：直接用它的 code 和 message */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：例如 @NotBlank 的字段传了空字符串。把所有错误信息拼成一句话 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return Result.fail(400, message);
    }

    /** 缺少必填的查询参数，例如 keyword 是必填却没传 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(400, "缺少参数：" + e.getParameterName());
    }

    /** 参数类型不对，例如 /api/courses/abc，id 应该是数字。不把 Java 内部的报错原文返回给用户 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(400, "参数格式不正确：" + e.getName());
    }

    /** 请求体不是合法的 JSON */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBadBody(HttpMessageNotReadableException e) {
        return Result.fail(400, "请求体格式错误");
    }

    /** 路径不存在，例如访问了 /api/xxx */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(404, "接口不存在：/" + e.getResourcePath());
    }

    /** HTTP 方法不对，例如接口只支持 GET，却用了 POST */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return Result.fail(405, "不支持的请求方法：" + e.getMethod());
    }

    /**
     * 兜底：其他所有没预料到的异常（通常是代码 bug）。
     * 完整错误堆栈只写进日志，前端只显示「服务器错误」，不把内部细节暴露给用户。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("未处理的异常", e);
        return Result.fail(500, "服务器错误，请稍后重试");
    }
}
