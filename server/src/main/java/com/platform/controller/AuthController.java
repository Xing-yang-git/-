package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.*;
import com.platform.security.JwtTokenProvider;
import com.platform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 REST API — 微信登录、管理端登录、手机登录、注册、认证状态查询、申诉。
 *
 * <p>支持三种登录方式：
 * <ul>
 *   <li>微信小程序登录（wx-login）：通过微信 code 换取 openid，自动注册新用户</li>
 *   <li>B端管理端登录（login）：用户名 + 密码</li>
 *   <li>手机号登录（phone-login）：手机号 + 验证码</li>
 * </ul>
 *
 * <p>C端用户注册后需提交认证材料，审核通过后方可使用完整功能。
 * 审核被驳回的用户可通过申诉接口重新提交。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtProvider;

    public AuthController(AuthService authService, JwtTokenProvider jwtProvider) {
        this.authService = authService;
        this.jwtProvider = jwtProvider;
    }

    /**
     * 微信小程序登录 — 通过微信 code 换取 openid，新用户自动注册。
     *
     * @param req 包含微信临时 code 的请求体
     * @return JWT token + 用户信息
     */
    @PostMapping("/wx-login")
    public Result<?> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        return Result.ok(authService.wxLogin(req));
    }

    /**
     * B端管理端登录 — 用户名 + 密码认证。
     *
     * @param req 包含 username 和 password 的请求体
     * @return JWT token + 管理员信息
     */
    @PostMapping("/login")
    public Result<?> adminLogin(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.adminLogin(req));
    }

    /**
     * 手机号登录 — 手机号 + 验证码认证。
     *
     * @param req 包含 phone 和验证码的请求体
     * @return JWT token + 用户信息
     */
    @PostMapping("/phone-login")
    public Result<?> phoneLogin(@Valid @RequestBody PhoneLoginRequest req) {
        return Result.ok(authService.phoneLogin(req));
    }

    /**
     * C端用户注册 — 提交认证材料（姓名、手机号、房间号、证件照片等）。
     *
     * <p>公开注册接口：未先经过 wxLogin 时 auth 可能为 null。</p>
     *
     * @param req  注册信息（姓名、手机号、小区、楼栋、单元、房间号、证件照片）
     * @param auth 当前认证用户（可为 null）
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest req, Authentication auth) {
        Long userId = auth != null ? Long.valueOf(auth.getName()) : null;
        return Result.ok(authService.register(req, userId));
    }

    /**
     * 查询当前用户的认证状态。
     *
     * @param auth 当前认证用户
     * @return 认证状态：pending(待审核) / approved(已通过) / rejected(已驳回) / registering(注册中)
     */
    @GetMapping("/status")
    public Result<?> getStatus(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(authService.getAuthStatus(userId));
    }

    /**
     * 申诉 — 认证被驳回后重新提交审核。
     *
     * @param auth 当前认证用户
     * @return 申诉结果
     */
    @PostMapping("/appeal")
    public Result<?> appeal(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(authService.appeal(userId));
    }
}
