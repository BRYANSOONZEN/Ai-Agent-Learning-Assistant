package com.se.platform.common.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 日期格式：按 docs/api.md 的约定，时间输出成 "2026-10-12 14:30:00"，日期输出成 "2026-10-12"。
 * 不配置的话，Jackson 默认输出 "2026-10-12T14:30:00"（中间有个 T），前端显示不友好。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateFormatCustomizer() {
        // Lambda：builder -> {...} 是「拿到 builder 后执行这些配置」的简写
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(DATE_TIME), new LocalDateSerializer(DATE))
                .deserializers(new LocalDateTimeDeserializer(DATE_TIME), new LocalDateDeserializer(DATE));
    }
}
