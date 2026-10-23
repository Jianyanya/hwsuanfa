package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.vo.TeamVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @Mock private TeamMapper teams;
    @Mock private TeamMemberMapper members;
    @Mock private UserMapper users;
    @Mock private AuditLogService audit;
    @InjectMocks private TeamServiceImpl service;

    private Team team;
    private User member;

    @BeforeEach
    void setUp() {
        team = new Team();
        team.setId(1L);
        team.setLeaderUserId(7L);
        member = new User();
        member.setId(7L);
        member.setClassName("private class");
        member.setRealName("private name");
    }

    @Test
    void publicTeamDetailDoesNotExposeMembersClassNames() {
        stubDetail();

        TeamVO result = service.detail(1L, null);

        assertNull(result.getMembers().get(0).getClassName());
        assertNull(result.getMembers().get(0).getRealName());
    }

    @Test
    void teammateCanSeeClassNameButNotRealName() {
        stubDetail();

        TeamVO result = service.detail(1L, new LoginUser(member));

        assertEquals("private class", result.getMembers().get(0).getClassName());
        assertNull(result.getMembers().get(0).getRealName());
    }

    @Test
    void changingLeaderSharesRowLockWithMemberRemoval() {
        when(teams.selectByIdForUpdate(1L)).thenReturn(team);
        when(teams.selectById(1L)).thenReturn(team);

        service.changeLeader(1L, 7L, new LoginUser(member));

        verify(teams).selectByIdForUpdate(1L);
    }

    private void stubDetail() {
        when(teams.selectById(1L)).thenReturn(team);
        TeamMember membership = new TeamMember();
        membership.setUserId(7L);
        when(members.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(membership));
        when(users.selectBatchIds(anyCollection())).thenReturn(List.of(member));
    }
}
