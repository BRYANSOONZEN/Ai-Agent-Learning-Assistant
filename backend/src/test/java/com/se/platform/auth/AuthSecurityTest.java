package com.se.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.auth.security.AccessRules;
import com.se.platform.auth.security.JwtService;
import com.se.platform.auth.security.LoginAttempts;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 打开登录检查后的整套规则：令牌、角色、注册、锁定、禁用立即生效、每个接口都有权限规则。
 * 其他测试默认关掉登录检查（src/test/resources/config/application.yml），这里单独打开。
 */
@SpringBootTest(properties = {"platform.auth.enabled=true", "platform.auth.jwt-secret=test-secret-for-auth-security"})
@AutoConfigureMockMvc
class AuthSecurityTest {

    /** 种子数据的演示密码，见 data.sql 第 9 行 */
    private static final String DEMO = "demo123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LoginAttempts attempts;
    @Autowired
    private RequestMappingHandlerMapping mappings;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void reset() {
        for (String u : List.of("admin", "teacher01", "2023001", "2023002", "2023003")) {
            attempts.clear(u);
        }
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String token(String username) throws Exception {
        String body = login(username, DEMO).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("token").asText();
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(token == null ? req : req.header("Authorization", "Bearer " + token));
    }

    // ------------------------------------------------------------------ 令牌

    @Test
    void loginGivesATokenThatIdentifiesTheUser() throws Exception {
        String t = token("teacher01");
        call(get("/api/auth/me"), t)
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.role").value("teacher"));
    }

    @Test
    void missingBrokenOrTamperedTokenIs401() throws Exception {
        call(get("/api/learn/courses"), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
        call(get("/api/learn/courses"), "abc.def.ghi").andExpect(status().isUnauthorized());

        // 把学生令牌里的角色改成 teacher：签名对不上
        String[] parts = token("2023001").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"student\"", "\"teacher\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        call(get("/api/courses/mine"), forged).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401WithAClearMessage() throws Exception {
        String expired = new JwtService("test-secret-for-auth-security", 0).issue(2, "teacher", "李老师");
        call(get("/api/courses/mine"), expired).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登录已过期，请重新登录"));
    }

    // ------------------------------------------------------------------ 角色

    @Test
    void eachRoleOnlyReachesItsOwnEndpoints() throws Exception {
        String student = token("2023001");
        String teacher = token("teacher01");
        String admin = token("admin");

        call(get("/api/learn/courses"), student).andExpect(status().isOk());
        call(get("/api/courses/mine"), student).andExpect(status().isForbidden());
        call(get("/api/interventions/board"), student).andExpect(status().isForbidden());
        call(get("/api/admin/users"), student).andExpect(status().isForbidden());

        call(get("/api/courses/mine"), teacher).andExpect(status().isOk());
        call(get("/api/learn/courses"), teacher).andExpect(status().isForbidden());
        call(get("/api/ai/logs"), teacher).andExpect(status().isForbidden());
        call(get("/api/admin/users"), teacher).andExpect(status().isForbidden());

        call(get("/api/admin/users"), admin).andExpect(status().isOk());
        call(get("/api/ai/logs"), admin).andExpect(status().isOk());
        call(get("/api/courses/mine"), admin).andExpect(status().isForbidden());
    }

    @Test
    void currentUserComesFromTheToken() throws Exception {
        // 不同老师看到的是各自的课
        long li = json.readTree(call(get("/api/courses/mine"), token("teacher01")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("total").asLong();
        long wang = json.readTree(call(get("/api/courses/mine"), token("teacher02")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("total").asLong();
        assertThat(li).isNotEqualTo(wang);

        // 学生03 的诊断就是学生03 的，不能靠请求头冒充别人
        call(get("/api/diagnosis/me").header("X-Demo-Student-Id", "5"), token("2023003"))
                .andExpect(jsonPath("$.data.studentName").value("学生03"));
    }

    @Test
    void everyEndpointHasAnAccessRule() {
        List<String> missing = new ArrayList<>();
        mappings.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().forEach(p -> {
            String path = p.replaceAll("\\{[^}]+}", "1");
            if (path.startsWith("/api") && AccessRules.find(path) == null) {
                missing.add(p);
            }
        }));
        assertThat(missing).as("这些接口没有写权限规则").isEmpty();
    }

    // ------------------------------------------------------------------ 注册

    @Test
    @Transactional
    void studentsCanRegisterButNeverAsTeacher() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new_student\",\"password\":\"abc12345\",\"name\":\"新同学\",\"role\":\"teacher\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user.role").value("student"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String t = json.readTree(body).path("data").path("token").asText();
        call(get("/api/learn/courses"), t).andExpect(status().isOk());
        call(get("/api/courses/mine"), t).andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new_student\",\"password\":\"abc12345\",\"name\":\"重名\"}"))
                .andExpect(jsonPath("$.code").value(409));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weak_pw\",\"password\":\"12345678\",\"name\":\"弱密码\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码为 8～64 位，至少包含一个字母和一个数字"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"abc12345\",\"name\":\"太短\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ------------------------------------------------------------------ 锁定和禁用

    @Test
    @Transactional
    void fiveWrongPasswordsLockTheAccountUntilAdminResets() throws Exception {
        login("2023002", "wrong1").andExpect(jsonPath("$.message").value("用户名或密码错误"));
        login("2023002", "wrong2");
        login("2023002", "wrong3").andExpect(jsonPath("$.message").value("用户名或密码错误，还可以再试 2 次"));
        login("2023002", "wrong4").andExpect(jsonPath("$.message").value("用户名或密码错误，还可以再试 1 次"));
        login("2023002", "wrong5").andExpect(jsonPath("$.code").value(429));
        // 锁住后密码对了也不行
        login("2023002", DEMO).andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("密码连续错误 5 次，请 15 分钟后再试"));

        // 不存在的用户名也一样计数，提示一样，不暴露谁存在
        for (int i = 0; i < 2; i++) {
            login("ghost_user", "x");
        }
        login("ghost_user", "x").andExpect(jsonPath("$.message").value("用户名或密码错误，还可以再试 2 次"));
        attempts.clear("ghost_user");

        // 管理员重置密码后马上能登录
        call(put("/api/admin/users/6/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newpass123\"}"), token("admin")).andExpect(jsonPath("$.code").value(200));
        login("2023002", "newpass123").andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Transactional
    void disablingAnAccountCutsOffItsTokenImmediately() throws Exception {
        String student = token("2023003");
        call(get("/api/learn/courses"), student).andExpect(status().isOk());

        call(put("/api/admin/users/7/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"), token("admin")).andExpect(jsonPath("$.data.status").value(0));

        call(get("/api/learn/courses"), student).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
        login("2023003", DEMO).andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @Transactional
    void adminCreatesTeachersButCannotTouchAdmins() throws Exception {
        String admin = token("admin");
        String body = call(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"teacher09\",\"password\":\"teach1234\",\"name\":\"陈老师\",\"role\":\"teacher\"}"), admin)
                .andExpect(jsonPath("$.data.role").value("teacher"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode created = json.readTree(body).path("data");
        assertThat(created.path("status").asInt()).isEqualTo(1);
        login("teacher09", "teach1234").andExpect(jsonPath("$.data.user.role").value("teacher"));

        call(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"boss_two\",\"password\":\"boss12345\",\"name\":\"另一个管理员\",\"role\":\"admin\"}"), admin)
                .andExpect(jsonPath("$.code").value(400));
        call(put("/api/admin/users/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"), admin)
                .andExpect(jsonPath("$.code").value(403));
    }
}
