package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

/** 我的个人资料（本人可见；handle 用于比赛榜单匹配） */
@Data
@Builder
public class ProfileVO {

    private Long id;
    private String username;
    private String nickname;
    private String realName;
    private String studentNo;
    private String className;
    private String avatar;
    private String email;
    private String privacyLevel;
    private Boolean showCode;

    private String cfHandle;
    private String atcoderHandle;
    private String luoguHandle;
    private String leetcodeHandle;
    private String nowcoderHandle;
    private String qojHandle;
    private String qq;
    private String wechat;
    private String phone;
}
