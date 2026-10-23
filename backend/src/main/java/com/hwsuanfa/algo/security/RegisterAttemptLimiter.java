package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.config.RegisterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 注册邀请码的尝试次数限制。
 *
 * 为什么必须要有：邀请码是短字符串（本项目普通码只有 6 位），
 * 不限流的话，暴力枚举猜中管理员码 = 直接拿到全站最高权限。
 * 按 IP 计数，超过阈值就在窗口内拒绝。
 *
 * 注意：这是「按 IP」的粗粒度限流，校内同一出口 IP 的多人会共用计数。
 * 阈值给得比较宽松（默认 1 小时 10 次），正常学生填错一两次不会被误伤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterAttemptLimiter {

    private static final String KEY_PREFIX = "reg:fail:";

    private final StringRedisTemplate redis;
    private final RegisterProperties props;

    /** 是否已被限流 */
    public boolean isBlocked(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        String raw = redis.opsForValue().get(KEY_PREFIX + ip);
        if (raw == null) {
            return false;
        }
        try {
            return Integer.parseInt(raw) >= props.getMaxAttemptsPerWindow();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 记录一次失败尝试，返回当前窗口内的失败次数 */
    public long recordFailure(String ip) {
        if (!StringUtils.hasText(ip)) {
            return 0;
        }
        String key = KEY_PREFIX + ip;
        Long count = redis.opsForValue().increment(key);
        // 只在第一次计数时设过期，后续 INCR 不重置窗口，避免攻击者用持续请求把窗口无限顺延
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(props.getAttemptWindowMinutes()));
        }
        if (count != null && count >= props.getMaxAttemptsPerWindow()) {
            log.warn("注册邀请码尝试次数超限，IP={}，次数={}", ip, count);
        }
        return count == null ? 0 : count;
    }

    /** 注册成功后清掉计数，避免正常用户被自己之前的误输拖累 */
    public void clear(String ip) {
        if (StringUtils.hasText(ip)) {
            redis.delete(KEY_PREFIX + ip);
        }
    }

    public int remainingAttempts(String ip) {
        String raw = redis.opsForValue().get(KEY_PREFIX + ip);
        int used = 0;
        if (raw != null) {
            try {
                used = Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                // 计数损坏时按 0 处理，不阻塞正常使用
            }
        }
        return Math.max(0, props.getMaxAttemptsPerWindow() - used);
    }
}
