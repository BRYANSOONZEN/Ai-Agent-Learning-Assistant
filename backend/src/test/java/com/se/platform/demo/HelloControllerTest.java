package com.se.platform.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 接口测试示例：验证骨架的统一返回格式和全局异常处理。
 *
 * <p>{@code @SpringBootTest}：启动完整的 Spring 应用（包括 H2 数据库），和真实运行时一样。
 * {@code @AutoConfigureMockMvc}：提供 MockMvc，可以在不启动真实服务器的情况下模拟发 HTTP 请求。
 *
 * <p>运行方式：IDEA 里点类名左边的绿色三角；或命令行在 backend 目录执行 mvnw test。
 */
@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hello_returnsUnifiedResult() throws Exception {
        mockMvc.perform(get("/api/hello").param("name", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").value("hello, 张三"));
    }

    @Test
    void businessException_becomesCode404() throws Exception {
        mockMvc.perform(get("/api/hello/courses/99"))
                .andExpect(status().isOk())   // 约定：业务错误的 HTTP 状态码仍是 200
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    @Test
    void wrongParamType_becomesCode400() throws Exception {
        mockMvc.perform(get("/api/hello/courses/abc"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数格式不正确：id"));
    }

    @Test
    void unknownPath_becomesCode404() throws Exception {
        mockMvc.perform(get("/api/not-exist"))
                .andExpect(jsonPath("$.code").value(404));
    }
}
