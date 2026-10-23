package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 比赛视图。
 * timeStatus 由后端按服务器时间算，前端不要自己用 Date.now() 判断。
 */
@Data
@Builder
public class ContestVO {

    private Long id;
    private String name;
    private String platform;
    private String url;

    /** S / A / B / C / OTHER */
    private String level;
    /** 参赛积分（按等级从规则里取出的值，前端直接显示） */
    private java.math.BigDecimal joinScore;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;

    /** NOT_STARTED / IN_PROGRESS / ENDED */
    private String timeStatus;
    private Long secondsUntilStart;
    private Long secondsUntilEnd;

    /** 已选择参赛的队伍 */
    private List<TeamRef> teams;
    private Integer teamCount;

    /** 当前访问者所在队伍是否已选参赛（队长选参赛用） */
    private Boolean myTeamSelected;

    /** 当前访问者的参赛身份：TEAM（本队参赛）/ USER（个人参赛），可同时有 */
    private List<String> myRoles;

    /** 是否允许个人报名（队伍没参赛时才允许） */
    private Boolean individualJoinAllowed;

    /** 比赛日志（详情页才带） */
    private List<LogVO> logs;

    @Data
    @Builder
    public static class TeamRef {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    public static class LogVO {
        private Long id;
        /** TEAM / USER —— 站内排名把两种混在一起算 */
        private String participantType;
        private Long teamId;
        private String teamName;
        private Long userId;
        private String userNickname;
        private String userAvatar;
        /** 参赛成员（队伍参赛时） */
        private List<MemberRef> members;
        /** 官方排名（手动填或从平台抓） */
        private String rankResult;
        private Integer solvedCount;
        private Integer acCount;
        /** 比赛用时秒 */
        private Integer durationSeconds;
        /** 罚时分钟 */
        private Integer penaltyMinutes;
        /** 站内排名：AC 降序 → 罚时升序 → 用时升序，队伍与个人混排 */
        private Integer siteRank;
        private String summary;
        private Long problemListId;
        /** PENDING / CONFIRMED */
        private String status;
        private String createdBy;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class MemberRef {
        private Long id;
        private String nickname;
    }
}
