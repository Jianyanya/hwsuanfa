package com.hwsuanfa.algo.spider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.dto.SubmissionImportRequest;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.SubmissionRecord;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.mapper.SubmissionRecordMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.service.ScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpiderUseCaseTest {

    @Mock
    private SpiderRegistry registry;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private SubmissionRecordMapper submissionMapper;
    @Mock
    private ScoreService scoreService;
    @Mock
    private StringRedisTemplate redis;

    private SpiderUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SubmissionRecord.class);
        useCase = new SpiderUseCase(registry, userMapper, problemMapper,
                submissionMapper, scoreService, redis);
        user = new User();
        user.setId(7L);
        user.setNickname("tester");
        user.setLuoguHandle("luogu-user");
        when(userMapper.selectById(7L)).thenReturn(user);
    }

    @Test
    void rejectsNonLuoguManualImport() {
        SubmissionImportRequest request = request();

        BizException error = assertThrows(BizException.class,
                () -> useCase.importSubmissions(7L, "CODEFORCES", request));

        assertEquals("手动导入只支持 LUOGU", error.getMessage());
        verify(problemMapper, never()).selectOne(any());
    }

    @Test
    void rejectsImportBeforeLuoguBinding() {
        user.setLuoguHandle(" ");

        BizException error = assertThrows(BizException.class,
                () -> useCase.importSubmissions(7L, "LUOGU", request()));

        assertEquals("请先在个人中心绑定洛谷用户名", error.getMessage());
        verify(submissionMapper, never()).insert(any(SubmissionRecord.class));
    }

    @Test
    void allowsOtherPlatformManualImportWithoutBinding() {
        Problem problem = new Problem();
        problem.setId(42L);
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(problemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(problem);
        when(submissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(scoreService.grantFirstAc(7L, 42L, "OTHER", null,
                new java.math.BigDecimal("2"))).thenReturn(true);

        SpiderUseCase.SyncResult result = useCase.importSubmissions(7L, "OTHER", request());

        assertEquals("OTHER", result.platform());
        assertEquals(1, result.inserted());
        verify(submissionMapper).insert(any(SubmissionRecord.class));
    }

    @Test
    void repeatedImportDoesNotInsertOrGrantAgain() {
        Problem problem = new Problem();
        problem.setId(42L);
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(), List.of(existingRecord()));
        when(problemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(problem);
        when(submissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(scoreService.grantFirstAc(7L, 42L, "LUOGU", null,
                new java.math.BigDecimal("2"))).thenReturn(true);

        SpiderUseCase.SyncResult first = useCase.importSubmissions(7L, "luogu", request());
        SpiderUseCase.SyncResult second = useCase.importSubmissions(7L, "LUOGU", request());

        assertEquals(1, first.inserted());
        assertEquals(1, first.firstAcGranted());
        assertEquals(0, second.inserted());
        assertEquals(0, second.firstAcGranted());
        verify(submissionMapper).insert(any(SubmissionRecord.class));
        verify(scoreService).grantFirstAc(7L, 42L, "LUOGU", null,
                new java.math.BigDecimal("2"));
    }

    @Test
    void anotherUsersFirstAcDoesNotBlockMyFirstAc() {
        Problem problem = new Problem();
        problem.setId(42L);
        when(problemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(problem);
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(submissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<SubmissionRecord> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            return sql.contains("user_id") && query.getParamNameValuePairs().containsValue(7L) ? 0L : 1L;
        });
        when(scoreService.grantFirstAc(7L, 42L, "LUOGU", null,
                new java.math.BigDecimal("2"))).thenReturn(true);

        SpiderUseCase.SyncResult result = useCase.importSubmissions(7L, "LUOGU", request());

        assertEquals(1, result.inserted());
        assertEquals(1, result.firstAcGranted());
        verify(submissionMapper).insert(org.mockito.ArgumentMatchers.argThat(
                (SubmissionRecord record) -> record.getUserId().equals(7L)
                        && Boolean.TRUE.equals(record.getIsFirstAc())));
    }

    @Test
    void concurrentFirstAcKeepsTheDistinctLaterSubmission() {
        Problem problem = new Problem();
        problem.setId(42L);
        when(problemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(problem);
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(submissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doThrow(new DuplicateKeyException("first AC raced"))
                .doReturn(1).when(submissionMapper).insert(any(SubmissionRecord.class));

        SpiderUseCase.SyncResult result = useCase.importSubmissions(7L, "LUOGU", request());

        assertEquals(1, result.inserted());
        assertEquals(0, result.firstAcGranted());
        verify(scoreService, never()).grantFirstAc(any(), any(), any(), any(), any());
    }

    @Test
    void unsupportedAutomaticPlatformDoesNotConsumeDailyQuota() {
        assertThrows(BizException.class, () -> useCase.syncSubmissions(7L, "LUOGU"));

        verify(redis, never()).opsForValue();
    }

    @Test
    void newlySyncedProblemLinksToProblemPageRatherThanSubmissionPage() {
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(submissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            Problem problem = invocation.getArgument(0);
            problem.setId(42L);
            assertEquals("https://www.luogu.com.cn/problem/P1001", problem.getUrl());
            assertEquals(7L, problem.getCreatedBy());
            return 1;
        }).when(problemMapper).insert(any(Problem.class));

        useCase.importSubmissions(7L, "LUOGU", request());

        verify(problemMapper).insert(any(Problem.class));
    }

    private SubmissionImportRequest request() {
        SubmissionImportRequest.Item item = new SubmissionImportRequest.Item();
        item.setProblemNo("P1001");
        item.setSubmittedAt(LocalDateTime.of(2026, 9, 27, 12, 0));
        item.setLanguage("C++17");
        item.setUrl("https://www.luogu.com.cn/record/1");
        SubmissionImportRequest request = new SubmissionImportRequest();
        request.setRecords(List.of(item));
        return request;
    }

    private SubmissionRecord existingRecord() {
        SubmissionRecord record = new SubmissionRecord();
        record.setProblemId(42L);
        // 去重键是 (user, problem, submittedAt)，mock 记录要带相同时间才能命中
        record.setSubmittedAt(LocalDateTime.of(2026, 9, 27, 12, 0));
        return record;
    }
}
