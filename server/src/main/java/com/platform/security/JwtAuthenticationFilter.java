package com.platform.security;

import com.platform.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtProvider, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        String uri = request.getRequestURI();

        if (!StringUtils.hasText(token)) {
            log.debug("No Authorization header on request to {}", uri);
        } else if (!jwtProvider.validate(token)) {
            String reason = jwtProvider.getLastError();
            log.warn("JWT validation failed for {} — reason: {}", uri, reason);
        } else {
            String userId = jwtProvider.getUserId(token);
            String userType = jwtProvider.getUserType(token);
            String role = "super_admin".equals(userType) ? "ROLE_SUPER_ADMIN" :
                         "admin".equals(userType) ? "ROLE_ADMIN" : "ROLE_USER";

            if ("ROLE_USER".equals(role)) {
                // 单会话登录：token 必须携带该用户当前 token_version。
                // 每请求一次 PK 查询（版本+审核状态一次查出），单实例部署足够；将来横向扩展可在此加缓存。
                Integer ver = jwtProvider.getTokenVersion(token);
                UserRepository.AuthProbe probe = null;
                try { probe = userRepository.findAuthProbeById(Long.valueOf(userId)); }
                catch (NumberFormatException ignored) { }
                if (ver == null || probe == null || !ver.equals(probe.getTokenVersion())) {
                    // 对 /api/auth/ 下的端点（审核状态查询、申诉等），即使 token 版本过期也放行。
                    // 场景：管理员驳回/封禁 → tokenVersion+1 → 用户点刷新仍需看到最新状态，
                    // 不应被 401 踢到登录页。token 签名仍然有效，userId 可用于身份识别。
                    if (!uri.startsWith("/api/auth/")) {
                        log.info("Rejecting stale token for user {} on {} — tokenVer={}, currentVer={}",
                                userId, uri, ver, probe == null ? null : probe.getTokenVersion());
                        filterChain.doFilter(request, response);
                        return;
                    }
                    log.debug("Allowing stale token for auth endpoint: userId={}, uri={}", userId, uri);
                }
                // 未审核通过的账号只放行白名单前缀，其余业务接口一律 403
                if (!"approved".equals(probe.getAuthStatus()) && !isAllowedForUnapproved(uri)) {
                    log.info("Rejecting unapproved user {} on {} — authStatus={}", userId, uri, probe.getAuthStatus());
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"账号未通过审核\",\"data\":\""
                            + probe.getAuthStatus() + "\"}");
                    return;   // 不再 doFilter，直接短路
                }
            }

            log.debug("JWT authenticated: userId={}, userType={}, role={}, uri={}",
                    userId, userType, role, uri);

            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null,
                    List.of(new SimpleGrantedAuthority(role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    /** 未审核账号可访问的路径前缀：审核状态查询/申诉/重新注册登录、证件上传、静态资源 */
    private boolean isAllowedForUnapproved(String uri) {
        return uri.startsWith("/api/auth/")
            || uri.startsWith("/api/common/")
            || uri.startsWith("/uploads/");
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
