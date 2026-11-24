package com.se.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.se.platform.auth.dto.AuthDtos.AdminUserView;
import com.se.platform.auth.dto.AuthDtos.CreateUserRequest;
import com.se.platform.auth.dto.AuthDtos.UserCounts;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.auth.security.LoginAttempts;
import com.se.platform.common.BusinessException;
import com.se.platform.common.CurrentUser;
import com.se.platform.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理员管理账号：列表、新建老师或学生、禁用和启用、重置密码。
 * 管理员账号不能在这里新建、禁用或重置，避免把自己或别的管理员锁在外面。
 */
@Service
public class AdminUserService {

    private final UserMapper userMapper;
    private final AuthService authService;
    private final LoginAttempts attempts;

    public AdminUserService(UserMapper userMapper, AuthService authService, LoginAttempts attempts) {
        this.userMapper = userMapper;
        this.authService = authService;
        this.attempts = attempts;
    }

    /** 分页列表：按角色筛选，按用户名或姓名搜索，新建的在前 */
    public PageResult<AdminUserView> page(int page, int size, String role, String keyword) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(role), User::getRole, role)
                .and(StringUtils.hasText(keyword), w -> w.like(User::getUsername, keyword.trim())
                        .or().like(User::getName, keyword.trim()))
                .orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)), q);
        return PageResult.of(result, this::view);
    }

    public UserCounts counts() {
        return new UserCounts(count("admin"), count("teacher"), count("student"),
                userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 0)));
    }

    public AdminUserView create(CreateUserRequest request) {
        String username = request.username().trim();
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw BusinessException.conflict("用户名「" + username + "」已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(authService.hash(request.password()));
        user.setName(request.name().trim());
        user.setRole(request.role());
        user.setStatus(User.STATUS_ACTIVE);
        userMapper.insert(user);
        return view(user);
    }

    public AdminUserView setStatus(long id, int status) {
        User user = requireEditable(id);
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == User.STATUS_ACTIVE) {
            attempts.clear(user.getUsername());
        }
        return view(user);
    }

    public void resetPassword(long id, String password) {
        User user = requireEditable(id);
        user.setPasswordHash(authService.hash(password));
        userMapper.updateById(user);
        attempts.clear(user.getUsername()); // 重置后马上能登录，不用等锁定到期
    }

    private User requireEditable(long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户");
        }
        CurrentUser.User me = CurrentUser.get();
        if (me != null && me.id() == id) {
            throw BusinessException.forbidden("不能修改自己的账号状态，改密码请用右上角的「修改密码」");
        }
        if ("admin".equals(user.getRole())) {
            throw BusinessException.forbidden("不能在这里修改管理员账号");
        }
        return user;
    }

    private long count(String role) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, role));
    }

    private AdminUserView view(User u) {
        return new AdminUserView(u.getId(), u.getUsername(), u.getName(), u.getRole(),
                u.getStatus() == null ? 1 : u.getStatus(), attempts.lockedMinutesLeft(u.getUsername()) > 0,
                u.getCreatedAt());
    }
}
