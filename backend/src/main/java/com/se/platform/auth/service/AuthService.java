package com.se.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.se.platform.auth.dto.AuthDtos.ChangePasswordRequest;
import com.se.platform.auth.dto.AuthDtos.LoginRequest;
import com.se.platform.auth.dto.AuthDtos.LoginResponse;
import com.se.platform.auth.dto.AuthDtos.RegisterRequest;
import com.se.platform.auth.dto.AuthDtos.UserView;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.auth.security.JwtService;
import com.se.platform.auth.security.LoginAttempts;
import com.se.platform.common.BusinessException;
import com.se.platform.common.CurrentUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录、注册、改密码。
 *
 * <p>登录的检查顺序：是否锁定 → 用户名和密码 → 账号状态 → 发令牌。
 */
@Service
public class AuthService {

    /** 用户不存在和密码错误用同一句话，别人就没法靠提示猜出哪些账号存在 */
    public static final String BAD_CREDENTIALS = "用户名或密码错误";
    /** 还剩几次时开始提醒 */
    static final int WARN_WHEN_LEFT = 2;

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final LoginAttempts attempts;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, JwtService jwtService, LoginAttempts attempts) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.attempts = attempts;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        long locked = attempts.lockedMinutesLeft(username);
        if (locked > 0) {
            throw new BusinessException(429, "密码连续错误 " + attempts.maxFailures() + " 次，请 " + locked + " 分钟后再试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int left = attempts.fail(username);
            if (left == 0) {
                throw new BusinessException(429, "密码连续错误 " + attempts.maxFailures() + " 次，请 "
                        + attempts.lockMinutes() + " 分钟后再试");
            }
            throw BusinessException.badRequest(left <= WARN_WHEN_LEFT
                    ? BAD_CREDENTIALS + "，还可以再试 " + left + " 次" : BAD_CREDENTIALS);
        }
        // 放在密码比对之后：只有密码正确的人，才能知道这个账号被禁用了
        if (user.getStatus() != User.STATUS_ACTIVE) {
            throw BusinessException.forbidden("账号已被禁用，请联系管理员");
        }
        attempts.success(username);
        return issue(user);
    }

    /** 学生自己注册：角色固定为学生，注册成功直接登录 */
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists > 0) {
            throw BusinessException.conflict("用户名「" + username + "」已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setRole("student");
        user.setStatus(User.STATUS_ACTIVE);
        userMapper.insert(user);
        return issue(user);
    }

    public UserView me() {
        return view(requireCurrent());
    }

    public void changePassword(ChangePasswordRequest request) {
        User user = requireCurrent();
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("原密码不对");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw BusinessException.badRequest("新密码不能和原密码一样");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private User requireCurrent() {
        CurrentUser.User current = CurrentUser.get();
        User user = current == null ? null : userMapper.selectById(current.id());
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        return user;
    }

    private LoginResponse issue(User user) {
        String token = jwtService.issue(user.getId(), user.getRole(), user.getName());
        return new LoginResponse(token, jwtService.validSeconds(), view(user));
    }

    public static UserView view(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getName(), user.getRole());
    }
}
