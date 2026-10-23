package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 积分排行榜视图（个人 + 队伍） */
@Data
@Builder
public class RankVO {

    private List<UserRow> users;
    private List<TeamRow> teams;

    @Data
    @Builder
    public static class UserRow {
        private Integer rank;
        private Long userId;
        private String nickname;
        private String className;
        private BigDecimal totalScore;
        private Integer solvedCount;
        private Integer firstAc;
        private Integer dailyCount;
        private Integer assignmentCount;
        private Integer contestCount;
        private String teamName;
    }

    @Data
    @Builder
    public static class TeamRow {
        private Integer rank;
        private Long teamId;
        private String teamName;
        private String leaderNickname;
        private Integer memberCount;
        private BigDecimal totalScore;
        private BigDecimal avgScore;
    }
}
