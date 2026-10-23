package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Announcement;
import com.hwsuanfa.algo.mapper.AnnouncementMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 公告：全站公告所有人可看；发布/编辑/删除仅站主 */
@RestController
@RequestMapping("/api/bulletin")
@RequiredArgsConstructor
public class BulletinController {

    private final AnnouncementMapper announcementMapper;
    private final UserMapper userMapper;
    private final ObjectProvider<NotificationService> notificationService;

    @Data
    public static class BulletinRequest {
        @NotBlank(message = "标题不能为空")
        @Size(max = 200)
        private String title;

        @NotBlank(message = "内容不能为空")
        private String content;

        private Boolean pinned;
        private Boolean published;
    }

    /** 公开：已发布的全站公告（置顶优先，新在前） */
    @GetMapping
    public ApiResult<List<Announcement>> list() {
        return ApiResult.ok(announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getScope, "GLOBAL")
                .eq(Announcement::getPublished, true)
                .orderByDesc(Announcement::getPinned)
                .orderByDesc(Announcement::getCreatedAt)));
    }

    /** 公开：公告详情 */
    @GetMapping("/{id}")
    public ApiResult<Announcement> detail(@PathVariable Long id) {
        Announcement a = announcementMapper.selectById(id);
        if (a == null || !"GLOBAL".equals(a.getScope()) || !Boolean.TRUE.equals(a.getPublished())) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return ApiResult.ok(a);
    }

    /** 站主：全部公告（含未发布草稿） */
    @GetMapping("/admin")
    public ApiResult<List<Announcement>> adminList(@AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) throw new BizException(ErrorCode.FORBIDDEN);
        return ApiResult.ok(announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getPinned)
                .orderByDesc(Announcement::getCreatedAt)));
    }

    /** 站主：发布公告 */
    @PostMapping
    public ApiResult<Announcement> create(@Valid @RequestBody BulletinRequest req,
                                         @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) throw new BizException(ErrorCode.FORBIDDEN);
        Announcement a = new Announcement();
        a.setScope("GLOBAL");
        a.setTitle(req.getTitle().trim());
        a.setContent(req.getContent());
        a.setPinned(Boolean.TRUE.equals(req.getPinned()));
        a.setPublished(req.getPublished() == null || req.getPublished());
        a.setCreatedBy(me.getId());
        announcementMapper.insert(a);
        if (Boolean.TRUE.equals(a.getPublished())) {
            notifyAllMembers(a, me.getId());
        }
        return ApiResult.ok(a);
    }

    /** 站主：编辑 / 置顶 / 发布切换 */
    @PutMapping("/{id}")
    public ApiResult<Announcement> update(@PathVariable Long id, @Valid @RequestBody BulletinRequest req,
                                         @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) throw new BizException(ErrorCode.FORBIDDEN);
        Announcement a = announcementMapper.selectById(id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        Announcement patch = new Announcement();
        patch.setId(id);
        patch.setTitle(req.getTitle().trim());
        patch.setContent(req.getContent());
        patch.setPinned(Boolean.TRUE.equals(req.getPinned()));
        patch.setPublished(req.getPublished() == null || req.getPublished());
        announcementMapper.updateById(patch);
        Announcement updated = announcementMapper.selectById(id);
        if (Boolean.TRUE.equals(updated.getPublished()) && !Boolean.TRUE.equals(a.getPublished())) {
            notifyAllMembers(updated, me.getId());
        }
        return ApiResult.ok(updated);
    }

    /** 站主：删除公告 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) throw new BizException(ErrorCode.FORBIDDEN);
        announcementMapper.deleteById(id);
        return ApiResult.ok();
    }

    private void notifyAllMembers(Announcement announcement, Long operatorId) {
        userMapper.selectList(new LambdaQueryWrapper<com.hwsuanfa.algo.entity.User>()
                        .eq(com.hwsuanfa.algo.entity.User::getStatus, "ACTIVE"))
                .stream()
                .filter(user -> !java.util.Objects.equals(user.getId(), operatorId))
                .forEach(user -> {
                    NotificationService service = notificationService.getIfAvailable();
                    if (service != null) {
                        service.send(user.getId(), "ANNOUNCEMENT", "新的站内公告",
                                announcement.getTitle(), "ANNOUNCEMENT", announcement.getId());
                    }
                });
    }
}
