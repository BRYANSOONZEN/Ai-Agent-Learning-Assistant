package com.se.platform.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.se.platform.auth.entity.User;
import com.se.platform.auth.mapper.UserMapper;
import com.se.platform.auth.security.AccessRules.Rule;
import com.se.platform.common.CurrentUser;
import com.se.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 每个 /api 请求进 Controller 之前先过这里：
 * <ol>
 *   <li>查权限表（{@link AccessRules}）：公开接口直接放行；表里没有的接口拒绝（403）</li>
 *   <li>取令牌：请求头 Authorization: Bearer xxx（资料文件可以用 ?token=xxx，因为 video 标签没法带请求头）</li>
 *   <li>验证签名和过期时间：不对或过期返回 401，前端回到登录页</li>
 *   <li>再查一次数据库：账号被禁用或删掉了，旧令牌马上失效（401）</li>
 *   <li>角色不在规则里：403</li>
 *   <li>通过后把用户放进请求（{@link CurrentUser}），业务代码只从这里取当前用户</li>
 * </ol>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final ObjectMapper json = new ObjectMapper();

    public AuthInterceptor(JwtService jwtService, UserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(req.getMethod())) {
            return true;
        }
        String path = req.getRequestURI().substring(req.getContextPath().length());
        Rule rule = AccessRules.find(path);
        if (rule == null) {
            return deny(resp, 403, "没有权限访问这个接口");
        }
        if (rule.allowed().contains(AccessRules.PUBLIC)) {
            return true;
        }

        JwtService.Claims claims;
        try {
            claims = jwtService.verify(token(req, path));
        } catch (JwtService.ExpiredException e) {
            return deny(resp, 401, e.getMessage());
        }
        if (claims == null) {
            return deny(resp, 401, "请先登录");
        }
        User user = userMapper.selectById(claims.userId());
        if (user == null) {
            return deny(resp, 401, "账号不存在，请重新登录");
        }
        if (user.getStatus() == null || user.getStatus() != User.STATUS_ACTIVE) {
            return deny(resp, 401, "账号已被禁用");
        }
        // 角色以数据库为准（令牌签发后角色不会变，这里是双保险）
        if (!rule.allowed().contains(AccessRules.LOGGED_IN) && !rule.allowed().contains(user.getRole())) {
            return deny(resp, 403, "没有权限访问这个接口");
        }
        req.setAttribute(CurrentUser.ATTRIBUTE, new CurrentUser.User(user.getId(), user.getRole(), user.getName()));
        return true;
    }

    private static String token(HttpServletRequest req, String path) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        if (path.startsWith("/api/materials/")) {
            return req.getParameter("token");
        }
        return null;
    }

    private boolean deny(HttpServletResponse resp, int status, String message) throws Exception {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getOutputStream().write(json.writeValueAsString(Result.fail(status, message))
                .getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
