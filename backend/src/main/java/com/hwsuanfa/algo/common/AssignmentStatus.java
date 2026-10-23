package com.hwsuanfa.algo.common;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 作业状态。
 *
 * ⚠️ 状态**必须由后端按服务器时间计算**，不能只靠前端判断 ——
 * 客户端时钟可以被改，逾期判定就失去意义（计划书 4.3 的硬要求）。
 * 表里也不存 status，避免出现「时间到了但状态没变」的不一致。
 */
public final class AssignmentStatus {

    private AssignmentStatus() {
    }

    /** 未开始 */
    public static final String NOT_STARTED = "NOT_STARTED";
    /** 进行中 */
    public static final String IN_PROGRESS = "IN_PROGRESS";
    /** 已完成 */
    public static final String COMPLETED = "COMPLETED";
    /** 已逾期（未完成） */
    public static final String OVERDUE = "OVERDUE";
    /** 逾期完成 */
    public static final String OVERDUE_DONE = "OVERDUE_DONE";

    /**
     * 纯时间态 —— 对所有访问者都一样。
     *
     * @param now 服务器当前时间（由调用方传入，便于测试注入）
     */
    public static String timeStatus(LocalDateTime startAt, LocalDateTime dueAt, LocalDateTime now) {
        if (startAt != null && now.isBefore(startAt)) {
            return NOT_STARTED;
        }
        if (dueAt != null && now.isAfter(dueAt)) {
            return OVERDUE;
        }
        return IN_PROGRESS;
    }

    /**
     * 访问者视角的状态 —— 叠加个人完成情况。
     *
     * @param completed 是否已完成
     * @param late      完成时是否已逾期
     */
    public static String displayStatus(String timeStatus, boolean completed, boolean late) {
        if (completed) {
            return late ? OVERDUE_DONE : COMPLETED;
        }
        return timeStatus;
    }

    /** 是否已经过了截止时间（用于判定「逾期完成」） */
    public static boolean isPastDue(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt != null && now.isAfter(dueAt);
    }

    /** 全部题目是否都已完成 */
    public static boolean allDone(Set<Long> doneProblemIds, Set<Long> allProblemIds) {
        return !allProblemIds.isEmpty() && doneProblemIds.containsAll(allProblemIds);
    }
}
