package com.hwsuanfa.algo.service.impl;

import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.config.RegisterProperties;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.JwtTokenProvider;
import com.hwsuanfa.algo.security.RegisterAttemptLimiter;
import com.hwsuanfa.algo.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private JwtTokenProvider provider;
    private TokenService tokenService;
    private UserMapper userMapper;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider("test-secret-at-least-thirty-two-bytes", Duration.ofMinutes(15), Duration.ofDays(3));
        tokenService = mock(TokenService.class);
        userMapper = mock(UserMapper.class);
        TeamMemberMapper membershipMapper = mock(TeamMemberMapper.class);
        when(membershipMapper.selectList(any())).thenReturn(List.of());
        service = new AuthServiceImpl(mock(AuthenticationManager.class), provider, tokenService,
                new RegisterProperties(), mock(RegisterAttemptLimiter.class), mock(PasswordEncoder.class),
                userMapper, mock(TeamMapper.class), membershipMapper);
        User user = new User();
        user.setId(1L);
        user.setUsername("20260001");
        user.setNickname("选手");
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        when(userMapper.selectById(1L)).thenReturn(user);
    }

    @Test
    void refreshRotatesExactlyOnceAndUsesConfiguredAccessLifetime() {
        String token = provider.createRefreshToken(1L, "20260001", "MEMBER", false, 0);
        String jti = provider.parse(token).getId();
        when(tokenService.consumeRefresh(jti, 1L)).thenReturn(true, false);
        var result = service.refresh(token);
        assertEquals(900, result.getExpiresIn());
        assertEquals(1L, result.getUser().getId());
        assertEquals(0, provider.getSessionVersion(provider.parse(result.getAccessToken())));
        verify(tokenService).saveRefresh(any(), eq(1L), eq(Duration.ofDays(3)));
        BizException exception = assertThrows(BizException.class, () -> service.refresh(token));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void refreshFromInvalidatedSessionIsRejectedBeforeIssuingTokens() {
        when(tokenService.sessionVersion(1L)).thenReturn(1L);
        String token = provider.createRefreshToken(1L, "20260001", "MEMBER", false, 0);
        BizException exception = assertThrows(BizException.class, () -> service.refresh(token));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
        verify(tokenService, never()).consumeRefresh(any(), any());
        verify(tokenService, never()).saveRefresh(any(), any(), any());
    }

    @Test
    void accessTokenCannotBeUsedToRefreshSession() {
        String token = provider.createAccessToken(1L, "20260001", "MEMBER", false, 0);
        BizException exception = assertThrows(BizException.class, () -> service.refresh(token));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
    }
}
