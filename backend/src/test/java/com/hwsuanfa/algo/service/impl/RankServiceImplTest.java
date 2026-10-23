package com.hwsuanfa.algo.service.impl;

import com.hwsuanfa.algo.mapper.RankQueryMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankServiceImplTest {
    @Test
    void seededTeamWithoutLeaderDoesNotCrashLeaderboard() {
        RankQueryMapper ranks = mock(RankQueryMapper.class);
        when(ranks.selectUserLeaderboard()).thenReturn(List.of());
        Map<String, Object> row = new HashMap<>();
        row.put("teamId", 1L);
        row.put("teamName", "示例队伍");
        row.put("leaderUserId", null);
        row.put("memberCount", 0);
        when(ranks.selectTeamLeaderboard()).thenReturn(List.of(row));
        var service = new RankServiceImpl(ranks, mock(UserMapper.class), mock(TeamMapper.class), mock(TeamMemberMapper.class));
        var data = service.leaderboard();
        assertEquals(1, data.getTeams().size());
        assertNull(data.getTeams().getFirst().getLeaderNickname());
        assertEquals(0, data.getTeams().getFirst().getAvgScore().intValue());
    }
}
