package com.platform.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expiration;
    private volatile String lastError;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(String userId, String userType) {
        return generateToken(userId, userType, null);
    }

    /** tokenVersion 非 null 时写入 ver claim（C端单会话登录）；B端 token 不带 ver */
    public String generateToken(String userId, String userType, Integer tokenVersion) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .claim("userType", userType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key);
        if (tokenVersion != null) {
            builder.claim("ver", tokenVersion);
        }
        return builder.compact();
    }

    public String getUserId(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public String getUserType(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().get("userType", String.class);
    }

    /** 令牌版本号，缺失返回 null（B端 token 无此 claim） */
    public Integer getTokenVersion(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().get("ver", Integer.class);
    }

    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            lastError = null;
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            return false;
        }
    }

    public String getLastError() {
        return lastError;
    }
}
