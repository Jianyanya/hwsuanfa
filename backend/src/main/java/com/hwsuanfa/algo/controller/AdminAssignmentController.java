package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.dto.AssignmentRequest;
import com.hwsuanfa.algo.dto.ProblemRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AssignmentService;
import com.hwsuanfa.algo.service.ProblemService;
import com.hwsuanfa.algo.vo.AssignmentVO;
import com.hwsuanfa.algo.vo.ProblemVO;
import jakarta.validation.Valid;
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

/**
 * 作业与题目管理 —— 站主后台专用。
 * 路径前缀 /api/admin/** 已在 SecurityConfig 限定 hasRole("SITE_OWNER")。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAssignmentController {

    private final AssignmentService assignmentService;
    private final ProblemService problemService;

    /* ---------------- 作业 ---------------- */

    @GetMapping("/assignments")
    public ApiResult<List<AssignmentVO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "true") boolean withDetail) {
        return ApiResult.ok(assignmentService.adminList(type, withDetail));
    }

    @GetMapping("/assignments/{id}")
    public ApiResult<AssignmentVO> detail(@PathVariable Long id,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.adminDetail(id, me));
    }

    @PostMapping("/assignments")
    public ApiResult<AssignmentVO> create(@Valid @RequestBody AssignmentRequest req,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.create(req, me));
    }

    @PutMapping("/assignments/{id}")
    public ApiResult<AssignmentVO> update(@PathVariable Long id,
                                          @Valid @RequestBody AssignmentRequest req,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(assignmentService.update(id, req, me));
    }

    @DeleteMapping("/assignments/{id}")
    public ApiResult<Void> delete(@PathVariable Long id,
                                  @AuthenticationPrincipal LoginUser me) {
        assignmentService.delete(id, me);
        return ApiResult.ok();
    }

    /* ---------------- 题目 ---------------- */

    @GetMapping("/problems")
    public ApiResult<List<ProblemVO>> searchProblems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(problemService.search(keyword, platform, limit));
    }

    @GetMapping("/problems/{id}")
    public ApiResult<ProblemVO> problem(@PathVariable Long id) {
        return ApiResult.ok(problemService.detail(id));
    }

    @PostMapping("/problems")
    public ApiResult<ProblemVO> createProblem(@Valid @RequestBody ProblemRequest req,
                                              @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemService.create(req, me));
    }

    @PutMapping("/problems/{id}")
    public ApiResult<ProblemVO> updateProblem(@PathVariable Long id,
                                              @Valid @RequestBody ProblemRequest req,
                                              @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemService.update(id, req, me));
    }

    @DeleteMapping("/problems/{id}")
    public ApiResult<Void> deleteProblem(@PathVariable Long id) {
        problemService.delete(id);
        return ApiResult.ok();
    }
}
