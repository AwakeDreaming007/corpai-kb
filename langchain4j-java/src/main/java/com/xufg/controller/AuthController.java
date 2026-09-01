package com.xufg.controller;

import com.xufg.common.Result;
import com.xufg.dto.LoginRequest;
import com.xufg.dto.LoginResponse;
import com.xufg.dto.RegisterRequest;
import com.xufg.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 认证服务。 */
    private final AuthService authService;

    /**
     * 注册新用户。
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /**
     * 登录并获取 JWT。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 查询当前登录用户和权限。
     */
    @GetMapping("/me")
    public Result<LoginResponse> me() {
        return Result.ok(authService.me());
    }
}
