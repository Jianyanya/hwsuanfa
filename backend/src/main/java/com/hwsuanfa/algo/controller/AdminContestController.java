package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.dto.ContestRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.ContestService;
import com.hwsuanfa.algo.vo.ContestVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 比赛管理 —— 站主后台。比赛是手动登记的，不提供任何抓取入口 */
@RestController
@RequestMapping("/api/admin/contests")
@RequiredArgsConstructor
public class AdminContestController {

    private final ContestService contestService;

    @GetMapping
    public ApiResult<List<ContestVO>> list() {
        return ApiResult.ok(contestService.adminList());
    }

    @GetMapping("/{id}")
    public ApiResult<ContestVO> detail(@PathVariable Long id,
                                      @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.detail(id, me));
    }

    @PostMapping
    public ApiResult<ContestVO> create(@Valid @RequestBody ContestRequest req,
                                      @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.create(req, me));
    }

    @PutMapping("/{id}")
    public ApiResult<ContestVO> update(@PathVariable Long id,
                                      @Valid @RequestBody ContestRequest req,
                                      @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(contestService.update(id, req, me));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id,
                                  @AuthenticationPrincipal LoginUser me) {
        contestService.delete(id, me);
        return ApiResult.ok();
    }
}
