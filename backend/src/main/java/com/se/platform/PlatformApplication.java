package com.se.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类：运行这个 main 方法，整个后端就启动了（内置 Tomcat 监听 8080 端口）。
 *
 * <p>{@code @SpringBootApplication} 做了三件事：
 * <ol>
 *   <li>把这个类标记为配置类</li>
 *   <li>开启自动配置：看到引入了 web 依赖就配 Tomcat，看到 H2 就配数据源……</li>
 *   <li>组件扫描：扫描 com.se.platform 包及子包下所有带 @RestController、@Service 等注解的类，
 *       创建对象交给 Spring 管理（这些对象就叫 Bean）</li>
 * </ol>
 * 所以所有代码都必须放在 com.se.platform 包下面，否则扫描不到。
 */
@SpringBootApplication
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
