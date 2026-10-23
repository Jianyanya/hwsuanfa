package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.*;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AssignmentService;
import com.hwsuanfa.algo.vo.AssignmentVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminOverviewControllerTest {
    private final AssignmentService assignments = mock(AssignmentService.class);
    private final ScoreEventMapper scores = mock(ScoreEventMapper.class);
    private final AdminOverviewController controller = new AdminOverviewController(mock(TeamMapper.class), mock(UserMapper.class),
            mock(AwardMapper.class), mock(ProblemListMapper.class), scores, mock(AuditLogMapper.class), assignments);

    @Test
    void emptyOverviewAndWeightedCompletionAreValid() {
        when(assignments.adminList("ALL", false)).thenReturn(List.of(
                assignment(2, 1), assignment(8, 0)));
        when(scores.selectList(any())).thenReturn(List.of());
        when(scores.selectObjs(any())).thenReturn(List.of(BigDecimal.ZERO));
        var result = controller.overview(owner()).getData();
        assertEquals(10.0, result.get("assignmentCompletionRate"));
        assertEquals(0.0, result.get("dailyCompletionRate"));
        assertEquals(7, ((List<?>) result.get("recentScores")).size());
    }

    @Test
    void nonOwnerCannotReadGlobalMetrics() {
        User user = new User();
        user.setId(2L);
        user.setRole("MEMBER");
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BizException.class, () -> controller.overview(new LoginUser(user))).getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BizException.class, () -> controller.logs(100, null, null, null)).getErrorCode());
    }

    private AssignmentVO assignment(int targets, int done) {
        return AssignmentVO.builder().type("TEAM")
                .stats(AssignmentVO.AdminStats.builder().targetUsers(targets).completedUsers(done).build()).build();
    }

    private LoginUser owner() {
        User user = new User();
        user.setId(1L);
        user.setRole("SITE_OWNER");
        return new LoginUser(user);
    }
}
