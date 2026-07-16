package com.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 微信小程序服务 — 封装 code2Session、内容安全等微信 API 调用。
 */
@Service
public class WeChatService {

    private static final Logger log = LoggerFactory.getLogger(WeChatService.class);
    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    private final String appId;
    private final String appSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeChatService(
            @Value("${wechat.miniapp.app-id}") String appId,
            @Value("${wechat.miniapp.app-secret}") String appSecret,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 用 wx.login() 返回的临时 code 换取微信用户的 openid。
     *
     * @param code 前端 wx.login() 返回的 code（5 分钟内有效，一次性）
     * @return openid（同一微信用户每次相同）
     * @throws RuntimeException 当 code 无效或微信 API 报错时
     */
    public String code2Session(String code) {
        // 未配置 AppID 时回退到本地开发模式：code 即 openid
        if (appId == null || appId.isEmpty()) {
            log.warn("微信 AppID 未配置，使用本地开发模式（code 即 openid）");
            return code;
        }

        try {
            String response = restTemplate.getForObject(
                    CODE2SESSION_URL, String.class, appId, appSecret, code);

            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                int errcode = json.get("errcode").asInt();
                String errmsg = json.has("errmsg") ? json.get("errmsg").asText() : "未知错误";
                log.error("微信 code2Session 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new RuntimeException("微信登录失败: " + errmsg + " (" + errcode + ")");
            }

            String openid = json.get("openid").asText();
            log.debug("微信 code2Session 成功: openid={}", openid);
            return openid;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 code2Session 异常: {}", e.getMessage(), e);
            throw new RuntimeException("微信登录服务暂不可用，请稍后重试");
        }
    }
}
