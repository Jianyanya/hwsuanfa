package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.dto.HandleBindRequest;
import com.hwsuanfa.algo.vo.ProfileVO;

/** 个人资料：查看与绑定各平台的 OJ 账号（handle） */
public interface ProfileService {

    ProfileVO myProfile(Long userId);

    /** 绑定各平台 handle。全量替换（没传的平台视为解绑） */
    ProfileVO bindHandles(Long userId, HandleBindRequest req);

    /**
     * 在线校验某平台 handle 是否真实存在。
     *
     * 各平台能力不同（见 ProfileServiceImpl 的能力表）：
     *   - CODEFORCES / ATCODER：真实校验（有公开接口）
     *   - LEETCODE：尽力而为（非官方 GraphQL）
     *   - NOWCODER：无公开接口，明确返回「无法校验」
     */
    /** 更新基本资料：昵称 / 头像（data URL）/ 邮箱 / 隐私档位 */
    ProfileVO updateProfile(Long userId, com.hwsuanfa.algo.dto.ProfileUpdateRequest req);

    /** 修改密码（校验旧密码） */
    void changePassword(Long userId, String oldPassword, String newPassword);

    CfVerifyResult verifyHandle(String platform, String handle);

    record CfVerifyResult(boolean exists, String handle, Integer rating, String rank, String message) {
    }
}
