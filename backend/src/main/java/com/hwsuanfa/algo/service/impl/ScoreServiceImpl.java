package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.ScoreEvent;
import com.hwsuanfa.algo.entity.ScoreRule;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.ScoreRuleMapper;
import com.hwsuanfa.algo.security.TokenService;
import com.hwsuanfa.algo.service.DifficultyService;
import com.hwsuanfa.algo.service.ScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    /** 幂等锁默认持有 10 分钟，足够覆盖一次业务事务 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final ScoreEventMapper scoreEventMapper;
    private final ScoreRuleMapper scoreRuleMapper;
    private final TokenService tokenService;
    private final DifficultyService difficultyService;

    @Override
    @Transactional
    public boolean grant(Long userId, String eventType, String subKey, BigDecimal fallbackScore,
                         String bizType, Long bizId, String reason, String idempotentKey) {
        // 规则驱动的分值：score_rule 说了算
        BigDecimal score = resolveRuleScore(eventType, subKey, fallbackScore);
        return doGrant(userId, eventType, score, bizType, bizId, reason, idempotentKey);
    }

    @Override
    @Transactional
    public boolean grantFirstAc(Long userId, Long problemId, String platform, String difficulty,
                                BigDecimal hardFallback) {
        /*
         * ⚠️ 分值优先级（踩过坑）：
         *   难度表精确档位 → 难度表的平台兜底档 → score_rule 里 FIRST_AC 的配置 → 硬编码兜底
         *
         * 注意**不能**把难度算出的分值塞给 grant() 当 fallback —— grant() 内部还会再查一次
         * score_rule，而 FIRST_AC 在 score_rule 里有配置，会把难度分值覆盖掉，
         * 结果所有题都发一样的分。
         */
        ScoreRule rule = findRule("FIRST_AC", "");
        if (rule != null && Boolean.FALSE.equals(rule.getEnabled())) {
            return false;
        }
        BigDecimal ruleFallback = rule == null || rule.getScore() == null ? hardFallback : rule.getScore();
        BigDecimal score = difficultyService.resolveScore(platform, difficulty, ruleFallback);

        String label = (difficulty == null || difficulty.isBlank()) ? "未标难度" : difficulty;
        return doGrant(userId, "FIRST_AC", score, "PROBLEM", problemId,
                "首次完成题目 #" + problemId + "（" + platform + " / " + label + "）",
                "first_ac:" + userId + ":" + problemId);
    }

    @Override
    @Transactional
    public boolean grantContestRank(Long userId, Long contestId, String reason) {
        Long existing = scoreEventMapper.selectCount(new LambdaQueryWrapper<ScoreEvent>()
                .eq(ScoreEvent::getUserId, userId)
                .eq(ScoreEvent::getEventType, "CONTEST_RANK")
                .eq(ScoreEvent::getBizType, "CONTEST")
                .eq(ScoreEvent::getBizId, contestId)
                .gt(ScoreEvent::getScore, BigDecimal.ZERO));
        if (existing != null && existing > 0) {
            return false;
        }
        return grant(userId, "CONTEST_RANK", "", BigDecimal.ZERO, "CONTEST", contestId,
                reason, "contest_rank_once:" + contestId + ":" + userId);
    }

    /**
     * 真正的落库逻辑：分值已经算好，这里只负责幂等与写入。
     *
     * 三道防线：① 先查幂等键 → ② Redis 抢锁串行化 → ③ 唯一索引兜底。
     */
    private boolean doGrant(Long userId, String eventType, BigDecimal score,
                            String bizType, Long bizId, String reason, String idempotentKey) {

        if (score == null || score.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        // ① 快路径：先查是否已发放（绝大多数重复请求在这一步就被挡掉）
        Long exists = scoreEventMapper.selectCount(
                new LambdaQueryWrapper<ScoreEvent>().eq(ScoreEvent::getIdempotentKey, idempotentKey));
        if (exists != null && exists > 0) {
            log.debug("积分已发放过，跳过：{}", idempotentKey);
            return false;
        }

        // ② Redis 抢锁：把并发请求串行化，避免都走到 insert 才发现冲突
        if (!tokenService.acquireIdempotent(idempotentKey, LOCK_TTL)) {
            log.debug("积分幂等锁被占用，跳过：{}", idempotentKey);
            return false;
        }

        boolean transactional = TransactionSynchronizationManager.isSynchronizationActive();
        if (transactional) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    tokenService.releaseIdempotent(idempotentKey);
                }
            });
        }

        try {
            ScoreEvent event = new ScoreEvent();
            event.setUserId(userId);
            event.setEventType(eventType);
            event.setScore(score);
            event.setBizType(bizType);
            event.setBizId(bizId);
            event.setReason(reason);
            event.setIdempotentKey(idempotentKey);
            event.setOccurredAt(LocalDateTime.now());

            scoreEventMapper.insert(event);
            log.info("积分发放：user={} {} +{}（{}）", userId, eventType, score, reason);
            return true;

        } catch (DuplicateKeyException e) {
            // ③ 唯一索引兜底：并发下另一个请求已插入，视为已发放
            log.debug("积分唯一索引冲突（已被并发请求发放）：{}", idempotentKey);
            return false;
        } finally {
            if (!transactional) {
                tokenService.releaseIdempotent(idempotentKey);
            }
        }
    }

    @Override
    @Transactional
    public boolean grantDailySolved(Long userId, LocalDate date, BigDecimal fallbackScore) {
        // 幂等键里带日期：每天每人只能拿一次
        return grant(userId, "DAILY_SOLVE", "", fallbackScore, "ASSIGNMENT", null,
                "完成 " + date + " 每日一题",
                "daily:" + date + ":" + userId);
    }

    @Override
    @Transactional
    public boolean grantAssignmentDone(Long userId, Long assignmentId, BigDecimal fallbackScore) {
        return grant(userId, "ASSIGNMENT_DONE", "", fallbackScore, "ASSIGNMENT", assignmentId,
                "完成作业 #" + assignmentId,
                "assignment:" + assignmentId + ":" + userId);
    }

    @Override
    public BigDecimal resolveRuleScore(String eventType, String subKey, BigDecimal fallback) {
        ScoreRule rule = findRule(eventType, subKey);
        if (rule == null) {
            return fallback;
        }
        if (Boolean.FALSE.equals(rule.getEnabled())) {
            // 规则被显式停用：返回 0 表示不发放，而不是回落到 fallback，
            // 否则「停用规则」这个操作就没有实际效果
            return BigDecimal.ZERO;
        }
        return rule.getScore() == null ? fallback : rule.getScore();
    }

    @Override
    public ScoreRule findRule(String eventType, String subKey) {
        String key = subKey == null ? "" : subKey;
        ScoreRule rule = scoreRuleMapper.selectOne(new LambdaQueryWrapper<ScoreRule>()
                .eq(ScoreRule::getEventType, eventType)
                .eq(ScoreRule::getSubKey, key));
        if (rule != null) {
            return rule;
        }
        // 带子键的规则没配时，退回无子键的通用规则
        if (!key.isEmpty()) {
            return scoreRuleMapper.selectOne(new LambdaQueryWrapper<ScoreRule>()
                    .eq(ScoreRule::getEventType, eventType)
                    .eq(ScoreRule::getSubKey, ""));
        }
        return null;
    }

    @Override
    @Transactional
    public void reverse(Long originalEventId, String reason, Long operatorId) {
        ScoreEvent original = scoreEventMapper.selectById(originalEventId);
        if (original == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "原积分事件不存在");
        }
        if (original.getReversedEventId() != null
                || scoreEventMapper.selectCount(new LambdaQueryWrapper<ScoreEvent>()
                .eq(ScoreEvent::getIdempotentKey, "reverse:" + originalEventId)) > 0) {
            throw BizException.of(ErrorCode.CONFLICT, "该事件已被撤销过");
        }
        if (original.getScore() == null || original.getScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "只能撤销正向事件");
        }

        // 用「反向事件」修正，绝不 UPDATE/删除原记录
        ScoreEvent reverse = new ScoreEvent();
        reverse.setUserId(original.getUserId());
        reverse.setEventType(original.getEventType());
        reverse.setScore(original.getScore().negate());
        reverse.setBizType(original.getBizType());
        reverse.setBizId(original.getBizId());
        reverse.setReason(reason == null ? "撤销事件 #" + originalEventId : reason);
        reverse.setIdempotentKey("reverse:" + originalEventId);
        reverse.setReversedEventId(originalEventId);
        reverse.setOperatorId(operatorId);
        reverse.setOccurredAt(LocalDateTime.now());
        try {
            scoreEventMapper.insert(reverse);
        } catch (DuplicateKeyException e) {
            throw BizException.of(ErrorCode.CONFLICT, "该事件已被撤销过");
        }

        log.info("撤销积分事件 {}（{} 分），反向事件 {}",
                originalEventId, original.getScore(), reverse.getId());
    }
}
