package com.hwsuanfa.algo.service.impl;

import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Award;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.AwardMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.ScoreService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AwardServiceImplTest {
    @BeforeAll
    static void initializeMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Award.class);
    }

    private final AwardMapper awards = mock(AwardMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final AuditLogService auditLog = mock(AuditLogService.class);
    private final ScoreService scores = mock(ScoreService.class);
    private final AwardServiceImpl service = new AwardServiceImpl(awards, users, auditLog, scores);

    @Test
    void concurrentAuditLoserCannotGrantOrOverwriteDecision() {
        Award award = award();
        when(awards.selectById(12L)).thenReturn(award);
        when(awards.update(isNull(), any())).thenReturn(0);
        var error = assertThrows(BizException.class, () -> service.audit(12L, true, null, me()));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("PENDING", award.getAuditStatus());
        verifyNoInteractions(scores, auditLog);
    }

    @Test
    void rejectedAuditReturnsReasonImmediately() {
        Award award = award();
        when(awards.selectById(12L)).thenReturn(award);
        when(awards.update(isNull(), any())).thenReturn(1);
        var result = service.audit(12L, false, "缺少证明", me());
        assertEquals("REJECTED", result.getAuditStatus());
        assertEquals("缺少证明", result.getRejectReason());
        verifyNoInteractions(scores);
    }

    private Award award() {
        Award award = new Award();
        award.setId(12L);
        award.setUserId(7L);
        award.setAwardName("竞赛奖项");
        award.setAuditStatus("PENDING");
        return award;
    }

    private LoginUser me() {
        User user = new User();
        user.setId(1L);
        user.setRole("SITE_OWNER");
        return new LoginUser(user);
    }
}
