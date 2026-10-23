package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.common.GlobalExceptionHandler;
import com.hwsuanfa.algo.security.LoginAttemptLimiter;
import com.hwsuanfa.algo.service.AuthService;
import com.hwsuanfa.algo.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerLoginTest {

    private AuthService authService;
    private LoginAttemptLimiter limiter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        limiter = mock(LoginAttemptLimiter.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, limiter))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void blockedLoginReturns429WithoutCheckingPassword() throws Exception {
        when(limiter.isBlocked("203.0.113.1", "20260001")).thenReturn(true);
        when(limiter.windowMinutes()).thenReturn(15);
        mvc.perform(login()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
        verifyNoInteractions(authService);
        verify(limiter, never()).recordFailure(any(), any());
    }

    @Test
    void wrongPasswordCountsFailureAndDoesNotTrustForwardingHeaders() throws Exception {
        when(authService.login(any())).thenThrow(new BizException(ErrorCode.LOGIN_FAILED));
        mvc.perform(login().header("X-Forwarded-For", "203.0.113.99").header("X-Real-IP", "203.0.113.88"))
                .andExpect(status().isUnauthorized());
        verify(limiter).isBlocked("203.0.113.1", "20260001");
        verify(limiter).recordFailure("203.0.113.1", "20260001");
        verify(limiter, never()).clear(any(), any());
    }

    @Test
    void successfulLoginClearsTheCorrectAttemptKey() throws Exception {
        when(authService.login(any())).thenReturn(LoginVO.builder().accessToken("test-access").build());
        mvc.perform(login()).andExpect(status().isOk());
        verify(limiter).clear("203.0.113.1", "20260001");
        verify(limiter, never()).recordFailure(any(), any());
    }

    @Test
    void disabledAccountIsNotCountedAsWrongPassword() throws Exception {
        when(authService.login(any())).thenThrow(new BizException(ErrorCode.ACCOUNT_DISABLED));
        mvc.perform(login()).andExpect(status().isForbidden());
        verify(limiter, never()).recordFailure(any(), any());
        verify(limiter, never()).clear(any(), any());
    }

    @Test
    void infrastructureFailureIsNotCountedAsWrongPassword() throws Exception {
        when(authService.login(any())).thenThrow(new DataAccessResourceFailureException("test unavailable"));
        mvc.perform(login()).andExpect(status().isInternalServerError());
        verify(limiter, never()).recordFailure(any(), any());
    }

    private MockHttpServletRequestBuilder login() {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"20260001\",\"password\":\"wrong-password\"}")
                .with(request -> {
                    request.setRemoteAddr("203.0.113.1");
                    return request;
                });
    }
}
