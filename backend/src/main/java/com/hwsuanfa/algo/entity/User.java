package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。注意 password 字段永远不要放进任何 VO。
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录账号 = **学号**。
     * 注册时把学号写进这个字段，登录链路（JWT / LoginUserService）就不需要特判。
     */
    private String username;

    /** BCrypt 哈希 —— 严禁出现在任何返回给前端的对象里 */
    private String password;

    /** 名称 / 站内显示名，唯一（防冒用） */
    private String nickname;

    private String realName;
    private String studentNo;

    /** 班级 */
    private String className;

    private String avatar;
    private String email;

    /** SITE_OWNER / MEMBER */
    private String role;

    /** PRIVATE / PARTIAL / PUBLIC */
    private String privacyLevel;

    /** 是否公开提交代码（仅 PUBLIC 档有效） */
    private Boolean showCode;

    /** ACTIVE / DISABLED */
    private String status;

    /** 各 OJ 平台账号，JSON 字符串 */
    private String ojAccounts;

    /** 各平台 handle，抓官方榜单时做选手匹配（ojAccounts 的结构化替代，查询友好） */
    private String cfHandle;
    private String atcoderHandle;
    private String luoguHandle;
    private String leetcodeHandle;
    private String nowcoderHandle;
    private String qojHandle;

    /** 联系方式（站内互加用） */
    private String qq;
    private String wechat;
    private String phone;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
