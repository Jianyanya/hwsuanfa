package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.config.LoginProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginAttemptLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private LoginProperties props;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        props = new LoginProperties();
        limiter = new LoginAttemptLimiter(redis, props);
    }

    @Test
    void defaultLimitBlocksAfterTenFailures() {
        when(values.get(anyString())).thenReturn("9", "10");
        assertFalse(limiter.isBlocked("203.0.113.1", "20260001"));
        assertTrue(limiter.isBlocked("203.0.113.1", "20260001"));
        assertEquals(15, limiter.windowMinutes());
    }

    @Test
    void usernamesAreNormalizedAndEachAddressAndUserHasAnIndependentHashedKey() {
        limiter.isBlocked("203.0.113.1", " StudentA ");
        limiter.isBlocked("203.0.113.1", "studenta");
        limiter.isBlocked("203.0.113.2", "studenta");
        limiter.isBlocked("203.0.113.1", "studentb");
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, times(4)).get(keys.capture());
        List<String> captured = keys.getAllValues();
        assertEquals(captured.get(0), captured.get(1));
        assertNotEquals(captured.get(0), captured.get(2));
        assertNotEquals(captured.get(0), captured.get(3));
        assertTrue(captured.get(0).matches("login:fail:[0-9a-f]{64}"));
        assertFalse(captured.get(0).contains("studenta"));
        assertFalse(captured.get(0).contains("203.0.113.1"));
    }

    @Test
    void failureRecordingUsesOneAtomicRedisOperationWithConfiguredWindow() {
        props.setAttemptWindowMinutes(2);
        limiter.recordFailure("203.0.113.1", "20260001");
        verify(redis).execute(any(DefaultRedisScript.class), anyList(), eq("120"));
        verifyNoInteractions(values);
    }

    @Test
    void successClearsOnlyItsOwnKey() {
        limiter.isBlocked("203.0.113.1", "20260001");
        ArgumentCaptor<String> queriedKey = ArgumentCaptor.forClass(String.class);
        verify(values).get(queriedKey.capture());
        limiter.clear("203.0.113.1", "20260001");
        verify(redis).delete(queriedKey.getValue());
    }

    @Test
    void limiterCanBeDisabledAndRejectsInvalidConfiguration() {
        props.setEnabled(false);
        assertFalse(limiter.isBlocked("203.0.113.1", "20260001"));
        limiter.recordFailure("203.0.113.1", "20260001");
        limiter.clear("203.0.113.1", "20260001");
        verifyNoInteractions(values);
        verify(redis, never()).delete(anyString());
        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), any());
        props.setMaxAttemptsPerWindow(0);
        assertThrows(IllegalStateException.class, props::validate);
    }
}
