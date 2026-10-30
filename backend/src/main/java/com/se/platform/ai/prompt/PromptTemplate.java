package com.se.platform.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

/**
 * 提示词模板：从 resources/prompts/ 读一个文件，把里面的 {{占位符}} 换成实际内容。
 *
 * <p>为什么提示词放文件而不写在 Java 里：提示词经常要改，放在单独文件里改起来方便，
 * 文件名带版本号（如 topic_draft_v1），调用日志里能看出用的是哪一版。
 */
public class PromptTemplate {

    /** 匹配 {{xxx}}：\\{\\{ 是字面的两个左花括号，(\\w+) 捕获中间的名字 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final String name;
    private final String text;

    private PromptTemplate(String name, String text) {
        this.name = name;
        this.text = text;
    }

    /** 读取 resources/prompts/{name}.md */
    public static PromptTemplate load(String name) {
        try (InputStream in = new ClassPathResource("prompts/" + name + ".md").getInputStream()) {
            return new PromptTemplate(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("找不到提示词模板 prompts/" + name + ".md", e);
        }
    }

    public String name() {
        return name;
    }

    /** 替换所有占位符；模板里有、但 values 里没给的占位符直接报错，防止漏传 */
    public String render(Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException("提示词模板 " + name + " 缺少占位符：" + key);
            }
            // quoteReplacement：内容里的 $ 和 \ 按普通字符处理，不当成正则的特殊符号
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
