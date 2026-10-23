package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "请输入用户名")
    @Size(max = 32, message = "用户名过长")
    private String username;

    /**
     * 明文密码，经 HTTPS 传输。
     * ⚠️ 不要在前端做哈希——本项目用 BCrypt，哈希后的值再传会破坏服务端校验。
     */
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
    private String password;
}
