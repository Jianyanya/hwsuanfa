package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AssignmentService;
import com.hwsuanfa.algo.vo.AssignmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 每日一题 —— 队员视角。
 *
 * 「今天发布了吗」「我今天完成了吗」都由服务端按**服务器时间**判定，
 * 不依赖客户端时钟。
 */
@RestController
@RequestMapping("/api/daily")
@RequiredArgsConstructor
public class DailyController {

    private final AssignmentService assignmentService;

    /** 今日一题；尚未发布时 data 为 null（前端显示「今天还没有出题」） */
    @GetMapping("/today")
    public ApiResult<AssignmentVO> today(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.todayDaily(me));
    }

    /** 历史每日一题，按发布日期倒序 */
    @GetMapping("/history")
    public ApiResult<List<AssignmentVO>> history(
            @RequestParam(defaultValue = "30") int limit,
            @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.dailyHistory(me, limit));
    }
}
