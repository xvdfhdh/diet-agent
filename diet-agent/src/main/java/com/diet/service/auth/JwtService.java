package com.diet.service.auth;

import com.diet.model.DietUserRow;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final int tokenHours;

    public JwtService(@Value("${diet.auth.jwt-secret-base64:}") String secretBase64,
                      @Value("${diet.auth.token-hours:24}") int tokenHours) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException("必须配置 DIET_JWT_SECRET_BASE64（至少 32 字节随机密钥的 Base64）");
        }
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("DIET_JWT_SECRET_BASE64 不是有效 Base64", error);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("JWT 密钥至少需要 32 字节随机数据");
        }
        if (tokenHours < 1 || tokenHours > 168) {
            throw new IllegalStateException("diet.auth.token-hours 必须在 1~168 小时内");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.tokenHours = tokenHours;
    }

    public String issue(DietUserRow user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("diet-agent")
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenHours, ChronoUnit.HOURS)))
                .claim("tv", user.getTokenVersion())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public TokenIdentity verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer("diet-agent")
                .build().parseSignedClaims(token).getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        Integer tokenVersion = claims.get("tv", Integer.class);
        if (tokenVersion == null || userId <= 0) {
            throw new IllegalArgumentException("JWT 缺少有效身份信息");
        }
        return new TokenIdentity(userId, tokenVersion);
    }

    public record TokenIdentity(Long userId, int tokenVersion) {
    }
}
