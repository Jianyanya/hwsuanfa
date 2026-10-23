package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.entity.ScoreEvent;
import com.hwsuanfa.algo.entity.ScoreRule;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.ScoreRuleMapper;
import com.hwsuanfa.algo.security.TokenService;
import com.hwsuanfa.algo.service.DifficultyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreServiceImplTest {

    @Mock private ScoreEventMapper events;
    @Mock private ScoreRuleMapper rules;
    @Mock private TokenService tokens;
    @Mock private DifficultyService difficulty;

    private ScoreServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScoreServiceImpl(events, rules, tokens, difficulty);
    }

    @Test
    void disabledFirstAcCannotBeOverriddenByDifficultyScore() {
        ScoreRule rule = new ScoreRule();
        rule.setEnabled(false);
        rule.setScore(BigDecimal.TEN);
        when(rules.selectOne(any(LambdaQueryWrapper.class))).thenReturn(rule);

        assertFalse(service.grantFirstAc(7L, 42L, "LUOGU", "困难", BigDecimal.ONE));

        verify(difficulty, never()).resolveScore(any(), any(), any());
        verify(events, never()).insert(any(ScoreEvent.class));
    }

    @Test
    void zeroScoreDoesNotHoldIdempotentLock() {
        assertFalse(service.grant(7L, "FIRST_AC", "", BigDecimal.ZERO,
                "PROBLEM", 42L, "test", "zero"));

        verify(tokens, never()).acquireIdempotent(any(), any());
    }

    @Test
    void lockRemainsHeldUntilOuterTransactionFinishesThenAllowsRetry() {
        when(events.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(tokens.acquireIdempotent(eq("grant"), any())).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(service.grant(7L, "FIRST_AC", "", BigDecimal.ONE,
                    "PROBLEM", 42L, "test", "grant"));
            verify(tokens, never()).releaseIdempotent("grant");
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(tokens).releaseIdempotent("grant");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void nonTransactionalFailureReleasesIdempotentLock() {
        when(events.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(tokens.acquireIdempotent(eq("failed"), any())).thenReturn(true);
        doThrow(new IllegalStateException("database failed")).when(events).insert(any(ScoreEvent.class));

        assertThrows(IllegalStateException.class, () -> service.grant(7L, "FIRST_AC", "",
                BigDecimal.ONE, "PROBLEM", 42L, "test", "failed"));

        verify(tokens).releaseIdempotent("failed");
    }

    @Test
    void historicalContestRankAwardBlocksAnotherLogAward() {
        when(events.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertFalse(service.grantContestRank(7L, 11L, "rank"));

        verify(tokens, never()).acquireIdempotent(any(), any());
        verify(events, never()).insert(any(ScoreEvent.class));
    }

    @Test
    void contestRankIdempotencyUsesContestAndUserRatherThanLogId() {
        when(events.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(tokens.acquireIdempotent(eq("contest_rank_once:11:7"), any())).thenReturn(true);
        ScoreRule rule = new ScoreRule();
        rule.setEnabled(true);
        rule.setScore(BigDecimal.TEN);
        when(rules.selectOne(any(LambdaQueryWrapper.class))).thenReturn(rule);

        assertTrue(service.grantContestRank(7L, 11L, "rank"));

        ArgumentCaptor<ScoreEvent> captor = ArgumentCaptor.forClass(ScoreEvent.class);
        verify(events).insert(captor.capture());
        assertEquals("contest_rank_once:11:7", captor.getValue().getIdempotentKey());
        assertEquals(11L, captor.getValue().getBizId());
        assertEquals(7L, captor.getValue().getUserId());
    }

    @Test
    void reversingAlreadyReversedEventReturnsConflict() {
        ScoreEvent event = new ScoreEvent();
        event.setId(42L);
        event.setScore(BigDecimal.TEN);
        when(events.selectById(42L)).thenReturn(event);
        when(events.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException error = assertThrows(BizException.class, () -> service.reverse(42L, null, 7L));

        assertEquals("该事件已被撤销过", error.getMessage());
        verify(events, never()).insert(any(ScoreEvent.class));
    }

    @Test
    void concurrentReversalReturnsConflictWithoutAnotherDebit() {
        ScoreEvent event = new ScoreEvent();
        event.setId(42L);
        event.setUserId(7L);
        event.setScore(BigDecimal.TEN);
        when(events.selectById(42L)).thenReturn(event);
        when(events.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doThrow(new DuplicateKeyException("reverse raced")).when(events).insert(any(ScoreEvent.class));

        BizException error = assertThrows(BizException.class, () -> service.reverse(42L, null, 7L));

        assertEquals("该事件已被撤销过", error.getMessage());
    }
}
