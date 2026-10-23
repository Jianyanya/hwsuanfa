package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.TeamService;
import com.hwsuanfa.algo.vo.TeamBriefVO;
import com.hwsuanfa.algo.vo.TeamVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 队伍 —— 面向普通用户（含队长）。
 *
 * 本阶段队长对本队是只读的：成员增减与队长变更都归站主，
 * 见 /api/admin/teams（AdminTeamController）。
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final TeamMemberMapper teamMemberMapper;

    /** 我所属的队伍列表（侧栏「当前队伍」与切换器用） */
    @GetMapping
    public ApiResult<List<TeamBriefVO>> myTeams(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(teamService.myTeams(me));
    }

    /**
     * 队伍详情。
     *
     * 权限：站主可看任意队伍；其他人**只能看自己所属的队伍**。
     * 非成员的访问返回 403，且由服务端裁剪掉敏感字段（真实姓名/学号）。
     */
    @GetMapping("/{teamId}")
    public ApiResult<TeamVO> detail(@PathVariable Long teamId,
                                    @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner() && !isMember(teamId, me.getId())) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        return ApiResult.ok(teamService.detail(teamId, me));
    }

    private boolean isMember(Long teamId, Long userId) {
        return teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, "ACTIVE")) > 0;
    }
}
