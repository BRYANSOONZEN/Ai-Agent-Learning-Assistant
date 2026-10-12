package com.se.platform.common;

import lombok.Getter;

/**
 * 业务异常：业务规则不满足时抛出，例如「课程不存在」「章节下还有知识点，不能删除」。
 *
 * <p>继承 RuntimeException（运行时异常），所以调用方不用写 try/catch，
 * 异常会一路向上抛，最后被 {@link GlobalExceptionHandler} 接住，转成 {code, message} 返回给前端。
 *
 * <p>用法：
 * <pre>
 *   throw new BusinessException(404, "课程不存在");
 *   throw BusinessException.notFound("课程");
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码，取值见 docs/api.md 1.3 节：400 / 403 / 404 / 409 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(404, what + "不存在");
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
