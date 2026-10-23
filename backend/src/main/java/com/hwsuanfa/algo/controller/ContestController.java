package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.dto.ContestLogRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.ContestService;
import com.hwsuanfa.algo.vo.ContestVO;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 比赛 —— 队员与队长视角。
 *
 * 比赛本身是公开信息（谁都能看），参赛与日志按队伍权限控制。
 * timeStatus 由后端按服务器时间算。
 */
@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    /** 全部比赛，带本队是否参赛标记 */
    @GetMapping
    public ApiResult<List<ContestVO>> list(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.list(me));
    }

    @GetMapping("/{id}")
    public ApiResult<ContestVO> detail(@PathVariable Long id,
                                      @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.detail(id, me));
    }

    /** 本队队长（或站主）选择参赛，按等级发参赛分 */
    @PostMapping("/{id}/participation")
    public ApiResult<ContestVO> select(@PathVariable Long id,
                                      @RequestBody TeamIdsRequest req,
                                      @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.selectParticipation(id, req.getTeamIds(), me));
    }

    /** 以个人身份报名（本队已报名时会被拒绝） */
    @PostMapping("/{id}/join-individual")
    public ApiResult<ContestVO> joinIndividual(@PathVariable Long id,
                                              @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.joinIndividually(id, me));
    }

    /** 取消个人报名（不追回已发的参赛分） */
    @DeleteMapping("/{id}/join-individual")
    public ApiResult<ContestVO> cancelIndividual(@PathVariable Long id,
                                                @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.cancelIndividualJoin(id, me));
    }

    /** 同步官方榜单（Codeforces 可自动；其他平台无公开接口，回落手动录入） */
    @PostMapping("/{id}/sync-standings")
    public ApiResult<ContestVO> syncStandings(@PathVariable Long id,
                                             @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.syncStandings(id, me));
    }

    /** 比赛记录：个人 + 本队，支持时间范围（7d/1m/1y/all）与平台过滤 */
    @GetMapping("/records")
    public ApiResult<List<ContestVO>> records(
            @RequestParam(defaultValue = "all") String range,
            @RequestParam(required = false) String platform,
            @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.records(me, range, platform));
    }

    /** 取消参赛（不追回已发的参赛分） */
    @DeleteMapping("/{id}/participation/{teamId}")
    public ApiResult<ContestVO> cancel(@PathVariable Long id,
                                      @PathVariable Long teamId,
                                      @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.cancelParticipation(id, teamId, me));
    }

    /** 提交比赛日志。队长/站主直接确认并发名次分；普通队员提交进待审核 */
    @PostMapping("/{id}/logs")
    public ApiResult<ContestVO> submitLog(@PathVariable Long id,
                                         @Valid @RequestBody ContestLogRequest req,
                                         @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.submitLog(id, req, me));
    }

    /** 本队队长确认日志 → 发名次分 */
    @PostMapping("/{id}/logs/{logId}/confirm")
    public ApiResult<ContestVO> confirmLog(@PathVariable Long id,
                                          @PathVariable Long logId,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.confirmLog(id, logId, me));
    }

    @Data
    public static class TeamIdsRequest {
        private List<Long> teamIds;
    }
}
