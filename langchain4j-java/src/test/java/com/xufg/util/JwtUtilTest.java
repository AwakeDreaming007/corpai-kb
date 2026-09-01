package com.xufg.util;

import com.xufg.config.JwtProperties;
import com.xufg.common.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWT 工具单元测试。
 */
class JwtUtilTest {

    /** 满足 HS256 最低长度要求的测试密钥。 */
    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";

    /**
     * 校验令牌签发后可以解析，且关键 claims 内容正确。
     */
    @Test
    void shouldGenerateAndParseTokenRoundtrip() {
        JwtUtil jwtUtil = createJwtUtil(720);
        List<String> authorities = List.of("USER", "kb:create");

        String token = jwtUtil.generateToken(1001L, "tester", "测试用户", authorities);
        Claims claims = jwtUtil.parseToken(token);

        assertEquals(1001L, ((Number) claims.get("uid")).longValue());
        assertEquals("tester", claims.get("username", String.class));
        assertEquals("测试用户", claims.get("nickname", String.class));
        assertEquals(authorities, claims.get("authorities", List.class));
    }

    /**
     * 校验短有效期令牌过期后无法解析。
     */
    @Test
    void shouldThrowExceptionWhenTokenExpired() {
        JwtUtil jwtUtil = createJwtUtil(-1);
        String token = jwtUtil.generateToken(1001L, "tester", "测试用户", List.of("USER"));

        assertThrows(JwtException.class, () -> jwtUtil.parseToken(token));
    }

    /**
     * 校验被篡改的令牌无法通过签名验证。
     */
    @Test
    void shouldThrowExceptionWhenTokenTampered() {
        JwtUtil jwtUtil = createJwtUtil(720);
        String token = jwtUtil.generateToken(1001L, "tester", "测试用户", List.of("USER"));
        String[] tokenParts = token.split("\\.");
        String tamperedToken = tokenParts[0] + "." + tokenParts[1] + "."
                + "x".repeat(tokenParts[2].length());

        assertThrows(JwtException.class, () -> jwtUtil.parseToken(tamperedToken));
    }

    /**
     * 校验过短密钥在签发阶段被明确拒绝。
     */
    @Test
    void shouldThrowExceptionWhenSecretTooShort() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("short-secret");
        properties.setExpireMinutes(720);
        JwtUtil jwtUtil = new JwtUtil(properties);

        BizException exception = assertThrows(BizException.class,
                () -> jwtUtil.generateToken(1001L, "tester", "测试用户", List.of("USER")));
        assertEquals("KB_JWT_SECRET 未配置或长度不足", exception.getMessage());
    }

    /**
     * 创建被测 JWT 工具。
     */
    private JwtUtil createJwtUtil(Integer expireMinutes) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        properties.setExpireMinutes(expireMinutes);
        return new JwtUtil(properties);
    }
}
