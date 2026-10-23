package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户简要信息 —— 用于站主后台的成员选择器。
 * 绝不含密码字段。
 */
@Data
@Builder
public class UserBriefVO {
    private Long id;
    /** 登录账号 = 学号 */
    private String username;
    private String nickname;
    private String realName;
    private String studentNo;
    private String className;
    private String role;
}
