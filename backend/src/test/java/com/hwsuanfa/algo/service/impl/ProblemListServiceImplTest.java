package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.dto.ProblemImportRequest;
import com.hwsuanfa.algo.dto.ProblemListRequest;
import com.hwsuanfa.algo.entity.ProblemList;
import com.hwsuanfa.algo.entity.ProblemListItem;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.ProblemListItemMapper;
import com.hwsuanfa.algo.mapper.ProblemListMapper;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.DifficultyService;
import com.hwsuanfa.algo.service.ProblemService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemListServiceImplTest {

    @Mock private ProblemListMapper lists;
    @Mock private ProblemListItemMapper items;
    @Mock private ProblemMapper problems;
    @Mock private TeamMapper teams;
    @Mock private TeamMemberMapper members;
    @Mock private UserMapper users;
    @Mock private ProblemService problemService;
    @Mock private DifficultyService difficulty;
    @Mock private AuditLogService audit;
    @InjectMocks private ProblemListServiceImpl service;

    private LoginUser me;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProblemList.class);
        User user = new User();
        user.setId(7L);
        user.setRole("MEMBER");
        me = new LoginUser(user);
    }

    @Test
    void userWithNoTeamsCanListPersonalListsWithoutEmptyInClause() {
        when(lists.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ProblemList> query = invocation.getArgument(0);
            assertFalse(query.getSqlSegment().contains("IN ()"));
            assertFalse(query.getSqlSegment().contains("team_id"));
            return List.of();
        });

        assertEquals(List.of(), service.myLists(me));
    }

    @Test
    void listDoesNotExposeOtherMembersPendingOrRejectedSubmissions() {
        TeamMember member = new TeamMember();
        member.setTeamId(1L);
        when(members.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        Team team = new Team();
        team.setId(1L);
        team.setLeaderUserId(1L);
        when(teams.selectById(1L)).thenReturn(team);
        when(lists.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(teamList(1L, 8L, "PENDING"), teamList(2L, 8L, "REJECTED")));

        assertEquals(List.of(), service.myLists(me));
    }

    @Test
    void outsideMemberCannotSubmitToAnotherTeam() {
        Team team = new Team();
        team.setId(1L);
        when(teams.selectById(1L)).thenReturn(team);
        ProblemListRequest request = new ProblemListRequest();
        request.setScope("TEAM");
        request.setTeamId(1L);
        request.setName("test");

        assertThrows(BizException.class, () -> service.create(request, me));

        verify(lists, never()).insert(any(ProblemList.class));
    }

    @Test
    void unknownProblemProducesBusinessErrorBeforeItemInsertion() {
        ProblemList list = new ProblemList();
        list.setId(11L);
        list.setName("test");
        list.setScope("PERSONAL");
        list.setOwnerId(7L);
        when(lists.selectByIdForUpdate(11L)).thenReturn(list);
        ProblemImportRequest request = new ProblemImportRequest();
        request.setProblemIds(List.of(42L));
        when(problems.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThrows(BizException.class, () -> service.importProblems(11L, request, me));

        verify(items, never()).insert(any(ProblemListItem.class));
    }

    @Test
    void appendedProblemUsesOrderAfterLargestExistingOrder() {
        ProblemList list = new ProblemList();
        list.setId(11L);
        list.setName("test");
        list.setScope("PERSONAL");
        list.setOwnerId(7L);
        when(lists.selectById(11L)).thenReturn(list);
        when(lists.selectByIdForUpdate(11L)).thenReturn(list);
        ProblemListItem first = new ProblemListItem();
        first.setProblemListId(11L);
        first.setProblemId(10L);
        first.setSortOrder(0);
        ProblemListItem last = new ProblemListItem();
        last.setProblemListId(11L);
        last.setProblemId(20L);
        last.setSortOrder(3);
        when(items.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, last));
        when(problems.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        ProblemImportRequest request = new ProblemImportRequest();
        request.setProblemIds(List.of(42L));

        service.importProblems(11L, request, me);

        ArgumentCaptor<ProblemListItem> captor = ArgumentCaptor.forClass(ProblemListItem.class);
        verify(items).insert(captor.capture());
        assertEquals(4, captor.getValue().getSortOrder());
    }

    @Test
    void authorCanReviseRejectedListAndMustReturnToPendingReview() {
        stubOwnRejectedList();
        ProblemListRequest request = new ProblemListRequest();
        request.setName("revised");

        service.update(11L, request, me);

        verifyResubmission();
    }

    @Test
    void removingProblemFromRejectedListAlsoResubmitsForReview() {
        stubOwnRejectedList();

        service.removeProblem(11L, 42L, me);

        verifyResubmission();
    }

    @Test
    void importingProblemsIntoRejectedListAlsoResubmitsForReview() {
        stubOwnRejectedList();
        when(problems.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        ProblemImportRequest request = new ProblemImportRequest();
        request.setProblemIds(List.of(42L));

        service.importProblems(11L, request, me);

        verifyResubmission();
    }

    @Test
    void reorderingRejectedListAlsoResubmitsForReview() {
        stubOwnRejectedList();

        service.reorder(11L, List.of(), me);

        verifyResubmission();
    }

    @Test
    void authorCannotDirectlyModifyAnApprovedTeamList() {
        ProblemList list = teamList(11L, 7L, "APPROVED");
        when(lists.selectByIdForUpdate(11L)).thenReturn(list);
        when(teams.selectById(1L)).thenReturn(nonManagingTeam());
        ProblemListRequest request = new ProblemListRequest();
        request.setName("bypass");

        assertThrows(BizException.class, () -> service.update(11L, request, me));

        verify(lists, never()).updateById(any(ProblemList.class));
    }

    @Test
    void departedAuthorCannotReviseARejectedTeamList() {
        ProblemList list = teamList(11L, 7L, "REJECTED");
        when(lists.selectByIdForUpdate(11L)).thenReturn(list);
        when(teams.selectById(1L)).thenReturn(nonManagingTeam());
        ProblemListRequest request = new ProblemListRequest();
        request.setName("bypass");

        assertThrows(BizException.class, () -> service.update(11L, request, me));

        verify(lists, never()).updateById(any(ProblemList.class));
    }

    @Test
    void anotherMemberCannotReviseTheAuthorsRejectedList() {
        ProblemList list = teamList(11L, 8L, "REJECTED");
        when(lists.selectByIdForUpdate(11L)).thenReturn(list);
        when(teams.selectById(1L)).thenReturn(nonManagingTeam());

        assertThrows(BizException.class, () -> service.removeProblem(11L, 42L, me));

        verify(items, never()).delete(any(LambdaQueryWrapper.class));
    }

    private void stubOwnRejectedList() {
        ProblemList list = teamList(11L, 7L, "REJECTED");
        list.setName("test");
        list.setRejectReason("old reason");
        when(lists.selectById(11L)).thenReturn(list);
        when(lists.selectByIdForUpdate(11L)).thenReturn(list);
        when(teams.selectById(1L)).thenReturn(nonManagingTeam());
        TeamMember member = new TeamMember();
        member.setTeamId(1L);
        when(members.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
    }

    private Team nonManagingTeam() {
        Team team = new Team();
        team.setId(1L);
        team.setLeaderUserId(1L);
        return team;
    }

    private void verifyResubmission() {
        ArgumentCaptor<LambdaUpdateWrapper<ProblemList>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(lists).update(isNull(), captor.capture());
        String sql = captor.getValue().getSqlSet();
        assertTrue(sql.contains("audit_status"));
        assertTrue(sql.contains("audited_by"));
        assertTrue(sql.contains("audited_at"));
        assertTrue(sql.contains("reject_reason"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("PENDING"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(null));
    }

    private ProblemList teamList(Long id, Long ownerId, String status) {
        ProblemList list = new ProblemList();
        list.setId(id);
        list.setTeamId(1L);
        list.setOwnerId(ownerId);
        list.setScope("TEAM");
        list.setAuditStatus(status);
        return list;
    }
}
