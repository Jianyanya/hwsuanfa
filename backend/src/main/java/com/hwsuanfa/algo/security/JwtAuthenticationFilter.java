package com.hwsuanfa.algo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Authorization: Bearer &lt;token&gt; 解析登录态。
 *
 * 这里刻意「解析失败就放行」——不抛异常、不写响应，
 * 让后续的授权规则决定是 401（需登录）还是继续（匿名可访问）。
 * 若在这里直接返回 401，匿名接口（如首页统计）会被误伤。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final LoginUserService loginUserService;
    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenProvider.parse(token);

                // refresh token 不能当 access token 用
                if (tokenProvider.isAccessToken(claims) && !tokenService.isBlacklisted(claims.getId())) {
                    Long userId = tokenProvider.getUserId(claims);
                    if (tokenProvider.getSessionVersion(claims) == tokenService.sessionVersion(userId)) {
                        LoginUser loginUser = loginUserService.loadById(userId);
                        if (loginUser.isEnabled()) {
                            var auth = new UsernamePasswordAuthenticationToken(
                                    loginUser, null, loginUser.getAuthorities());
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 令牌无效/过期：保持匿名，交由授权规则处理
                log.debug("JWT 解析失败，按匿名处理: {}", e.getMessage());
            } catch (Exception e) {
                // 用户已被删除等异常
                log.warn("加载登录用户失败: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
