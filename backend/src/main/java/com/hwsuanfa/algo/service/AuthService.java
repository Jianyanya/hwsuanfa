package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.dto.LoginRequest;
import com.hwsuanfa.algo.dto.RegisterRequest;
import com.hwsuanfa.algo.vo.LoginVO;

public interface AuthService {

    /** 账号密码登录，返回双令牌 + 用户信息 */
    LoginVO login(LoginRequest request);

    /**
     * 邀请码注册。注册成功后直接签发双令牌（免去再登录一次）。
     *
     * @param clientIp 用于邀请码尝试次数限流
     */
    LoginVO register(RegisterRequest request, String clientIp);

    /** 用 refresh token 换新的双令牌（轮换：旧的立即失效） */
    LoginVO refresh(String refreshToken);

    /** 注销：refresh token 拉黑 + access token 进黑名单 */
    void logout(String accessToken, String refreshToken);

    /** 按 ID 组装用户信息（登录与 /me 共用） */
    LoginVO.UserVO buildUserVO(Long userId);
}
