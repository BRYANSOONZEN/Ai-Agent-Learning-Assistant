package com.se.platform.ai.client;

/**
 * 调模型失败：没配 key、超时、HTTP 报错、返回格式不对。
 *
 * <p>继承 RuntimeException（非受检异常）：调用方不必每次都写 try-catch，
 * 需要兜底的地方（比如对话里改用预写提示）再专门捕获。
 */
public class AiCallException extends RuntimeException {

    public AiCallException(String message) {
        super(message);
    }

    public AiCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
