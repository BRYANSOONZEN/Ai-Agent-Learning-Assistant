package com.se.platform.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 部署时关掉演示切换（platform.demo.switch-enabled=false）：列表为空，演示登录一律拒绝 */
@SpringBootTest(properties = {"platform.auth.enabled=true", "platform.demo.switch-enabled=false"})
@AutoConfigureMockMvc
class DemoSwitchOffTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void switchOff_noAccountsAndLoginRejected() throws Exception {
        mvc.perform(get("/api/auth/demo-accounts")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(post("/api/auth/demo-login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }
}
