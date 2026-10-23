package com.hwsuanfa.algo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 生成与解析。
 *
 * access token 短期（默认 2h），refresh token 长期（默认 14d）。
 * refresh token 的 jti 存 Redis，支持主动注销（见 TokenService）。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl:2h}") Duration accessTtl,
            @Value("${app.jwt.refresh-ttl:14d}") Duration refreshTtl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 至少需要 32 字节（256 位），当前 " + bytes.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        if (accessTtl.isZero() || accessTtl.isNegative() || refreshTtl.isZero() || refreshTtl.isNegative()) {
            throw new IllegalStateException("JWT 有效期必须大于 0");
        }
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String createAccessToken(Long userId, String username, String role, boolean siteOwner, long sessionVersion) {
        return build(userId, username, role, siteOwner, TYPE_ACCESS, accessTtl, sessionVersion);
    }

    public String createRefreshToken(Long userId, String username, String role, boolean siteOwner, long sessionVersion) {
        return build(userId, username, role, siteOwner, TYPE_REFRESH, refreshTtl, sessionVersion);
    }

    private String build(Long userId, String username, String role, boolean siteOwner,
                         String type, Duration ttl, long sessionVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("owner", siteOwner)
                .claim("ver", sessionVersion)
                .claim(CLAIM_TYPE, type)
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名与过期；失败抛 JwtException 子类 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public long getSessionVersion(Claims claims) {
        Number version = claims.get("ver", Number.class);
        return version == null ? 0 : version.longValue();
    }

    /** 返回令牌剩余有效时长，用于设置 Redis TTL */
    public Duration remaining(Claims claims) {
        long ms = claims.getExpiration().getTime() - System.currentTimeMillis();
        return ms > 0 ? Duration.ofMillis(ms) : Duration.ZERO;
    }

    /** 令牌是否有效（不抛异常版本，供过滤器使用） */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 无效: {}", e.getMessage());
            return false;
        }
    }
}
