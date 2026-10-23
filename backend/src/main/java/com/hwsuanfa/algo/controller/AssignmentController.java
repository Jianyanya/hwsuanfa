package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AssignmentService;
import com.hwsuanfa.algo.vo.AssignmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/**
 * 作业 —— 面向队员（含队长）。
 *
 * 可见性由服务端按「作业目标队伍快照」过滤：队员只会拿到指派给自己所在队伍的作业。
 */
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    /** 我的作业。type 可选：TEAM / GLOBAL / DAILY / ALL */
    @GetMapping
    public ApiResult<List<AssignmentVO>> myAssignments(
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.myAssignments(me, type));
    }

    @GetMapping("/{id}")
    public ApiResult<AssignmentVO> detail(@PathVariable Long id,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.detail(id, me));
    }

    /**
     * 标记作业里的某道题完成。
     *
     * 逾期后仍可标记，是否给分由该作业的 allowLateReward 决定；
     * 同一道题的首次完成积分与同一作业的完成奖励都只发一次（服务端幂等）。
     */
    /** 站主：贴链接批量导入题目 */
    @PostMapping("/{id}/problems/import")
    public ApiResult<AssignmentVO> importProblems(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body,
                                                 @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.importProblems(
                id, body.getOrDefault("text", ""), body.get("defaultPlatform"), me));
    }

    @PostMapping("/{id}/problems/{problemId}/done")
    public ApiResult<AssignmentVO> markDone(@PathVariable Long id,
                                           @PathVariable Long problemId,
                                           @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.markProblemDone(id, problemId, me));
    }

    /**
     * 结算作业。
     *
     * 用途：作业里的题在别处已经做过时，每题都显示「已完成」而没有按钮可按，
     * 作业会一直停在未结算状态。这个接口负责把它正式结算并发放奖励。
     * 要求所有题目都已完成，且重复调用安全（幂等键保证不重复发分）。
     */
    @PostMapping("/{id}/complete")
    public ApiResult<AssignmentVO> complete(@PathVariable Long id,
                                           @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.completeAssignment(id, me));
    }
}
