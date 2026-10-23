package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.security.TokenService;
import com.hwsuanfa.algo.security.PasswordPolicy;
import com.hwsuanfa.algo.service.AuditLogService;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.Map;

/** 站主管理成员：查看列表 / 修改资料 / 重置密码（防止乱设置，最终解释权在站主） */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final TokenService tokenService;

    /** 成员列表（不含密码） */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(@AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) throw new BizException(ErrorCode.FORBIDDEN);
        return ApiResult.ok(userMapper.selectList(new LambdaQueryWrapper<User>()
                        .orderByAsc(User::getId)).stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "nickname", u.getNickname() == null ? "" : u.getNickname(),
                        "realName", u.getRealName() == null ? "" : u.getRealName(),
                        "className", u.getClassName() == null ? "" : u.getClassName(),
                        "role", u.getRole(),
                        "status", u.getStatus(),
                        "cfHandle", u.getCfHandle() == null ? "" : u.getCfHandle(),
                        "atcoderHandle", u.getAtcoderHandle() == null ? "" : u.getAtcoderHandle(),
                        "luoguHandle", u.getLuoguHandle() == null ? "" : u.getLuoguHandle()))
                .toList());
    }

    @Data
    public static class AdminUserUpdateRequest {
        @Size(max = 32)
        private String nickname;

        @Size(max = 32)
        private String realName;

        @Size(max = 64)
        private String className;

        /** ACTIVE / DISABLED */
        @Pattern(regexp = "ACTIVE|DISABLED", message = "账号状态不合法")
        private String status;

        @Size(max = 64)
        private String cfHandle;
        @Size(max = 64)
        private String atcoderHandle;
        @Size(max = 64)
        private String luoguHandle;

        /** 非空则重置为该密码 */
        @Size(min = 8, max = 64, message = "新密码长度 8-64 位")
        private String newPassword;
    }

    /** 站主：修改成员资料 / 停用启用 / 重置密码 */
    @PutMapping("/{id}")
    @Transactional
    public ApiResult<Void> update(@PathVariable Long id,
                                  @Valid @RequestBody AdminUserUpdateRequest req,
                                  @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) throw new BizException(ErrorCode.FORBIDDEN);
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND, "成员不存在");
        if (id.equals(me.getId()) && "DISABLED".equals(req.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能停用自己");
        }

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> w = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>().eq(User::getId, id);
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            String nick = req.getNickname().trim();
            Long taken = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getNickname, nick).ne(User::getId, id));
            if (taken > 0) throw new BizException(ErrorCode.CONFLICT, "该昵称已被使用");
            w.set(User::getNickname, nick);
        }
        if (req.getRealName() != null) w.set(User::getRealName, req.getRealName().trim());
        if (req.getClassName() != null) w.set(User::getClassName, req.getClassName().trim());
        if (req.getStatus() != null && Set.of("ACTIVE", "DISABLED").contains(req.getStatus())) {
            w.set(User::getStatus, req.getStatus());
        }
        if (req.getCfHandle() != null) w.set(User::getCfHandle, blankToNull(req.getCfHandle()));
        if (req.getAtcoderHandle() != null) w.set(User::getAtcoderHandle, blankToNull(req.getAtcoderHandle()));
        if (req.getLuoguHandle() != null) w.set(User::getLuoguHandle, blankToNull(req.getLuoguHandle()));
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            PasswordPolicy.validate(req.getNewPassword());
            w.set(User::getPassword, passwordEncoder.encode(req.getNewPassword()));
        }
        if (w.getSqlSet() == null || w.getSqlSet().isBlank()) {
            return ApiResult.ok();
        }
        try {
            userMapper.update(null, w);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "昵称或平台账号已被其他成员使用");
        }
        if ((req.getNewPassword() != null && !req.getNewPassword().isBlank())
                || "DISABLED".equals(req.getStatus())) {
            tokenService.invalidateSessions(id);
        }

        auditLogService.record(me, "ADMIN_UPDATE_USER", "USER", id, null, null,
                "站主修改成员资料：" + u.getUsername());
        return ApiResult.ok();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
