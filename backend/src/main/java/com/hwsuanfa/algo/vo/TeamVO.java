package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 队伍详情。
 *
 * 隐私约定：成员信息按访问者身份裁剪 ——
 *   - 站主：可见 realName / studentNo
 *   - 其他人：不含真实姓名与学号（这两个字段只在站主后台出现）
 * 裁剪在 Service 里做，不靠前端隐藏。
 */
@Data
@Builder
public class TeamVO {

    private Long id;
    private String name;
    private String description;

    /** 队长用户 id，可能为 null（队伍尚未指定队长） */
    private Long leaderUserId;

    private Integer maxMembers;
    private Integer memberCount;

    /** ACTIVE / FROZEN */
    private String status;
    private String frozenReason;

    private LocalDateTime createdAt;

    private List<MemberVO> members;

    /** 当前访问者是否本队队长 */
    private Boolean viewerIsLeader;
    /** 当前访问者是否站主 */
    private Boolean viewerIsSiteOwner;
    /** 当前访问者是否可编辑本队（站主 或 本队队长） */
    private Boolean editable;

    @Data
    @Builder
    public static class MemberVO {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;
        /** 是否本队队长 */
        private Boolean leader;
        private LocalDateTime joinedAt;

        /** 仅站主可见 —— 其他身份为 null */
        private String realName;
        /** 仅站主可见 —— 其他身份为 null */
        private String studentNo;
        /** 班级：站主可见，队内成员也可见（便于互相认人） */
        private String className;
    }
}
