package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 *
 * 登录账号就是**学号**，所以这里没有单独的 username 字段。
 * 注册后 role 由**邀请码**决定（普通码 → MEMBER，管理员码 → SITE_OWNER），
 * 前端不会、也不能指定角色。
 */
@Data
public class RegisterRequest {

    /** 名称 / 站内显示名 */
    @NotBlank(message = "请填写名称")
    @Size(min = 2, max = 32, message = "名称长度需在 2-32 字之间")
    private String nickname;

    @NotBlank(message = "请填写真实姓名")
    @Size(min = 2, max = 32, message = "真实姓名长度需在 2-32 字之间")
    private String realName;

    /** 登录账号 = 学号 */
    @NotBlank(message = "请填写学号")
    @Pattern(regexp = "^[A-Za-z0-9]{4,32}$", message = "学号只能是 4-32 位字母或数字")
    private String studentNo;

    @NotBlank(message = "请填写班级")
    @Size(min = 2, max = 64, message = "班级长度需在 2-64 字之间")
    private String className;

    /**
     * 明文密码，经 HTTPS 传输，服务端 BCrypt 存储。
     * ⚠️ 不要在前端做哈希 —— 那会让哈希本身变成等价密码，也无法配合 BCrypt。
     */
    @NotBlank(message = "请填写密码")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
    private String password;

    /** 邀请码。管理员码与普通码的比对只在后端进行 */
    @NotBlank(message = "请填写邀请码")
    @Size(max = 64, message = "邀请码过长")
    private String inviteCode;
}
