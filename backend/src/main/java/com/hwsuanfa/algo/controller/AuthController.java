package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.LoginRequest;
import com.hwsuanfa.algo.dto.RefreshRequest;
import com.hwsuanfa.algo.dto.RegisterRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.security.LoginAttemptLimiter;
import com.hwsuanfa.algo.service.AuthService;
import com.hwsuanfa.algo.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证入口。
 * /login 与 /refresh 在 SecurityConfig 中已放行，/logout 也放行
 * （令牌可能已过期，注销不应因认证失败而报错）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter loginLimiter;

    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginRequest request,
                                    HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        String username = request.getUsername();
        if (loginLimiter.isBlocked(ip, username)) {
            throw new BizException(ErrorCode.LOGIN_ATTEMPT_LIMIT,
                    "登录失败次数过多，请 " + loginLimiter.windowMinutes() + " 分钟后再试");
        }
        try {
            LoginVO result = authService.login(request);
            loginLimiter.clear(ip, username);
            return ApiResult.ok(result);
        } catch (BizException e) {
            if (e.getErrorCode() == ErrorCode.LOGIN_FAILED) {
                loginLimiter.recordFailure(ip, username);
            }
            throw e;
        }
    }

    /**
     * 邀请码注册。注册成功直接返回双令牌，前端无需再登录一次。
     *
     * role 由**邀请码**在后端决定（普通码 → MEMBER，管理员码 → SITE_OWNER），
     * 请求体里没有、也不能传角色字段。
     */
    @PostMapping("/register")
    public ApiResult<LoginVO> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        return ApiResult.ok(authService.register(request, resolveClientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    public ApiResult<LoginVO> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResult.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request,
                                  @RequestBody(required = false) RefreshRequest body) {
        authService.logout(resolveAccessToken(request),
                body == null ? null : body.getRefreshToken());
        return ApiResult.ok();
    }

    /** 当前登录用户 —— 前端刷新页面后用它恢复登录态 */
    @GetMapping("/me")
    public ApiResult<LoginVO.UserVO> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResult.ok(authService.buildUserVO(loginUser.getId()));
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
