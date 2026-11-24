package com.se.platform.common.config;

import com.se.platform.auth.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把登录检查挂到所有 /api 接口上。
 * platform.auth.enabled=false 时不挂（只给单元测试用，见 src/test/resources/config/application.yml），
 * 这时当前用户退回演示身份（见 CurrentUser）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final boolean enabled;

    public WebConfig(AuthInterceptor authInterceptor, @Value("${platform.auth.enabled:true}") boolean enabled) {
        this.authInterceptor = authInterceptor;
        this.enabled = enabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (enabled) {
            registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
        }
    }
}
