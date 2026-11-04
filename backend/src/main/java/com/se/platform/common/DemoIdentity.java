package com.se.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 演示身份（临时）：登录还没做，"当前学生"默认是 5 号（学生01）。
 * 请求头带 X-Demo-Student-Id 时改用那个学生，方便在浏览器里演示不同学生（如干预）。
 * 后端登录做好后，改成从登录信息里取当前用户，删掉这个类。
 */
public final class DemoIdentity {

    public static final long DEFAULT_STUDENT_ID = 5L;
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
