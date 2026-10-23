package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank(message = "缺少刷新令牌")
    private String refreshToken;
}
