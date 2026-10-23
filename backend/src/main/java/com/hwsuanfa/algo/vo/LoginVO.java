package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录返回。前端 auth store 直接消费这个结构。
 * 注意：绝不包含 password 字段。
 */
@Data
@Builder
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    /** 秒，前端可据此提前刷新 */
    private Long expiresIn;
    private UserVO user;

    @Data
    @Builder
    public static class UserVO {
        private Long id;
        /** 登录账号 = 学号 */
        private String username;
        /** 名称 / 站内显示名 */
        private String nickname;
        private String avatar;

        /**
         * 以下三项是**用户本人的**资料，返回给自己没有问题。
         * ⚠️ 但绝不能出现在别人的视角里 —— 真实姓名与学号仅站主可见，
         *    见 TeamVO.MemberVO 的裁剪逻辑。
         */
        private String realName;
        private String studentNo;
        private String className;

        /** SITE_OWNER / MEMBER */
        private String role;
        /** 便捷布尔，前端判断管理分组是否渲染 */
        private Boolean siteOwner;
        private String privacyLevel;
        private Boolean showCode;

        /** 所属队伍（用于侧栏「当前队伍」，无队伍为 null） */
        private List<TeamBrief> teams;

        private LocalDateTime lastLoginAt;
    }

    @Data
    @Builder
    public static class TeamBrief {
        private Long id;
        private String name;
        /** 是否该队队长 */
        private Boolean leader;
    }
}
