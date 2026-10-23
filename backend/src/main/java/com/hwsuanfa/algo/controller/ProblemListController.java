package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.ProblemRefParser;
import com.hwsuanfa.algo.dto.ProblemImportRequest;
import com.hwsuanfa.algo.dto.ProblemListRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.ProblemListService;
import com.hwsuanfa.algo.vo.ProblemListVO;
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
 * 题单 —— 面向队员与队长。
 *
 * 队伍题单：队长和站主可直接改；普通队员新建的进待审核。
 * 个人题单：本人管理。
 */
@RestController
@RequestMapping("/api/problem-lists")
@RequiredArgsConstructor
public class ProblemListController {

    private final ProblemListService problemListService;

    /** 我能看到的题单：我的个人题单 + 我所在队伍的题单 */
    @GetMapping
    public ApiResult<List<ProblemListVO>> myLists(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.myLists(me));
    }

    @GetMapping("/{id}")
    public ApiResult<ProblemListVO> detail(@PathVariable Long id,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.detail(id, me));
    }

    @PostMapping
    public ApiResult<ProblemListVO> create(@Valid @RequestBody ProblemListRequest req,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.create(req, me));
    }

    @PutMapping("/{id}")
    public ApiResult<ProblemListVO> update(@PathVariable Long id,
                                          @Valid @RequestBody ProblemListRequest req,
                                          @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.update(id, req, me));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id,
                                  @AuthenticationPrincipal LoginUser me) {
        problemListService.delete(id, me);
        return ApiResult.ok();
    }

    /* ---------------- 题目 ---------------- */

    /** 批量加题：给 id 或直接粘文本都行 */
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

    @PutMapping("/{id}/order")
    public ApiResult<ProblemListVO> reorder(@PathVariable Long id,
                                           @RequestBody ReorderRequest req,
                                           @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.reorder(id, req.getProblemIds(), me));
    }

    /** 审核队员提交的队伍题单（队长或站主） */
    @PostMapping("/{id}/audit")
    public ApiResult<ProblemListVO> audit(@PathVariable Long id,
                                         @RequestBody AuditRequest req,
                                         @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(problemListService.audit(id, req.isApprove(), req.getRejectReason(), me));
    }

    /* ---------------- 粘贴预览 ---------------- */

    /**
     * 只解析不落库：粘贴后先让用户看看能识别出什么、哪几行没认出来。
     * 放在这里而不是 admin 下，因为队长录题单时也要用。
     */
    @PostMapping("/parse-preview")
    public ApiResult<ParsePreviewVO> parsePreview(@RequestBody ParsePreviewRequest req) {
        ProblemRefParser.Result r = problemListService.previewImport(req.getText(), req.getDefaultPlatform());
        ParsePreviewVO vo = new ParsePreviewVO();
        vo.setProblems(r.problems().stream().map(p -> {
            Item i = new Item();
            i.setPlatform(p.platform());
            i.setProblemNo(p.problemNo());
            i.setUrl(p.url());
            return i;
        }).toList());
        vo.setUnparsed(r.unparsed());
        vo.setDuplicateCount(r.duplicateCount());
        return ApiResult.ok(vo);
    }

    @Data
    public static class ReorderRequest {
        private List<Long> problemIds;
    }

    @Data
    public static class AuditRequest {
        private boolean approve;
        private String rejectReason;
    }

    @Data
    public static class ParsePreviewRequest {
        private String text;
        private String defaultPlatform;
    }

    @Data
    public static class ParsePreviewVO {
        private List<Item> problems;
        private List<String> unparsed;
        private int duplicateCount;
    }

    @Data
    public static class Item {
        private String platform;
        private String problemNo;
        private String url;
    }
}
