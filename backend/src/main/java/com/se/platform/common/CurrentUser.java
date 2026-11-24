package com.se.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前登录的用户：由 AuthInterceptor 验证令牌后放进本次请求里，业务代码只从这里取。
 *
 * <p>只有关掉登录检查时（platform.auth.enabled=false，单元测试用）请求里才没有用户，
 * 这时退回到演示身份：老师 2 号、学生 5 号，学生可以用请求头 X-Demo-Student-Id 切换（见 {@link DemoIdentity}）。
 */
public final class CurrentUser {

    public static final String ATTRIBUTE = "currentUser";

    /** 令牌验证通过后的用户 */
    public record User(long id, String role, String name) {
    }

    private CurrentUser() {
    }

    /** 本次请求的登录用户；没有登录检查（测试）或不在请求里时返回 null */
    public static User get() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            return req.getAttribute(ATTRIBUTE) instanceof User u ? u : null;
        }
        return null;
    }

    public static long studentId() {
        User u = get();
        return u != null ? u.id() : DemoIdentity.studentId();
    }

    public static long teacherId() {
        User u = get();
        return u != null ? u.id() : DemoIdentity.DEFAULT_TEACHER_ID;
    }
}
