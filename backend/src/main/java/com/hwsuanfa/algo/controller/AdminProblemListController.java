package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.dto.ProblemImportRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.ProblemListService;
import com.hwsuanfa.algo.vo.ProblemListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 题单管理 —— 站主后台。
 *
 * 复用 ProblemListService 的同一套逻辑，只是入口不同；
 * 站主的权限在 service 里已经放开（isSiteOwner 全权）。
 */
@RestController
@RequestMapping("/api/admin/problem-lists")
@RequiredArgsConstructor
public class AdminProblemListController {

    private final ProblemListService problemListService;

    @GetMapping
    public ApiResult<List<ProblemListVO>> list(@RequestParam(required = false) String scope) {
        return ApiResult.ok(problemListService.adminList(scope));
    }

    @GetMapping("/{id}")
    public ApiResult<ProblemListVO> detail(@PathVariable Long id,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.detail(id, me));
    }

    @PostMapping("/{id}/problems")
    public ApiResult<ProblemListVO> importProblems(@PathVariable Long id,
                                                   @RequestBody ProblemImportRequest req,
                                                   @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.importProblems(id, req, me));
    }

    @DeleteMapping("/{id}/problems/{problemId}")
    public ApiResult<ProblemListVO> removeProblem(@PathVariable Long id,
                                                 @PathVariable Long problemId,
                                                 @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.removeProblem(id, problemId, me));
    }

    /** 批量通过待审核的队伍题单，省得一个个点 */
    @PostMapping("/audit-batch")
    public ApiResult<Map<String, Integer>> auditBatch(@RequestBody BatchAuditRequest req,
                                                     @AuthenticationPrincipal LoginUser me) {
        int ok = 0;
        int failed = 0;
        for (Long id : req.getIds()) {
            try {
                problemListService.audit(id, req.isApprove(), req.getRejectReason(), me);
                ok++;
            } catch (RuntimeException e) {
                failed++;
            }
        }
        return ApiResult.ok(Map.of("approved", ok, "failed", failed));
    }

    @PutMapping("/order/{id}")
    public ApiResult<ProblemListVO> reorder(@PathVariable Long id,
                                           @RequestBody ReorderRequest req,
                                           @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.reorder(id, req.getProblemIds(), me));
    }

    @lombok.Data
    public static class BatchAuditRequest {
        private List<Long> ids;
        private boolean approve;
        private String rejectReason;
    }

    @lombok.Data
    public static class ReorderRequest {
        private List<Long> problemIds;
    }
}
