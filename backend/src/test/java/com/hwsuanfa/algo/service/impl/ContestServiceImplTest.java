package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.StandingsRow;
import com.hwsuanfa.algo.dto.ContestLogRequest;
import com.hwsuanfa.algo.dto.ContestRequest;
import com.hwsuanfa.algo.entity.Contest;
import com.hwsuanfa.algo.entity.ContestLog;
import com.hwsuanfa.algo.entity.TeamContest;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.entity.UserContest;
import com.hwsuanfa.algo.mapper.ContestLogMapper;
import com.hwsuanfa.algo.mapper.ContestMapper;
import com.hwsuanfa.algo.mapper.TeamContestMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserContestMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.ScoreService;
import com.hwsuanfa.algo.service.standings.StandingsFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestServiceImplTest {

    @Mock private ContestMapper contests;
    @Mock private TeamContestMapper teamContests;
    @Mock private ContestLogMapper logs;
    @Mock private TeamMapper teams;
    @Mock private TeamMemberMapper members;
    @Mock private UserMapper users;
    @Mock private ScoreService score;
    @Mock private AuditLogService audit;
    @Mock private UserContestMapper userContests;
    @Mock private StandingsFetcher standings;

    private ContestServiceImpl service;
    private Contest contest;
    private LoginUser me;

    @BeforeEach
    void setUp() {
        service = service(List.of());
        contest = new Contest();
        contest.setId(11L);
        contest.setName("test");
        contest.setLevel("B");
        contest.setPlatform("LUOGU");
        User user = new User();
        user.setId(7L);
        user.setRole("SITE_OWNER");
        me = new LoginUser(user);
    }

    @Test
    void cancelledIndividualRegistrationPreservesContestHistory() {
        when(contests.selectById(11L)).thenReturn(contest);
        when(userContests.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThrows(BizException.class, () -> service.delete(11L, me));

        verify(contests, never()).deleteById(anyLong());
        verify(logs, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void manualIndividualScoreRequiresRegistrationEvenForSiteOwner() {
        when(contests.selectById(11L)).thenReturn(contest);
        ContestLogRequest request = new ContestLogRequest();
        request.setParticipantType("USER");
        request.setUserId(7L);

        assertThrows(BizException.class, () -> service.submitManualScore(11L, request, me));

        verify(logs, never()).insert(any(ContestLog.class));
        verify(score, never()).grantContestRank(any(), any(), any());
    }

    @Test
    void manualTeamScoreRequiresRegistrationEvenForSiteOwner() {
        when(contests.selectById(11L)).thenReturn(contest);
        ContestLogRequest request = new ContestLogRequest();
        request.setTeamId(1L);

        assertThrows(BizException.class, () -> service.submitManualScore(11L, request, me));

        verify(logs, never()).insert(any(ContestLog.class));
    }

    @Test
    void repeatedTeamLogUpdatesTheSameParticipantRatherThanCreatingNewLogs() {
        when(contests.selectById(11L)).thenReturn(contest);
        TeamContest selection = new TeamContest();
        selection.setTeamId(1L);
        selection.setContestId(11L);
        when(teamContests.selectOne(any(LambdaQueryWrapper.class))).thenReturn(selection);
        TeamMember member = new TeamMember();
        member.setUserId(7L);
        member.setTeamId(1L);
        when(members.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        ContestLog existing = new ContestLog();
        existing.setId(77L);
        existing.setStatus("CONFIRMED");
        when(logs.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        ContestLogRequest request = new ContestLogRequest();
        request.setTeamId(1L);
        request.setMemberIds(List.of(7L));
        request.setRankResult("12");

        service.submitLog(11L, request, me);
        service.submitLog(11L, request, me);

        verify(logs, never()).insert(any(ContestLog.class));
        ArgumentCaptor<ContestLog> captor = ArgumentCaptor.forClass(ContestLog.class);
        verify(logs, times(2)).updateById(captor.capture());
        assertEquals(List.of(77L, 77L), captor.getAllValues().stream().map(ContestLog::getId).toList());
        verify(score, times(2)).grantContestRank(eq(7L), eq(11L), any());
    }

    @Test
    void officialStandingsConfirmsPendingIndividualLogAndGrantsRankOnce() {
        contest.setPlatform("CODEFORCES");
        contest.setExternalId("1932");
        service = service(List.of(standings));
        when(contests.selectById(11L)).thenReturn(contest);
        when(standings.supports("CODEFORCES")).thenReturn(true);
        when(standings.fetch("1932"))
                .thenReturn(List.of(new StandingsRow("tester", "12", 3, 10, 600)));
        UserContest selection = new UserContest();
        selection.setUserId(7L);
        selection.setExternalHandle("tester");
        when(userContests.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(selection));
        ContestLog existing = new ContestLog();
        existing.setId(77L);
        existing.setStatus("PENDING");
        when(logs.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.syncStandings(11L, me);

        ArgumentCaptor<ContestLog> captor = ArgumentCaptor.forClass(ContestLog.class);
        verify(logs).updateById(captor.capture());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
        verify(score).grantContestRank(eq(7L), eq(11L), any());
    }

    @Test
    void atCoderContestUrlProvidesExternalIdForResultsFetcher() {
        ContestRequest request = new ContestRequest();
        request.setName("ABC 400");
        request.setPlatform("ATCODER");
        request.setLevel("B");
        request.setUrl("https://atcoder.jp/contests/abc400");

        service.create(request, me);

        ArgumentCaptor<Contest> captor = ArgumentCaptor.forClass(Contest.class);
        verify(contests).insert(captor.capture());
        assertEquals("abc400", captor.getValue().getExternalId());
    }

    private ContestServiceImpl service(List<StandingsFetcher> fetchers) {
        return new ContestServiceImpl(contests, teamContests, logs, teams, members, users,
                score, audit, new ObjectMapper(), userContests, fetchers, List.of());
    }
}
