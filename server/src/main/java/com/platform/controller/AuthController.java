package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.*;
import com.platform.security.JwtTokenProvider;
import com.platform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtProvider;

    public AuthController(AuthService authService, JwtTokenProvider jwtProvider) {
        this.authService = authService;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/wx-login")
    public Result<?> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        return Result.ok(authService.wxLogin(req));
    }

    @PostMapping("/login")
    public Result<?> adminLogin(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.adminLogin(req));
    }

    @PostMapping("/phone-login")
    public Result<?> phoneLogin(@Valid @RequestBody PhoneLoginRequest req) {
        return Result.ok(authService.phoneLogin(req));
    }

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest req, Authentication auth) {
        // 公开注册：未先经过 wxLogin 时 auth 可能为 null
        Long userId = auth != null ? Long.valueOf(auth.getName()) : null;
        return Result.ok(authService.register(req, userId));
    }

    @GetMapping("/status")
    public Result<?> getStatus(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(authService.getAuthStatus(userId));
    }

    @PostMapping("/appeal")
    public Result<?> appeal(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(authService.appeal(userId));
    }
}
