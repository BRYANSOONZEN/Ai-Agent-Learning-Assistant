package com.se.platform.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 登录、注册、用户管理接口的请求和响应（docs/api.md 第 2 节） */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 用户名：4～32 位，字母、数字、下划线 */
    public static final String USERNAME_REGEX = "^[A-Za-z0-9_]{4,32}$";
    public static final String USERNAME_MESSAGE = "用户名为 4～32 位字母、数字或下划线";
    /** 密码：8～64 位，至少有一个字母和一个数字 */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$";
    public static final String PASSWORD_MESSAGE = "密码为 8～64 位，至少包含一个字母和一个数字";

    /** 前端发来的用户名和密码 */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 学生自己注册：没有角色字段，一律注册成学生 */
    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空") @Pattern(regexp = USERNAME_REGEX, message = USERNAME_MESSAGE)
            String username,
            @NotBlank(message = "密码不能为空") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,
            @NotBlank(message = "姓名不能为空") @Size(max = 32, message = "姓名不能超过 32 个字")
            String name) {
    }

    /** 修改自己的密码 */
    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String newPassword) {
    }

    /** 返回给前端的用户信息：没有 passwordHash 字段，所以不可能把哈希带出去 */
    public record UserView(Long id, String username, String name, String role) {
    }

    /** 登录结果：令牌 + 用户信息；expiresIn 是令牌还有几秒过期 */
    public record LoginResponse(String token, long expiresIn, UserView user) {
    }

    // ---------------------------------------------------------------- 管理员

    /** 管理员新建账号：只能建老师和学生，管理员账号不开放新建 */
    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空") @Pattern(regexp = USERNAME_REGEX, message = USERNAME_MESSAGE)
            String username,
            @NotBlank(message = "密码不能为空") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,
            @NotBlank(message = "姓名不能为空") @Size(max = 32, message = "姓名不能超过 32 个字")
            String name,
            @NotBlank(message = "请选择角色") @Pattern(regexp = "teacher|student", message = "角色只能是老师或学生")
            String role) {
    }

    public record StatusRequest(
            @NotNull(message = "请选择状态") @Min(value = 0, message = "状态只能是 1（正常）或 0（禁用）")
            @Max(value = 1, message = "状态只能是 1（正常）或 0（禁用）") Integer status) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "请输入新密码") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password) {
    }

    /** 用户管理列表里的一行 */
    public record AdminUserView(Long id, String username, String name, String role, int status,
                                boolean locked, LocalDateTime createdAt) {
    }

    /** 各角色人数 */
    public record UserCounts(long admin, long teacher, long student, long disabled) {
    }
}
