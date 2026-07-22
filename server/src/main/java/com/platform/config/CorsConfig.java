package com.platform.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * CorsConfigurationSource Bean — 由 Spring Security 的 .cors() DSL 自动拾取。
     * Spring Security 会基于此自行构建内部的 CorsFilter，无需我们再提供一个 corsFilter bean。
     * 对非 preflight 的跨域请求来说，这是关键配置。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Servlet 层的 CorsFilter（兜底），仅覆盖 /api/* 和 /uploads/*。
     * 排除 /ws/** 路径：Spring WebSocket 有自己独立的 origin 校验（通过
     * WebSocketHandlerRegistration.setAllowedOriginPatterns），Servlet 层的
     * CorsFilter 响应头包装会干扰 WebSocket 升级握手，导致返回 200 而非 101。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> servletCorsFilterRegistration() {
        CorsFilter filter = new CorsFilter(corsConfigurationSource());
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(0);
        registration.addUrlPatterns("/api/*", "/uploads/*");
        return registration;
    }
}
