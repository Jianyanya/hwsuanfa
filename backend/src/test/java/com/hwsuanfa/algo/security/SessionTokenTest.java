package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionTokenTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "unit-test-secret-with-at-least-32-bytes", Duration.ofMinutes(15), Duration.ofDays(3));
    private final TokenService tokenService = mock(TokenService.class);
    private final LoginUserService loginUserService = mock(LoginUserService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, loginUserService, tokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void legacyClaimDefaultsToSessionVersionZeroAndCurrentTokenCarriesVersion() {
        Claims legacy = Jwts.claims().subject("1").build();
        assertEquals(0, provider.getSessionVersion(legacy));
        Claims current = provider.parse(provider.createAccessToken(1L, "member", "MEMBER", false, 3));
        assertEquals(3, provider.getSessionVersion(current));
        assertEquals(Duration.ofMinutes(15), provider.getAccessTtl());
    }

    @Test
    void invalidatedUserCannotAuthenticateWithOldToken() throws Exception {
        when(tokenService.sessionVersion(1L)).thenReturn(1L);
        AtomicInteger calls = authenticate(1L, 0);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(1, calls.get());
        verifyNoInteractions(loginUserService);
    }

    @Test
    void legacySignedTokenWithoutVersionContinuesWorkingForUnchangedAccount() throws Exception {
        String token = Jwts.builder().subject("1").claim("typ", "access").id("legacy-test-token")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("unit-test-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
        when(loginUserService.loadById(1L)).thenReturn(activeUser(1L));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(1L, ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }

    @Test
    void otherUsersAndFreshTokenStillAuthenticate() throws Exception {
        when(tokenService.sessionVersion(1L)).thenReturn(1L);
        when(tokenService.sessionVersion(2L)).thenReturn(0L);
        when(loginUserService.loadById(anyLong())).thenAnswer(invocation -> activeUser(invocation.getArgument(0)));
        authenticate(2L, 0);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(2L, ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
        SecurityContextHolder.clearContext();
        authenticate(1L, 1);
        assertEquals(1L, ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }

    @Test
    void refreshTokenCannotAuthenticateAsAccessToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createRefreshToken(1L, "member", "MEMBER", false, 0));
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(loginUserService, tokenService);
    }

    @Test
    void invalidTtlFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(
                "unit-test-secret-with-at-least-32-bytes", Duration.ZERO, Duration.ofDays(3)));
    }

    private AtomicInteger authenticate(Long userId, long version) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createAccessToken(userId, "member", "MEMBER", false, version));
        AtomicInteger calls = new AtomicInteger();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> calls.incrementAndGet());
        return calls;
    }

    private LoginUser activeUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("member" + id);
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        return new LoginUser(user);
    }
}
