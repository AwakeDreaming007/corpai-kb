package com.xufg.util;

import com.xufg.common.BizException;
import com.xufg.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 签发与解析工具。
 */
@Component
public class JwtUtil {

    /** JWT 配置属性。 */
    private final JwtProperties jwtProperties;

    /**
     * 注入 JWT 配置。
     */
    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 签发 JWT， claims 中携带用户身份和权限。
     */
    public String generateToken(Long userId, String username, String nickname, List<String> authorities) {
        SecretKey signingKey = signingKey();
        long expirationMillis = jwtProperties.getExpireMinutes() * 60_000L;
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("username", username);
        claims.put("nickname", nickname);
        claims.put("authorities", authorities);

        return Jwts.builder()
                .claims().add(claims).and()
                .subject(username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 JWT， 过期或非法令牌抛出 JwtException。
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtException("无效或已过期的令牌", exception);
        }
    }

    /**
     * 根据配置中的 secret 派生 HS256 签名密钥。
     */
    private SecretKey signingKey() {
        String secret = jwtProperties.getSecret();
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new BizException(401, "KB_JWT_SECRET 未配置或长度不足");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }
}
