package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.service.RankService;
import com.hwsuanfa.algo.vo.RankVO;
import com.hwsuanfa.algo.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分排行榜。
 * 登录即可看（队内激励公开）；完全私密的成员由后端过滤不上榜。
 */
@RestController
@RequestMapping("/api/rank")
@RequiredArgsConstructor
public class RankController {

    private final RankService rankService;

    @GetMapping
    public ApiResult<RankVO> leaderboard(@AuthenticationPrincipal LoginUser me) {
        RankVO rank = rankService.leaderboard();
        if (me == null || !me.isSiteOwner()) {
            rank.getUsers().stream()
                    .filter(row -> me == null || !me.getId().equals(row.getUserId()))
                    .forEach(row -> row.setClassName(null));
        }
        return ApiResult.ok(rank);
    }
}
