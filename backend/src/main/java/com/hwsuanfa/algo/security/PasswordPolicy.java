package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码长度应为 8-64 位");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码编码后不能超过 72 字节，请减少中文或特殊字符");
        }
    }
}
