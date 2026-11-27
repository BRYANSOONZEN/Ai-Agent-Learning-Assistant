package com.se.platform.auth.controller;

import com.se.platform.auth.dto.AuthDtos.ChangePasswordRequest;
import com.se.platform.auth.dto.AuthDtos.LoginRequest;
import com.se.platform.auth.dto.AuthDtos.LoginResponse;
import com.se.platform.auth.dto.AuthDtos.RegisterRequest;
import com.se.platform.auth.dto.AuthDtos.UserView;
import com.se.platform.auth.service.AuthService;
import com.se.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 账号接口，前缀 /api/auth（docs/api.md 第 2 节）。登录和注册不用令牌，其他要登录 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /api/auth/login：返回令牌和用户信息 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** GET /api/auth/demo-accounts：演示切换开着时，可以一键登录的演示账号；关掉时为空 */
    @GetMapping("/demo-accounts")
    public Result<java.util.List<UserView>> demoAccounts() {
        return Result.ok(authService.demoAccounts());
    }

    /** POST /api/auth/demo-login {username}：演示切换开着时，直接登录列出的演示账号（不用密码） */
    @PostMapping("/demo-login")
    public Result<LoginResponse> demoLogin(@RequestBody java.util.Map<String, String> body) {
        return Result.ok(authService.demoLogin(body == null ? null : body.get("username")));
    }

    /** POST /api/auth/register：学生注册，成功后直接登录 */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /** GET /api/auth/me：当前登录的是谁（前端刷新页面时用来确认令牌还有效） */
    @GetMapping("/me")
    public Result<UserView> me() {
        return Result.ok(authService.me());
    }

    /** PUT /api/auth/password {oldPassword, newPassword}：改密码；其他设备上的旧令牌失效，返回新令牌 */
    @PutMapping("/password")
    public Result<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return Result.ok(authService.changePassword(request));
    }

    /** POST /api/auth/logout：退出登录，手里的令牌立即失效 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }
}
