package com.se.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 演示身份：只在关掉登录检查时用（platform.auth.enabled=false，单元测试默认关掉）。
 * 正常运行时当前用户来自令牌，见 {@link CurrentUser}。
 * 测试里"当前学生"默认是 5 号（学生01），请求头带 X-Demo-Student-Id 时改用那个学生；"当前老师"是 2 号（李老师）。
 */
public final class DemoIdentity {

    public static final long DEFAULT_STUDENT_ID = 5L;
    public static final long DEFAULT_TEACHER_ID = 2L;
    public static final String HEADER = "X-Demo-Student-Id";

    private DemoIdentity() {
    }

    public static long studentId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            String value = req.getHeader(HEADER);
            if (value != null && value.matches("[0-9]{1,9}")) {
                return Long.parseLong(value);
            }
        }
        return DEFAULT_STUDENT_ID;
    }
}
