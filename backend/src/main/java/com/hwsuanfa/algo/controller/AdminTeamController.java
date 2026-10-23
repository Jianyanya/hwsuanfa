package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.dto.AddMemberRequest;
import com.hwsuanfa.algo.dto.ChangeLeaderRequest;
import com.hwsuanfa.algo.dto.RemoveMemberRequest;
import com.hwsuanfa.algo.dto.TeamCreateRequest;
import com.hwsuanfa.algo.dto.TeamUpdateRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.TeamService;
import com.hwsuanfa.algo.vo.TeamBriefVO;
import com.hwsuanfa.algo.vo.TeamVO;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 队伍管理 —— 站主后台专用。
 *
 * 路径前缀 /api/admin/** 已在 SecurityConfig 里限定 hasRole("SITE_OWNER")，
 * 所以这里不需要再逐个方法判权限；但**仍然**在 Service 层校验业务规则。
 */
@RestController
@RequestMapping("/api/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private final TeamService teamService;

    /* ---------------- 队伍 ---------------- */

    @GetMapping
    public ApiResult<List<TeamBriefVO>> list() {
        return ApiResult.ok(teamService.listAll());
    }

    @GetMapping("/{teamId}")
    public ApiResult<TeamVO> detail(@PathVariable Long teamId,
                                    @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(teamService.detail(teamId, me));
    }

    @PostMapping
    public ApiResult<TeamVO> create(@Valid @RequestBody TeamCreateRequest req,
                                    @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(teamService.create(req, me));
    }

    @PutMapping("/{teamId}")
    public ApiResult<TeamVO> update(@PathVariable Long teamId,
                                    @Valid @RequestBody TeamUpdateRequest req,
                                    @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(teamService.update(teamId, req, me));
    }

    @DeleteMapping("/{teamId}")
    public ApiResult<Void> dissolve(@PathVariable Long teamId,
                                    @AuthenticationPrincipal LoginUser me) {
        teamService.dissolve(teamId, me);
        return ApiResult.ok();
    }

    /* ---------------- 冻结 / 解冻 ---------------- */

    @PostMapping("/{teamId}/freeze")
    public ApiResult<Void> freeze(@PathVariable Long teamId,
                                  @RequestBody(required = false) FreezeRequest body,
                                  @AuthenticationPrincipal LoginUser me) {
        teamService.freeze(teamId, body == null ? null : body.getReason(), me);
        return ApiResult.ok();
    }

    @PostMapping("/{teamId}/unfreeze")
    public ApiResult<Void> unfreeze(@PathVariable Long teamId,
                                    @AuthenticationPrincipal LoginUser me) {
        teamService.unfreeze(teamId, me);
        return ApiResult.ok();
    }

    /* ---------------- 成员 ---------------- */

    @PostMapping("/{teamId}/members")
    public ApiResult<TeamVO> addMember(@PathVariable Long teamId,
                                       @Valid @RequestBody AddMemberRequest req,
                                       @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(teamService.addMember(teamId, req, me));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ApiResult<TeamVO> removeMember(@PathVariable Long teamId,
                                          @PathVariable Long userId,
                                          @RequestBody(required = false) RemoveMemberRequest body,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(teamService.removeMember(
                teamId, userId, body == null ? null : body.getNewLeaderId(), me));
    }

    /* ---------------- 队长 ---------------- */

    @PutMapping("/{teamId}/leader")
    public ApiResult<TeamVO> changeLeader(@PathVariable Long teamId,
                                          @Valid @RequestBody ChangeLeaderRequest req,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(teamService.changeLeader(teamId, req.getNewLeaderId(), me));
    }

    /* ---------------- 用户检索见 AdminUserController (/api/admin/users) ---------------- */

    @Data
    public static class FreezeRequest {
        private String reason;
    }
}
