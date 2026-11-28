package com.se.platform.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.auth.security.LoginAttempts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 登录接口：正常登录、密码错误、用户不存在，以及禁用账号和空参数 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    /** 种子数据的演示密码，见 data.sql 第 9 行 */
    private static final String DEMO_PASSWORD = "demo123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LoginAttempts attempts;

    /** 输错次数记在内存里、各测试共用，每个测试前清零 */
    @BeforeEach
    void resetAttempts() {
        attempts.clear("teacher01");
        attempts.clear("nobody");
    }

    private ResultActions login(String username, String password) throws Exception {
        String body = """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void rightPasswordReturnsUserWithoutHash() throws Exception {
        login("teacher01", DEMO_PASSWORD)
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user.id").value(2))
                .andExpect(jsonPath("$.data.user.name").value("李老师"))
                .andExpect(jsonPath("$.data.user.role").value("teacher"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(24 * 3600));

        login("2023001", DEMO_PASSWORD).andExpect(jsonPath("$.data.user.role").value("student"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameMessage() throws Exception {
        login("teacher01", DEMO_PASSWORD + "x")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());

        login("nobody", DEMO_PASSWORD)
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @Transactional // 测试结束后回滚，这里改的账号状态不影响别的测试
    void disabledAccountIsRefusedOnlyAfterTheRightPassword() throws Exception {
        userMapper.update(new LambdaUpdateWrapper<User>()
                .eq(User::getUsername, "teacher01")
                .set(User::getStatus, 0));

        login("teacher01", DEMO_PASSWORD).andExpect(jsonPath("$.code").value(403));
        // 密码错时不透露账号被禁用
        login("teacher01", DEMO_PASSWORD + "x").andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void blankFieldsAreRejected() throws Exception {
        login("", DEMO_PASSWORD)
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));
        login("teacher01", "")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }
}
