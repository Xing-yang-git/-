package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.*;
import com.platform.security.JwtTokenProvider;
import com.platform.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
    public Result<?> wxLogin(@RequestBody WxLoginRequest req) {
        return Result.ok(authService.wxLogin(req));
    }

    @PostMapping("/login")
    public Result<?> adminLogin(@RequestBody LoginRequest req) {
        return Result.ok(authService.adminLogin(req));
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(authService.register(req, userId));
    }

    @GetMapping("/status")
    public Result<?> getStatus(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(authService.getAuthStatus(userId));
    }

    @PostMapping("/appeal")
    public Result<?> appeal(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(authService.appeal(userId));
    }

    @PostMapping("/verification")
    public Result<?> submitVerification(@RequestBody VerificationSubmitRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        authService.submitVerification(userId, req);
        return Result.ok();
    }
}
