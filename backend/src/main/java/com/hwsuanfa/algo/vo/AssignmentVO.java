package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 作业视图（列表与详情共用）。
 *
 * 状态分两层：
 *   - timeStatus：纯时间态，对所有人一样（未开始 / 进行中 / 已逾期）
 *   - mine.status：访问者个人状态（再叠加 已完成 / 逾期完成）
 * 这样列表页不必为每个用户重算，详情页又能给出准确个人状态。
 */
@Data
@Builder
public class AssignmentVO {

    private Long id;
    /** TEAM / GLOBAL / DAILY */
    private String type;
    private String title;
    private String description;

    private LocalDateTime startAt;
    private LocalDateTime dueAt;
    private java.math.BigDecimal scoreReward;
    private Boolean allowLateReward;
    private LocalDate publishDate;
    private LocalDateTime createdAt;

    /** 纯时间态 */
    private String timeStatus;
    /** 距截止的剩余秒数，负数表示已过期（前端倒计时直接用，不用自己算） */
    private Long secondsUntilDue;
    /** 距开始的剩余秒数，负数表示已开始 */
    private Long secondsUntilStart;

    /** 目标队伍快照 */
    private List<TeamRef> targets;
    /** 被指派队伍数（列表页展示用） */
    private Integer targetTeamCount;

    /** 题目清单（列表页可为空以省流量） */
    private List<ProblemVO> problems;
    private Integer totalCount;

    /** 访问者本人进度；站主查看他人时可为 null */
    private MyProgress mine;

    /** 站主视角统计：完成人数 / 应完成人数 */
    private AdminStats stats;

    @Data
    @Builder
    public static class TeamRef {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    public static class MyProgress {
        /** IN_PROGRESS / COMPLETED */
        private String rawStatus;
        /** 叠加时间后的展示状态：COMPLETED / OVERDUE_DONE / OVERDUE / IN_PROGRESS / NOT_STARTED */
        private String status;
        private Boolean late;
        private Integer completedCount;
        private Integer totalCount;
        private LocalDateTime completedAt;
        /** 已完成的题目 id，前端用来打勾 */
        private Set<Long> doneProblemIds;
        /** 是否已拿到该项积分（避免前端误显示「可加分」） */
        private Boolean scored;
    }

    @Data
    @Builder
    public static class AdminStats {
        /** 目标队伍数 */
        private Integer targetTeams;
        /** 应完成人数（目标队伍的在队人数） */
        private Integer targetUsers;
        /** 已完成人数 */
        private Integer completedUsers;
        /** 逾期未完成人数 */
        private Integer overdueUsers;
    }
}
