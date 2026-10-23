package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(notificationService.list(me.getId(), page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ApiResult<Map<String, Long>> unreadCount(@AuthenticationPrincipal LoginUser me) {
        return ApiResult.ok(Map.of("count", notificationService.unreadCount(me.getId())));
    }

    @PostMapping("/{id}/read")
    public ApiResult<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal LoginUser me) {
        notificationService.markRead(me.getId(), id);
        return ApiResult.ok();
    }

    @PostMapping("/read-all")
    public ApiResult<Void> markAllRead(@AuthenticationPrincipal LoginUser me) {
        notificationService.markAllRead(me.getId());
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LoginUser me) {
        notificationService.delete(me.getId(), id);
        return ApiResult.ok();
    }

    @DeleteMapping("/read")
    public ApiResult<Void> clearRead(@AuthenticationPrincipal LoginUser me) {
        notificationService.clearRead(me.getId());
        return ApiResult.ok();
    }
}
