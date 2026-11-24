package com.se.platform.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 防止一直猜密码：同一个用户名连续输错 5 次，锁 15 分钟；登录成功就清零。
 *
 * <p>按用户名计数，不管这个用户名存不存在都一样计数、一样上锁，
 * 所以"还能试几次""已锁定"这些提示也不会暴露哪些用户名是真的。
 * 计数放在内存里，重启后清零（开发阶段够用；多台服务器时要换成 Redis 之类的共享存储）。
 */
@Component
public class LoginAttempts {

    private final int maxFailures;
    private final Duration lockFor;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemDefaultZone();

    private static final class Entry {
        int failures;
        Instant lockedUntil;
    }

    public LoginAttempts(@Value("${platform.auth.max-failures:5}") int maxFailures,
                         @Value("${platform.auth.lock-minutes:15}") long lockMinutes) {
        this.maxFailures = maxFailures;
        this.lockFor = Duration.ofMinutes(lockMinutes);
    }

    public int maxFailures() {
        return maxFailures;
    }

    public long lockMinutes() {
        return lockFor.toMinutes();
    }

    /** 还在锁定中就返回剩余分钟数（向上取整），否则返回 0 */
    public synchronized long lockedMinutesLeft(String username) {
        Entry e = entries.get(key(username));
        if (e == null || e.lockedUntil == null) {
            return 0;
        }
        Duration left = Duration.between(clock.instant(), e.lockedUntil);
        if (left.isNegative() || left.isZero()) {
            entries.remove(key(username)); // 锁到期，重新开始计数
            return 0;
        }
        return (left.toSeconds() + 59) / 60;
    }

    /** 记一次失败，返回还能再试几次（0 = 这次之后被锁了） */
    public synchronized int fail(String username) {
        Entry e = entries.computeIfAbsent(key(username), k -> new Entry());
        e.failures++;
        if (e.failures >= maxFailures) {
            e.lockedUntil = clock.instant().plus(lockFor);
            return 0;
        }
        return maxFailures - e.failures;
    }

    public synchronized void success(String username) {
        entries.remove(key(username));
    }

    /** 管理员重置密码或启用账号时解锁 */
    public synchronized void clear(String username) {
        entries.remove(key(username));
    }

    /** 测试用：换一个时钟，就能不等 15 分钟测解锁 */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
