package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.SubmissionInsights;
import com.hwsuanfa.algo.dto.SubmissionImportRequest;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.SubmissionRecord;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.spider.SpiderUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 提交记录同步与查询：爬虫同步 / 洛谷脚本导入 / 我的记录 / 刷题画像聚合。
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionSyncController {

    private final SpiderUseCase spiderUseCase;
    private final ProblemMapper problemMapper;

    /** 同步指定平台的提交记录（限流 3 次/天/平台，由 UseCase 控制） */
    @PostMapping("/sync/{platform}")
    public ApiResult<SpiderUseCase.SyncResult> sync(@PathVariable String platform,
                                                    @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(spiderUseCase.syncSubmissions(me.getId(), platform));
    }

    /** 洛谷脚本批量导入 AC 记录（令牌鉴权，限 5000 条） */
    @PostMapping("/import/{platform}")
    public ApiResult<SpiderUseCase.SyncResult> importRecords(@PathVariable String platform,
                                                            @Valid @RequestBody SubmissionImportRequest request,
                                                            @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(spiderUseCase.importSubmissions(me.getId(), platform, request));
    }

    /** 我的提交记录（学习档案 / 首页 / 画像共用） */
    @GetMapping("/mine")
    public ApiResult<List<SubmissionRecord>> mine(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(spiderUseCase.mySubmissions(me.getId()));
    }

    /** 刷题画像聚合：平台 / 难度 / 层级 / 标签（数据源：本人 AC 记录 join 题目） */
    @GetMapping("/insights")
    public ApiResult<Map<String, Object>> insights(@AuthenticationPrincipal LoginUser me) {
        List<SubmissionRecord> acs = spiderUseCase.mySubmissions(me.getId());
        List<Long> pids = acs.stream()
                .map(SubmissionRecord::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Problem> problems = pids.isEmpty()
                ? Map.of()
                : problemMapper.selectBatchIds(pids).stream()
                        .collect(Collectors.toMap(Problem::getId, p -> p));
        return ApiResult.ok(SubmissionInsights.summarize(acs, problems));
    }
}
