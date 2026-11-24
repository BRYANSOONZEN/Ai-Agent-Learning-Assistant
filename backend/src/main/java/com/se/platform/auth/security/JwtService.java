package com.se.platform.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 登录令牌（JWT，HS256）：用 JDK 自带的 HMAC-SHA256 签名，不引入新依赖。
 *
 * <p>令牌 = 头.内容.签名，三段都是 Base64URL。内容里放用户 id、角色、姓名、签发时间、过期时间。
 * 签名用只有服务器知道的密钥算出来，别人改了内容（例如把 student 改成 teacher），签名就对不上。
 *
 * <p>密钥：配置 platform.auth.jwt-secret（或环境变量 JWT_SECRET）。没配就每次启动随机生成一个，
 * 重启后旧令牌全部失效——开发阶段数据库也是每次启动重建的，正好一致。
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
            .getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final long validSeconds;
    private final ObjectMapper json = new ObjectMapper();

    public JwtService(@Value("${platform.auth.jwt-secret:}") String configuredSecret,
                      @Value("${platform.auth.token-hours:24}") long tokenHours) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("没有配置 platform.auth.jwt-secret，本次启动随机生成密钥；重启后需要重新登录");
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.validSeconds = tokenHours * 3600;
    }

    public long validSeconds() {
        return validSeconds;
    }

    /** 令牌里带的信息 */
    public record Claims(long userId, String role, String name, long expiresAt) {
    }

    public String issue(long userId, String role, String name) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", String.valueOf(userId));
        body.put("role", role);
        body.put("name", name);
        body.put("iat", now);
        body.put("exp", now + validSeconds);
        try {
            String payload = B64.encodeToString(json.writeValueAsBytes(body));
            String unsigned = HEADER + "." + payload;
            return unsigned + "." + B64.encodeToString(sign(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("生成令牌失败", e);
        }
    }

    /** 验证签名和过期时间。不合法返回 null；过期抛 {@link ExpiredException} */
    public Claims verify(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            return null;
        }
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            // 用固定时间的比较，别人没法靠响应快慢一点点猜出签名
            if (!MessageDigest.isEqual(expected, B64D.decode(parts[2]))) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = json.readValue(B64D.decode(parts[1]), Map.class);
            long exp = ((Number) body.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new ExpiredException();
            }
            return new Claims(Long.parseLong((String) body.get("sub")), (String) body.get("role"),
                    (String) body.get("name"), exp);
        } catch (ExpiredException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /** 令牌过期 */
    public static class ExpiredException extends RuntimeException {
        public ExpiredException() {
            super("登录已过期，请重新登录");
        }
    }
}
