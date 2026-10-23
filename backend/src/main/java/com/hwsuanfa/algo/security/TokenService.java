package com.hwsuanfa.algo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Refresh Token 的 Redis 管理。
 *
 * 两个作用：
 *   1. 存 refresh token 的 jti，实现「服务端可控注销」——
 *      光靠 JWT 自身过期无法主动失效，这是必需的补丁。
 *   2. access token 的 jti 黑名单（注销后立即失效）。
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String REFRESH_KEY = "auth:refresh:";
    private static final String BLACKLIST_KEY = "auth:blacklist:";
    private static final String SESSION_VERSION_KEY = "auth:version:";
    private static final DefaultRedisScript<Long> CONSUME_REFRESH = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;

    /* ---------------- Refresh Token ---------------- */

    /** 允许一个用户多个设备登录：按 jti 存，而不是按 userId */
    public void saveRefresh(String jti, Long userId, Duration ttl) {
        redis.opsForValue().set(REFRESH_KEY + jti, String.valueOf(userId), ttl);
    }

    public boolean consumeRefresh(String jti, Long userId) {
        if (jti == null || jti.isBlank() || userId == null) {
            return false;
        }
        return Long.valueOf(1L).equals(redis.execute(CONSUME_REFRESH,
                List.of(REFRESH_KEY + jti), String.valueOf(userId)));
    }

    public void revokeRefresh(String jti) {
        redis.delete(REFRESH_KEY + jti);
    }

    public long sessionVersion(Long userId) {
        String version = redis.opsForValue().get(SESSION_VERSION_KEY + userId);
        return version == null ? 0 : Long.parseLong(version);
    }

    public void invalidateSessions(Long userId) {
        redis.opsForValue().increment(SESSION_VERSION_KEY + userId);
    }

    /* ---------------- Access Token 黑名单 ---------------- */

    public void blacklist(String jti, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return; // 已过期，不必进黑名单
        }
        redis.opsForValue().set(BLACKLIST_KEY + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_KEY + jti));
    }

    /**
     * 幂等键检查 —— 积分发放等场景防重复。
     * 返回 true 表示本次是首次（可以继续），false 表示已存在（应跳过）。
     */
    public boolean acquireIdempotent(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent("idem:" + key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void releaseIdempotent(String key) {
        redis.delete("idem:" + key);
    }
}
