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

    /** PUT /api/auth/password：修改自己的密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.ok();
    }
}
