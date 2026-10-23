package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新基本资料（头像传 data URL，≤1MB） */
@Data
public class ProfileUpdateRequest {

    @Size(max = 32, message = "昵称不能超过 32 字")
    private String nickname;

    /** 头像 data URL（jpg/png/webp） */
    @Size(max = 1_500_000, message = "头像太大，请选择 1MB 以内的图片")
    private String avatar;

    @Size(max = 64, message = "邮箱过长")
    private String email;

    /** PUBLIC / PARTIAL / PRIVATE */
    private String privacyLevel;

    /** 是否允许公开个人提交代码 */
    private Boolean showCode;

    @Size(max = 32, message = "QQ 过长")
    private String qq;

    @Size(max = 64, message = "微信过长")
    private String wechat;

    @Size(max = 20, message = "手机号过长")
    private String phone;
}
