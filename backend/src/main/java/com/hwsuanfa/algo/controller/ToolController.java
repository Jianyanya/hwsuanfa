package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.FriendlyLink;
import com.hwsuanfa.algo.entity.ShortLink;
import com.hwsuanfa.algo.mapper.FriendlyLinkMapper;
import com.hwsuanfa.algo.mapper.ShortLinkMapper;
import com.hwsuanfa.algo.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工具：粘贴板（短链分享）+ 友情链接。
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ShortLinkMapper shortLinkMapper;
    private final FriendlyLinkMapper friendlyLinkMapper;

    /* ---------- 粘贴板 ---------- */

    @Data
    public static class PasteRequest {
        @NotBlank(message = "内容不能为空")
        private String content;

        @Size(max = 128, message = "标题过长")
        private String title;

        /** TEXT / CODE */
        private String contentType = "TEXT";

        @Size(max = 32)
        private String language;

        @Min(value = 1, message = "有效天数至少为 1")
        @Max(value = 3650, message = "有效天数不能超过 3650")
        private Integer expireDays;
    }

    /** 创建粘贴（登录即可，限长 64KB） */
    @PostMapping("/pastes")
    public ApiResult<Map<String, Object>> createPaste(@Valid @RequestBody PasteRequest req,
                                                     @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        if (req.getContent().getBytes(StandardCharsets.UTF_8).length > 65536) {
            throw new BizException(ErrorCode.BAD_REQUEST, "内容过长（上限 64KB）");
        }
        ShortLink link = new ShortLink();
        link.setCode(genCode());
        link.setTitle(req.getTitle() == null ? null : req.getTitle().trim());
        link.setContentType("CODE".equals(req.getContentType()) ? "CODE" : "TEXT");
        link.setContent(req.getContent());
        link.setLanguage(req.getLanguage());
        link.setCreatedBy(me.getId());
        if (req.getExpireDays() != null && req.getExpireDays() > 0) {
            link.setExpiresAt(LocalDateTime.now().plusDays(req.getExpireDays()));
        }
        shortLinkMapper.insert(link);
        return ApiResult.ok(Map.of("code", link.getCode()));
    }

    /** 查看粘贴（无需登录——分享出去别人点开就得能看）；过期 410 */
    @GetMapping("/pastes/{code}")
    public ApiResult<ShortLink> viewPaste(@PathVariable String code) {
        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getCode, code));
        if (link == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "粘贴不存在或已被删除");
        }
        if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.GONE, "该粘贴已过期");
        }
        shortLinkMapper.incrementViewCount(link.getId());
        return ApiResult.ok(shortLinkMapper.selectById(link.getId()));
    }

    /** 我的粘贴列表 */
    @GetMapping("/pastes/mine")
    public ApiResult<List<ShortLink>> myPastes(@AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        return ApiResult.ok(shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getCreatedBy, me.getId())
                .orderByDesc(ShortLink::getId)));
    }

    /** 删除我的粘贴 */
    @DeleteMapping("/pastes/{code}")
    public ApiResult<Void> deletePaste(@PathVariable String code,
                                      @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getCode, code));
        if (link == null) {
            return ApiResult.ok();
        }
        if (!me.isSiteOwner() && !me.getId().equals(link.getCreatedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        shortLinkMapper.deleteById(link.getId());
        return ApiResult.ok();
    }

    /** 8 位短码（去掉易混淆字符的字母表） */
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String genCode() {
        for (int i = 0; i < 5; i++) {
            StringBuilder sb = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (shortLinkMapper.selectCount(new LambdaQueryWrapper<ShortLink>()
                    .eq(ShortLink::getCode, code)) == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.CONFLICT, "短码生成失败，请重试");
    }

    /* ---------- 友情链接 ---------- */

    /** 公开：全部友链（按 sort 排序） */
    @GetMapping("/links")
    public ApiResult<List<FriendlyLink>> links() {
        return ApiResult.ok(friendlyLinkMapper.selectList(
                new LambdaQueryWrapper<FriendlyLink>().orderByAsc(FriendlyLink::getSortOrder)));
    }

    @Data
    public static class LinkRequest {
        @NotBlank(message = "名称不能为空")
        @Size(max = 64)
        private String name;

        @NotBlank(message = "链接不能为空")
        @Size(max = 500)
        private String url;

        @Size(max = 500)
        private String logo;

        @Size(max = 200)
        private String description;

        private Integer sortOrder;
    }

    /** 站主：新增 / 修改友链 */
    @PostMapping("/links")
    public ApiResult<FriendlyLink> saveLink(@Valid @RequestBody LinkRequest req,
                                           @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        if (!me.isSiteOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        FriendlyLink link = new FriendlyLink();
        link.setName(req.getName().trim());
        link.setUrl(httpUrl(req.getUrl()));
        link.setLogo(trimOrNull(req.getLogo()));
        link.setDescription(trimOrNull(req.getDescription()));
        link.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        friendlyLinkMapper.insert(link);
        return ApiResult.ok(link);
    }

    @PutMapping("/links/{id}")
    public ApiResult<FriendlyLink> updateLink(@PathVariable Long id,
                                             @Valid @RequestBody LinkRequest req,
                                             @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        if (!me.isSiteOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        FriendlyLink link = friendlyLinkMapper.selectById(id);
        if (link == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "友链不存在");
        }
        FriendlyLink patch = new FriendlyLink();
        patch.setId(id);
        patch.setName(req.getName().trim());
        patch.setUrl(httpUrl(req.getUrl()));
        patch.setLogo(trimOrNull(req.getLogo()));
        patch.setDescription(trimOrNull(req.getDescription()));
        patch.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        friendlyLinkMapper.update(null, new LambdaUpdateWrapper<FriendlyLink>()
                .eq(FriendlyLink::getId, id)
                .set(FriendlyLink::getName, patch.getName())
                .set(FriendlyLink::getUrl, patch.getUrl())
                .set(FriendlyLink::getLogo, patch.getLogo())
                .set(FriendlyLink::getDescription, patch.getDescription())
                .set(FriendlyLink::getSortOrder, patch.getSortOrder()));
        return ApiResult.ok(friendlyLinkMapper.selectById(id));
    }

    @DeleteMapping("/links/{id}")
    public ApiResult<Void> deleteLink(@PathVariable Long id,
                                     @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        if (!me.isSiteOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        friendlyLinkMapper.deleteById(id);
        return ApiResult.ok();
    }

    private static void requireLogin(LoginUser me) {
        if (me == null) throw BizException.of(ErrorCode.UNAUTHORIZED);
    }

    private static String httpUrl(String value) {
        try {
            String url = value.trim();
            URI uri = URI.create(url);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) return url;
        } catch (IllegalArgumentException ignored) {}
        throw BizException.of(ErrorCode.BAD_REQUEST, "链接必须是有效的 HTTP 或 HTTPS 地址");
    }

    private static String trimOrNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
