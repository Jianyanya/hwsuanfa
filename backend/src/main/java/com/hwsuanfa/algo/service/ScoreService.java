package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.entity.ScoreRule;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 积分事件服务。
 *
 * 设计要点（对应计划书 4.8）：
 *   - 积分 = **事件流水 + 汇总值**，事件不可覆盖，撤销用反向事件。
 *   - 每条事件必须带 **幂等键**，靠 `score_event.idempotent_key` 唯一索引兜底，
 *     再加 Redis 抢锁削峰，保证并发下也不会重复发放。
 *   - 「同一道题 / 同一每日一题 / 同一作业」都不得重复发分。
 *
 * 分值类型是 BigDecimal（对应 DECIMAL 列），因为难度分档可能需要小数。
 */
public interface ScoreService {

    /**
     * 发放积分。**幂等**：同 key 重复调用只生效一次。
     *
     * @return true 表示本次真的发放了；false 表示之前已发过，本次跳过
     */
    boolean grant(Long userId, String eventType, String subKey, BigDecimal fallbackScore,
                  String bizType, Long bizId, String reason, String idempotentKey);

    /**
     * 首次完成一道题。
     * 分值按 (平台, 难度档位) 从 difficulty_score 取；取不到再回落到 fallbackScore。
     */
    boolean grantFirstAc(Long userId, Long problemId, String platform, String difficulty,
                         BigDecimal fallbackScore);

    boolean grantContestRank(Long userId, Long contestId, String reason);

    /** 完成每日一题：daily:{date}:{userId} */
    boolean grantDailySolved(Long userId, LocalDate date, BigDecimal fallbackScore);

    /** 完成作业：assignment:{assignmentId}:{userId} */
    boolean grantAssignmentDone(Long userId, Long assignmentId, BigDecimal fallbackScore);

    /** 取 score_rule 的分值；规则缺失或停用时返回 fallback */
    BigDecimal resolveRuleScore(String eventType, String subKey, BigDecimal fallback);

    /** 查规则明细（供后台展示/编辑） */
    ScoreRule findRule(String eventType, String subKey);

    /** 撤销一条事件：产生一条反向事件（负数），不改原记录 */
    void reverse(Long originalEventId, String reason, Long operatorId);
}
