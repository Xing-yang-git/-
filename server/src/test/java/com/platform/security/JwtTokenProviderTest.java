package com.platform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider 单元测试")
class JwtTokenProviderTest {

    /** 测试用密钥（至少 256 bits = 32 字符，jjwt 0.12 要求） */
    private static final String TEST_SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes-long!!";
    /** 测试用过期时间 1 小时 */
    private static final long TEST_EXPIRATION = 3600_000L;

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(TEST_SECRET, TEST_EXPIRATION);
    }

    // ==================== 生成与校验往返 ====================

    @Test
    @DisplayName("生成并校验 token 往返成功")
    void should_generateAndValidate_when_validInput() {
        // 准备
        String userId = "123";
        String userType = "owner";

        // 执行
        String token = provider.generateToken(userId, userType);

        // 断言
        assertThat(provider.validate(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(userId);
        assertThat(provider.getUserType(token)).isEqualTo(userType);
    }

    @Test
    @DisplayName("生成带 tokenVersion 的 token 后可解析出版本号")
    void should_extractTokenVersion_when_versionPresent() {
        // 准备
        String userId = "456";
        String userType = "owner";
        Integer version = 3;

        // 执行
        String token = provider.generateToken(userId, userType, version);

        // 断言
        assertThat(provider.validate(token)).isTrue();
        assertThat(provider.getTokenVersion(token)).isEqualTo(version);
        assertThat(provider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("B端 token 不带 ver claim 时 getTokenVersion 返回 null")
    void should_returnNullVersion_when_noVersionClaim() {
        // 准备
        String token = provider.generateToken("1", "admin");

        // 执行
        Integer version = provider.getTokenVersion(token);

        // 断言
        assertThat(version).isNull();
        assertThat(provider.validate(token)).isTrue();
    }

    // ==================== 过期 token ====================

    @Test
    @DisplayName("过期 token 校验失败")
    void should_rejectToken_when_expired() {
        // 准备：创建一个极短过期时间的 provider
        JwtTokenProvider shortLived = new JwtTokenProvider(TEST_SECRET, 1L); // 1ms 过期
        String token = shortLived.generateToken("1", "owner");

        // 执行 & 断言
        assertThat(shortLived.validate(token)).isFalse();
        assertThat(shortLived.getLastError()).startsWith("ExpiredJwtException");
    }

    // ==================== 篡改与畸形 token ====================

    @Test
    @DisplayName("篡改签名后 token 校验失败")
    void should_rejectToken_when_tampered() {
        // 准备
        String token = provider.generateToken("1", "owner");
        // 篡改最后一个字符
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        // 执行 & 断言
        assertThat(provider.validate(tampered)).isFalse();
        assertThat(provider.getLastError()).isNotNull();
    }

    @Test
    @DisplayName("空字符串 token 校验失败")
    void should_rejectToken_when_empty() {
        // 准备
        String token = "";

        // 执行 & 断言
        assertThat(provider.validate(token)).isFalse();
        assertThat(provider.getLastError()).isNotNull();
    }

    @Test
    @DisplayName("null token 校验失败")
    void should_rejectToken_when_null() {
        // 准备
        String token = null;

        // 执行 & 断言
        assertThat(provider.validate(token)).isFalse();
        assertThat(provider.getLastError()).isNotNull();
    }

    @Test
    @DisplayName("畸形 token 校验失败")
    void should_rejectToken_when_malformed() {
        // 准备
        String token = "this.is.not.a.valid.jwt.token";

        // 执行 & 断言
        assertThat(provider.validate(token)).isFalse();
        assertThat(provider.getLastError()).isNotNull();
    }

    @Test
    @DisplayName("纯随机字符串 token 校验失败")
    void should_rejectToken_when_randomString() {
        // 准备
        String token = "abcdefghijklmnopqrstuvwxyz0123456789";

        // 执行 & 断言
        assertThat(provider.validate(token)).isFalse();
        assertThat(provider.getLastError()).isNotNull();
    }

    // ==================== 错误状态管理 ====================

    @Test
    @DisplayName("校验成功后 getLastError 被清空")
    void should_clearLastError_when_validationSucceeds() {
        // 准备：先校验失败一次
        provider.validate("bad-token");
        assertThat(provider.getLastError()).isNotNull();

        // 执行：再校验成功
        String validToken = provider.generateToken("1", "owner");
        boolean result = provider.validate(validToken);

        // 断言
        assertThat(result).isTrue();
        assertThat(provider.getLastError()).isNull();
    }

    // ==================== 不同用户类型 ====================

    @Test
    @DisplayName("支持不同的 userType 载荷值")
    void should_preserveUserType_when_differentRoles() {
        // 准备 & 执行
        String token = provider.generateToken("999", "super_admin", 5);

        // 断言
        assertThat(provider.getUserType(token)).isEqualTo("super_admin");
        assertThat(provider.getTokenVersion(token)).isEqualTo(5);
        assertThat(provider.getUserId(token)).isEqualTo("999");
    }
}
