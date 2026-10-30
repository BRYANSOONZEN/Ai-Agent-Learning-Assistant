package com.se.platform.ai.config;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型配置：把 application.yml 里 {@code llm:} 下面的值读进来，其他类注入这个对象就能用。
 *
 * <p>{@code @ConfigurationProperties(prefix = "llm")}：Spring 启动时按字段名去 yml 里找 llm.xxx 的值，
 * 调用 setter 赋进来。yml 里的短横线写法会自动对应驼峰字段，比如 {@code base-url} → {@code baseUrl}。
 * 类型也会自动转换：yml 里写 5000，赋给 int 字段就是数字 5000。
 *
 * <p>{@code @Component}：让 Spring 创建这个对象并管理它，别的类写
 * {@code private final LlmProperties llmProperties;}（配合构造器注入）就能拿到。
 *
 * <p>{@code @Data}（Lombok）：编译时自动生成 getter、setter、toString 等方法，不用手写。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 接口地址，不带 /chat/completions，例如 https://api.deepseek.com */
    private String baseUrl;

    /** 模型名，例如 deepseek-flash */
    private String model;

    /** 备用模型：主模型重试后仍然 503 / 超时才用；空 = 不用备用 */
    private String fallbackModel;

    /**
     * API key，只从环境变量 LLM_API_KEY 读。
     *
     * <p>{@code @ToString.Exclude}：@Data 生成的 toString() 默认会打印所有字段，
     * 一旦有人 log 这个对象，key 就会出现在日志里。加上这个注解，toString() 就跳过它。
     */
    @ToString.Exclude
    private String apiKey;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5000;

    /** 读超时（毫秒）：连上之后等模型回复的最长时间 */
    private int readTimeoutMs = 15000;

    /** 超时或 5xx 时最多重试几次 */
    private int maxRetries = 1;

    /**
     * 输入单价（元 / 百万 token）。
     * 用 BigDecimal 而不是 double：double 是二进制小数，0.1 + 0.2 会算成 0.30000000000000004，
     * 和钱有关的计算一律用 BigDecimal。
     */
    private BigDecimal priceInputPerMillion = BigDecimal.ZERO;

    /** 输出单价（元 / 百万 token） */
    private BigDecimal priceOutputPerMillion = BigDecimal.ZERO;

    /** 原样合并进请求体的额外参数，例如千问的 enable_thinking: false。默认空，不加任何参数。 */
    private Map<String, Object> extraBody = new LinkedHashMap<>();

    /** key 是否已配置（没设环境变量时 yml 给的是空字符串） */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 启动检查：打印当前用的模型；没设 key 时只给出警告，不抛异常，
     * 这样没有 key 的组员和测试也能正常启动，其他页面照常使用。
     *
     * <p>{@code @PostConstruct}：Spring 创建好这个对象、把 yml 的值都赋进来之后，自动调用一次。
     * 注意这里只打印"有没有配置"，绝不打印 key 本身。
     */
    @PostConstruct
    void logOnStartup() {
        log.info("大模型配置：model={}，备用={}，base-url={}", model,
                fallbackModel == null || fallbackModel.isBlank() ? "无" : fallbackModel, baseUrl);
        if (!isApiKeyConfigured()) {
            log.warn("没有配置 API key（application-local.yml 的 llm.api-key 或环境变量 LLM_API_KEY）："
                    + "AI 功能暂时不可用，其他功能不受影响。");
        }
    }
}
