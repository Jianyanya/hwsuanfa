package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.entity.Assignment;
import com.hwsuanfa.algo.entity.AssignmentProblem;
import com.hwsuanfa.algo.entity.AssignmentProgress;
import com.hwsuanfa.algo.entity.AssignmentTarget;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.SubmissionRecord;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.dto.AssignmentRequest;
import com.hwsuanfa.algo.mapper.AssignmentMapper;
import com.hwsuanfa.algo.mapper.AssignmentProblemMapper;
import com.hwsuanfa.algo.mapper.AssignmentProgressMapper;
import com.hwsuanfa.algo.mapper.AssignmentTargetMapper;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.SubmissionRecordMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.DifficultyService;
import com.hwsuanfa.algo.service.ProblemListService;
import com.hwsuanfa.algo.service.ProblemService;
import com.hwsuanfa.algo.service.ScoreService;
import com.hwsuanfa.algo.vo.AssignmentVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceImplTest {

    @Mock private AssignmentMapper assignments;
    @Mock private AssignmentTargetMapper targets;
    @Mock private AssignmentProblemMapper assignmentProblems;
    @Mock private AssignmentProgressMapper progress;
    @Mock private SubmissionRecordMapper submissions;
    @Mock private ProblemMapper problems;
    @Mock private ProblemService problemService;
    @Mock private TeamMapper teams;
    @Mock private TeamMemberMapper members;
    @Mock private ScoreEventMapper events;
    @Mock private ScoreService score;
    @Mock private DifficultyService difficulty;
    @Mock private ProblemListService problemLists;
    @Mock private AuditLogService audit;
    @Mock private UserMapper users;
    @InjectMocks private AssignmentServiceImpl service;

    private Assignment assignment;
    private LoginUser me;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SubmissionRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        User user = new User();
        user.setId(7L);
        user.setRole("MEMBER");
        me = new LoginUser(user);
        assignment = new Assignment();
        assignment.setId(11L);
        assignment.setType("DAILY");
        assignment.setTitle("test");
        assignment.setStartAt(LocalDateTime.now().minusDays(1));
        assignment.setDueAt(LocalDateTime.now().plusDays(1));
        assignment.setScoreReward(BigDecimal.TEN);
        assignment.setAllowLateReward(true);
    }

    @Test
    void confirmingRealAcDoesNotFabricateSubmissionOrGrantHistoricalFirstAc() {
        stubCompletion();
        when(submissions.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        AssignmentVO result = service.markProblemDone(11L, 42L, me);

        assertEquals("COMPLETED", result.getMine().getRawStatus());
        assertEquals(1, result.getMine().getCompletedCount());
        verify(submissions, never()).insert(any(SubmissionRecord.class));
        verify(score, never()).grantFirstAc(any(), any(), any(), any(), any());
        verify(assignments).selectByIdForUpdate(11L);
    }

    @Test
    void explicitCompletionAcceptsHistoricalRealAcWithoutFirstAcFlag() {
        stubCompletion();

        AssignmentVO result = service.completeAssignment(11L, me);

        assertEquals("COMPLETED", result.getMine().getRawStatus());
        assertEquals(1, result.getMine().getCompletedCount());
        verify(score).grantDailySolved(7L, java.time.LocalDate.now(), BigDecimal.TEN);
        verify(submissions, never()).insert(any(SubmissionRecord.class));
        verify(score, never()).grantFirstAc(any(), any(), any(), any(), any());
    }

    @Test
    void confirmationStillRequiresAnAcAfterAssignmentStart() {
        when(assignments.selectByIdForUpdate(11L)).thenReturn(assignment);
        when(assignmentProblems.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(problemRef()));
        when(submissions.selectCount(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<SubmissionRecord> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertEquals(true, sql.contains("submitted_at >="));
            assertEquals(true, query.getParamNameValuePairs().containsValue(assignment.getStartAt()));
            return 0L;
        });

        assertThrows(BizException.class, () -> service.markProblemDone(11L, 42L, me));

        verify(progress, never()).insert(any(AssignmentProgress.class));
        verify(score, never()).grantDailySolved(any(), any(), any());
    }

    @Test
    void emptyGlobalSnapshotDoesNotBecomeVisibleToFutureTeams() {
        assignment.setType("GLOBAL");
        when(assignments.selectById(11L)).thenReturn(assignment);

        assertThrows(BizException.class, () -> service.detail(11L, me));
    }

    @Test
    void usersInTwoTargetTeamsAreCountedOnce() {
        when(assignments.selectById(11L)).thenReturn(assignment);
        AssignmentTarget first = new AssignmentTarget();
        first.setTeamId(1L);
        AssignmentTarget second = new AssignmentTarget();
        second.setTeamId(2L);
        when(targets.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));
        when(teams.selectBatchIds(anyCollection())).thenReturn(List.of());
        TeamMember firstMembership = new TeamMember();
        firstMembership.setUserId(7L);
        TeamMember secondMembership = new TeamMember();
        secondMembership.setUserId(7L);
        when(members.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(firstMembership, secondMembership));

        AssignmentVO result = service.adminDetail(11L, me);

        assertEquals(1, result.getStats().getTargetUsers());
    }

    @Test
    void dailyWithoutTeamTargetsCountsAllActiveUsersIncludingSiteOwner() {
        when(assignments.selectById(11L)).thenReturn(assignment);
        when(users.selectCount(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<User> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertEquals(true, sql.contains("status"));
            assertEquals(false, sql.contains("role"));
            assertEquals(true, query.getParamNameValuePairs().containsValue("ACTIVE"));
            return 3L;
        });

        AssignmentVO result = service.adminDetail(11L, me);

        assertEquals(3, result.getStats().getTargetUsers());
    }

    @Test
    void completedAssignmentCannotHaveItsProblemSetReplaced() {
        when(assignments.selectByIdForUpdate(11L)).thenReturn(assignment);
        when(assignmentProblems.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(problemRef()));
        when(problems.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(progress.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        AssignmentRequest request = updateRequest(List.of(43L));

        BizException error = assertThrows(BizException.class, () -> service.update(11L, request, me));

        assertEquals("该作业已有完成记录，不能增删题目；可以修改标题、说明或调整原题目的顺序。",
                error.getMessage());
        verify(assignments, never()).updateById(any(Assignment.class));
        verify(score, never()).reverse(any(), any(), any());
    }

    @Test
    void completedAssignmentCanKeepTheSameProblemsAndChangeTheirOrder() {
        when(assignments.selectByIdForUpdate(11L)).thenReturn(assignment);
        when(assignments.selectById(11L)).thenReturn(assignment);
        AssignmentProblem second = new AssignmentProblem();
        second.setProblemId(43L);
        when(assignmentProblems.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(problemRef(), second));
        when(problems.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(progress.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        AssignmentRequest request = updateRequest(List.of(43L, 42L));

        service.update(11L, request, me);

        org.mockito.ArgumentCaptor<AssignmentProblem> captor =
                org.mockito.ArgumentCaptor.forClass(AssignmentProblem.class);
        verify(assignmentProblems, times(2)).insert(captor.capture());
        assertEquals(List.of(43L, 42L), captor.getAllValues().stream()
                .map(AssignmentProblem::getProblemId).toList());
        verify(progress).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void completedAssignmentCannotGainProblemsThroughPasteImport() {
        me.getUser().setRole("SITE_OWNER");
        when(assignments.selectByIdForUpdate(11L)).thenReturn(assignment);
        when(assignmentProblems.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(problemRef()));
        when(progress.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        Problem problem = new Problem();
        problem.setId(43L);
        when(problemService.findOrCreate("LUOGU", "P1002", "https://www.luogu.com.cn/problem/P1002", 7L))
                .thenReturn(problem);

        assertThrows(BizException.class,
                () -> service.importProblems(11L, "https://www.luogu.com.cn/problem/P1002", "LUOGU", me));

        verify(assignmentProblems, never()).insert(any(AssignmentProblem.class));
    }

    private AssignmentRequest updateRequest(List<Long> ids) {
        AssignmentRequest request = new AssignmentRequest();
        request.setTitle("updated");
        request.setStartAt(assignment.getStartAt());
        request.setDueAt(assignment.getDueAt());
        request.setProblemIds(ids);
        return request;
    }

    private void stubCompletion() {
        when(assignments.selectByIdForUpdate(11L)).thenReturn(assignment);
        when(assignmentProblems.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(problemRef()));
        SubmissionRecord ac = new SubmissionRecord();
        ac.setProblemId(42L);
        ac.setResult("AC");
        ac.setIsFirstAc(false);
        ac.setSubmittedAt(assignment.getStartAt().minusDays(1));
        when(submissions.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<SubmissionRecord> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            return sql.contains("result") && !sql.contains("is_first_ac")
                    && !sql.contains("submitted_at")
                    && query.getParamNameValuePairs().containsValue(7L)
                    ? List.of(ac) : List.of();
        });
        Problem problem = new Problem();
        problem.setId(42L);
        when(problems.selectBatchIds(anyCollection())).thenReturn(List.of(problem));
        AtomicReference<AssignmentProgress> saved = new AtomicReference<>();
        when(progress.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> saved.get());
        doAnswer(invocation -> {
            AssignmentProgress record = invocation.getArgument(0);
            record.setId(9L);
            saved.set(record);
            return 1;
        }).when(progress).insert(any(AssignmentProgress.class));
    }

    private AssignmentProblem problemRef() {
        AssignmentProblem ref = new AssignmentProblem();
        ref.setAssignmentId(11L);
        ref.setProblemId(42L);
        return ref;
    }
}
