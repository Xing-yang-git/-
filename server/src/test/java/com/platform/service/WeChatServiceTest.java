package com.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeChatService 微信服务单元测试 — 覆盖本地开发模式与微信 code2Session 错误分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeChatService 微信服务单元测试")
class WeChatServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("code2Session - 未配置 AppID 时本地开发模式 code 即 openid")
    void should_returnCode_when_appIdNotConfigured() {
        WeChatService service = new WeChatService("", "secret", restTemplate, objectMapper);

        String openid = service.code2Session("dev-code");

        assertThat(openid).isEqualTo("dev-code");
        verify(restTemplate, never()).getForObject(anyString(), eq(String.class), any(), any(), any());
    }

    @Test
    @DisplayName("code2Session - 成功时返回 openid")
    void should_returnOpenid_when_success() {
        WeChatService service = new WeChatService("wx-app", "secret", restTemplate, objectMapper);
        when(restTemplate.getForObject(anyString(), eq(String.class), any(), any(), any()))
                .thenReturn("{\"openid\":\"o-123\",\"session_key\":\"sk\"}");

        String openid = service.code2Session("valid-code");

        assertThat(openid).isEqualTo("o-123");
    }

    @Test
    @DisplayName("code2Session - 微信返回 errcode 时抛出带错误码的异常")
    void should_throw_when_wechatReturnsError() {
        WeChatService service = new WeChatService("wx-app", "secret", restTemplate, objectMapper);
        when(restTemplate.getForObject(anyString(), eq(String.class), any(), any(), any()))
                .thenReturn("{\"errcode\":40029,\"errmsg\":\"invalid code\"}");

        assertThatThrownBy(() -> service.code2Session("bad-code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("40029")
                .hasMessageContaining("invalid code");
    }

    @Test
    @DisplayName("code2Session - Runtime 异常原样透传不包装")
    void should_throw_when_apiCallFails() {
        WeChatService service = new WeChatService("wx-app", "secret", restTemplate, objectMapper);
        when(restTemplate.getForObject(anyString(), eq(String.class), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        // code2Session 对 RuntimeException 直接重抛（保持原始错误信息）
        assertThatThrownBy(() -> service.code2Session("code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("connection refused");
    }

    @Test
    @DisplayName("code2Session - 响应解析失败时包装为统一提示")
    void should_throw_when_responseMalformed() {
        WeChatService service = new WeChatService("wx-app", "secret", restTemplate, objectMapper);
        // readTree 抛 JsonProcessingException（受检异常）→ 进入统一包装分支
        when(restTemplate.getForObject(anyString(), eq(String.class), any(), any(), any()))
                .thenReturn("not-a-json");

        assertThatThrownBy(() -> service.code2Session("code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("微信登录服务暂不可用");
    }

    @Test
    @DisplayName("code2Session - 响应缺失 openid 时抛出友好异常")
    void should_throw_when_openidMissing() {
        WeChatService service = new WeChatService("wx-app", "secret", restTemplate, objectMapper);
        when(restTemplate.getForObject(anyString(), eq(String.class), any(), any(), any()))
                .thenReturn("{}");

        // openid 缺失时走友好错误分支（不再 NPE 原样透传），提示微信登录服务暂不可用
        assertThatThrownBy(() -> service.code2Session("code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("微信登录服务暂不可用");
    }

    @Test
    @DisplayName("code2Session - openid 为 null 时抛出友好异常")
    void should_throw_when_openidNull() {
        WeChatService service = new WeChatService("wx-app", "secret", restTemplate, objectMapper);
        when(restTemplate.getForObject(anyString(), eq(String.class), any(), any(), any()))
                .thenReturn("{\"openid\":null}");

        // "openid":null 经 hasNonNull 守卫同样走友好错误分支，不返回字面量 "null"
        assertThatThrownBy(() -> service.code2Session("code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("微信登录服务暂不可用");
    }

    @Test
    @DisplayName("code2Session - openid 为空串时抛出友好异常")
    void should_throw_when_openidEmpty() {
        WeChatService service = new WeChatService("wx-app", "secret", restTemplate, objectMapper);
        when(restTemplate.getForObject(anyString(), eq(String.class), any(), any(), any()))
                .thenReturn("{\"openid\":\"\"}");

        // openid 空串命中 isEmpty 拦截，提示微信登录服务暂不可用
        assertThatThrownBy(() -> service.code2Session("code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("微信登录服务暂不可用");
    }
}
