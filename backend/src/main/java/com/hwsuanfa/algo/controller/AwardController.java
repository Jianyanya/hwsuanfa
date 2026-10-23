package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.dto.AwardRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AwardService;
import com.hwsuanfa.algo.vo.AwardVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 获奖记录：队员提交 → 站主审核 → 公示到个人页 */
@RestController
@RequestMapping("/api/awards")
@RequiredArgsConstructor
public class AwardController {

    private final AwardService awardService;

    /** 提交获奖记录（待审核） */
    @PostMapping
    public ApiResult<AwardVO> submit(@Valid @RequestBody AwardRequest req,
                                    @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(awardService.submit(me, req));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ApiResult<AwardVO> update(@PathVariable Long id, @Valid @RequestBody AwardRequest req,
                                     @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(awardService.update(id, me, req));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LoginUser me) {
        awardService.delete(id, me);
        return ApiResult.ok();
    }

    /** 我的获奖记录（含审核状态） */
    @GetMapping("/mine")
    public ApiResult<List<AwardVO>> mine(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(awardService.mine(me.getId()));
    }

    /** 站主：按状态查看（ALL/PENDING/APPROVED/REJECTED） */
    @GetMapping("/admin")
    public ApiResult<List<AwardVO>> adminList(
            @RequestParam(defaultValue = "PENDING") String status,
            @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) {
            throw new com.hwsuanfa.algo.common.BizException(
                    com.hwsuanfa.algo.common.ErrorCode.FORBIDDEN);
        }
        return ApiResult.ok(awardService.adminList(status));
    }

    /** 站主：审核（通过 / 驳回+原因） */
    @PostMapping("/admin/{id}/audit")
    public ApiResult<AwardVO> audit(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) {
            throw new com.hwsuanfa.algo.common.BizException(
                    com.hwsuanfa.algo.common.ErrorCode.FORBIDDEN);
        }
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String reason = body.get("rejectReason") == null ? null
                : body.get("rejectReason").toString();
        return ApiResult.ok(awardService.audit(id, approved, reason, me));
    }

    @org.springframework.web.bind.annotation.PostMapping(value = "/{id}/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<AwardVO> uploadCertificate(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal LoginUser me) {
        awardService.uploadCertificate(id, file, me);
        return ApiResult.ok(awardService.mine(me.getId()).stream().filter(a -> id.equals(a.getId())).findFirst().orElse(null));
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}/certificate")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long id,
                                                      @AuthenticationPrincipal LoginUser me) {
        var certificate = awardService.getCertificate(id, me);
        if (certificate == null) return ResponseEntity.notFound().build();
        MediaType type;
        try { type = MediaType.parseMediaType(certificate.getContentType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                        java.net.URLEncoder.encode(certificate.getFileName(), java.nio.charset.StandardCharsets.UTF_8))
                .body(certificate.getContent());
    }
}
