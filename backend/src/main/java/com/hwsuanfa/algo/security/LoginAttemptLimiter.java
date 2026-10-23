package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.config.LoginProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    private static final String KEY_PREFIX = "login:fail:";
    private static final DefaultRedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; return count",
            Long.class);

    private final StringRedisTemplate redis;
    private final LoginProperties props;

    public boolean isBlocked(String ip, String username) {
        if (!props.isEnabled()) {
            return false;
        }
        String count = redis.opsForValue().get(key(ip, username));
        return count != null && Long.parseLong(count) >= props.getMaxAttemptsPerWindow();
    }

    public void recordFailure(String ip, String username) {
        if (props.isEnabled()) {
            redis.execute(RECORD_FAILURE, List.of(key(ip, username)),
                    String.valueOf(Duration.ofMinutes(props.getAttemptWindowMinutes()).toSeconds()));
        }
    }

    public void clear(String ip, String username) {
        if (props.isEnabled()) {
            redis.delete(key(ip, username));
        }
    }

    public int windowMinutes() {
        return props.getAttemptWindowMinutes();
    }

    private String key(String ip, String username) {
        String normalizedUser = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String source = (ip == null ? "unknown" : ip) + "\u0000" + normalizedUser;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
