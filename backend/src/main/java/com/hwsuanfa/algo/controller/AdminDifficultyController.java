package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.entity.DifficultyScore;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.DifficultyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 难度分档配置 —— 站主后台。
 *
 * 分值属于教学策略，放在后台可改，改完立刻对新产生的积分事件生效
 * （**不追溯**已发放的历史事件 —— 那属于积分修正，要用反向事件做）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDifficultyController {

    private final DifficultyService difficultyService;

    /** 所有平台的档位，按平台分组（后台配置页用） */
    @GetMapping("/difficulty-tiers")
    public ApiResult<Map<String, List<DifficultyScore>>> all() {
        return ApiResult.ok(difficultyService.allTiers());
    }

    /** 某个平台的档位，按由易到难排序（录入题目时给下拉用） */
    @GetMapping("/difficulty-tiers/{platform}")
    public ApiResult<List<DifficultyScore>> byPlatform(@PathVariable String platform) {
        return ApiResult.ok(difficultyService.tiersOf(platform));
    }

    /**
     * 整组替换某平台的档位。
     * 采用「整组替换」而不是逐条增删，前端可以拖拽排序后一次保存，
     * 不用担心增量 diff 的边界情况。
     */
    @PutMapping("/difficulty-tiers/{platform}")
    public ApiResult<Void> replace(@PathVariable String platform,
                                   @RequestBody TierRequest body,
                                   @AuthenticationPrincipal LoginUser me) {
        difficultyService.replaceTiers(platform, body.getTiers(), me.getId());
        return ApiResult.ok();
    }

    @Data
    public static class TierRequest {
        private List<DifficultyScore> tiers;
    }
}
