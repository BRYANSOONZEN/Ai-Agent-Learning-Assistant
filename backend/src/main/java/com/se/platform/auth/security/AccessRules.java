package com.se.platform.auth.security;

import java.util.List;
import java.util.Set;
import org.springframework.util.AntPathMatcher;

/**
 * 每个接口谁能调：一张表，从上往下找，第一条匹配的规则生效。
 *
 * <ul>
 *   <li>PUBLIC：不用登录（登录、注册）</li>
 *   <li>LOGGED_IN：登录了就行，不分角色</li>
 *   <li>其他：只有列出的角色能调，别的角色返回 403</li>
 * </ul>
 * 注意：/api/courses/** 也包括 /api/courses 本身。
 * 表里没有的接口一律拒绝（403），新加接口忘了写规则也不会被随便调用；
 * AuthSecurityTest 会检查每个接口都有规则。
 */
public final class AccessRules {

    public static final String PUBLIC = "PUBLIC";
    public static final String LOGGED_IN = "LOGGED_IN";
    public static final String STUDENT = "student";
    public static final String TEACHER = "teacher";
    public static final String ADMIN = "admin";

    /** 一条规则：路径模式（** 表示后面任意几层）和允许的角色 */
    public record Rule(String pattern, Set<String> allowed) {
    }

    private static Rule rule(String pattern, String... allowed) {
        return new Rule(pattern, Set.of(allowed));
    }

    /** 顺序有意义：更具体的写在前面 */
    public static final List<Rule> RULES = List.of(
            // 账号
            rule("/api/auth/login", PUBLIC),
            rule("/api/auth/register", PUBLIC),
            rule("/api/auth/**", LOGGED_IN),
            rule("/api/hello/**", LOGGED_IN),
            // 管理员：用户管理、AI 调用记录
            rule("/api/admin/**", ADMIN),
            rule("/api/ai/logs/**", ADMIN),
            // 学生：学、记、诊（自己）、画（自己）、荐、助手、自己的干预任务
            rule("/api/learn/**", STUDENT),
            rule("/api/dialogue/**", STUDENT),
            rule("/api/recommendations/**", STUDENT),
            rule("/api/assistant/**", STUDENT),
            rule("/api/diagnosis/me", STUDENT),
            rule("/api/profiles/me", STUDENT),
            rule("/api/interventions/mine", STUDENT),
            // 资料文件：老师预览、学生学习都要看
            rule("/api/materials/**", STUDENT, TEACHER),
            // 老师：建课、对话话题、全班诊断、单个学生的诊断和画像、预警和干预
            rule("/api/courses/**", TEACHER),
            rule("/api/chapters/**", TEACHER),
            rule("/api/chapter-items/**", TEACHER),
            rule("/api/chapter-kps/**", TEACHER),
            rule("/api/knowledge-points/**", TEACHER),
            rule("/api/exercises/**", TEACHER),
            rule("/api/teacher/**", TEACHER),
            rule("/api/diagnosis/**", TEACHER),
            rule("/api/profiles/**", TEACHER),
            rule("/api/interventions/**", TEACHER),
            rule("/api/class-analysis/**", TEACHER),
            rule("/api/class-issues/**", TEACHER),
            rule("/api/alerts/**", TEACHER));

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private AccessRules() {
    }

    /** 这个路径的规则；没有返回 null（= 拒绝） */
    public static Rule find(String path) {
        for (Rule r : RULES) {
            if (MATCHER.match(r.pattern(), path)) {
                return r;
            }
        }
        return null;
    }
}
