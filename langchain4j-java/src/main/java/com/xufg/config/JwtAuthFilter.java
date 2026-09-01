package com.xufg.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufg.common.Result;
import com.xufg.common.UserContext;
import com.xufg.entity.SysUser;
import com.xufg.mapper.SysUserMapper;
import com.xufg.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * JWT 认证过滤器。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Authorization 请求头名称。 */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 令牌前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** JWT 工具。 */
    private final JwtUtil jwtUtil;

    /** 用户 Mapper， 用于实时校验用户状态。 */
    private final SysUserMapper sysUserMapper;

    /** JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /**
     * 解析令牌、映射权限并写入安全上下文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (!HttpMethod.OPTIONS.matches(request.getMethod())) {
                // 仅对 permitAll 的登录/注册路径跳过令牌解析（过期 token 不应拦住重新登录）；
                // 注意不能用 /api/auth/ 前缀整体跳过，否则 /api/auth/me 拿不到认证上下文永远 401
                String uri = request.getRequestURI();
                if ("/api/auth/login".equals(uri) || "/api/auth/register".equals(uri)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String token = extractToken(request);
                if (StringUtils.hasText(token)) {
                    Claims claims = parseToken(token, response);
                    if (claims == null) {
                        return;
                    }
                    Long userId = ((Number) claims.get("uid")).longValue();
                    String username = claims.get("username", String.class);
                    if (!isUserEnabled(userId, response)) {
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    List<String> authorities = (List<String>) claims.get("authorities", List.class);
                    List<SimpleGrantedAuthority> grantedAuthorities = authorities == null ? List.of()
                            : authorities.stream().filter(Objects::nonNull).map(SimpleGrantedAuthority::new).toList();
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            username, null, grantedAuthorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    UserContext.set(userId, username);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 提取 Bearer Token。
     */
    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 解析令牌， 解析失败时直接写出 401 响应。
     */
    private Claims parseToken(String token, HttpServletResponse response) throws IOException {
        try {
            return jwtUtil.parseToken(token);
        } catch (JwtException exception) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return null;
        } catch (RuntimeException exception) {
            writeUnauthorized(response, "登录状态异常，请重新登录");
            return null;
        }
    }

    /**
     * 校验用户是否存在且已启用。
     */
    private boolean isUserEnabled(Long userId, HttpServletResponse response) throws IOException {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            writeUnauthorized(response, "账号已被禁用或不存在");
            return false;
        }
        return true;
    }

    /**
     * 直接输出统一格式的 401 JSON 响应。
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, message)));
    }
}
