package com.se.platform.ai.client;

/**
 * 发给模型的一条消息。
 *
 * <p>{@code record}（Java 16+）：只用来装数据的类。写一行，编译器自动生成构造方法、
 * 读取方法 {@code role()} / {@code content()}、equals 和 toString。字段创建后不能修改。
 *
 * @param role    system（规则）/ user（用户说的话）/ assistant（模型之前的回复）
 * @param content 文字内容
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}
