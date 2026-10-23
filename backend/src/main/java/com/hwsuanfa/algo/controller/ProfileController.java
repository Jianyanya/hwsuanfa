package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.dto.HandleBindRequest;
import com.hwsuanfa.algo.dto.PasswordChangeRequest;
import com.hwsuanfa.algo.dto.ProfileUpdateRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.ProfileService;
import com.hwsuanfa.algo.vo.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人资料。
 *
 * OJ 账号绑定只收 handle（用户名），不收密码 —— 见 HandleBindRequest 的说明。
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ApiResult<ProfileVO> me(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(profileService.myProfile(me.getId()));
    }

    @PutMapping("/me")
    public ApiResult<ProfileVO> updateMe(@Valid @RequestBody ProfileUpdateRequest req,
                                        @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(profileService.updateProfile(me.getId(), req));
    }

    @PutMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody PasswordChangeRequest req,
                                          @AuthenticationPrincipal LoginUser me) {
        profileService.changePassword(me.getId(), req.getOldPassword(), req.getNewPassword());
        return ApiResult.ok();
    }

    /** 绑定各平台 OJ 账号（handle） */
    @PutMapping("/handles")
    public ApiResult<ProfileVO> bindHandles(@Valid @RequestBody HandleBindRequest req,
                                           @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(profileService.bindHandles(me.getId(), req));
    }

    /**
     * 在线校验某平台用户名是否存在。
     * 能力随平台不同：CF/AtCoder 真实校验，LeetCode 尽力，牛客无法校验。
     */
    @GetMapping("/verify-handle")
    public ApiResult<ProfileService.CfVerifyResult> verifyHandle(
            @RequestParam String platform, @RequestParam String handle) {
        return ApiResult.ok(profileService.verifyHandle(platform, handle));
    }
}
