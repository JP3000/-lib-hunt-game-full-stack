package com.libhunt.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析。
 * <p>
 * 密钥通过环境变量 {@code JWT_SECRET} 注入，默认值仅用于本地开发。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final Duration expire;

    public JwtUtil(@Value("${libhunt.jwt.secret}") String secret,
                   @Value("${libhunt.jwt.expire-hours:12}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expire = Duration.ofHours(expireHours);
    }

    public String issue(Long playerId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(playerId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire.toMillis()))
                .signWith(key)
                .compact();
    }

    public long expireSeconds() {
        return expire.toSeconds();
    }

    /**
     * 解析并校验签名与过期时间；任何异常都视为无效令牌。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
