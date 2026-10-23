package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Notification;
import com.hwsuanfa.algo.mapper.NotificationMapper;
import com.hwsuanfa.algo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public void send(Long userId, String type, String title, String content, String targetType, Long targetId) {
        if (userId == null || title == null || title.isBlank()) return;
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type == null || type.isBlank() ? "SYSTEM" : type.trim().toUpperCase());
        notification.setTitle(title.trim());
        notification.setContent(content == null ? "" : content.trim());
        notification.setTargetType(targetType == null || targetType.isBlank() ? null : targetType.trim().toUpperCase());
        notification.setTargetId(targetId);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    @Override
    public Map<String, Object> list(Long userId, int page, int size, boolean unreadOnly) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId);
        if (unreadOnly) query.isNull(Notification::getReadAt);
        Page<Notification> result = notificationMapper.selectPage(new Page<>(safePage, safeSize), query);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getRecords());
        body.put("page", result.getCurrent());
        body.put("size", result.getSize());
        body.put("total", result.getTotal());
        body.put("pages", result.getPages());
        body.put("unreadCount", unreadCount(userId));
        return body;
    }

    @Override
    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        int changed = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
        if (changed == 0 && notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, notificationId).eq(Notification::getUserId, userId)) == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "通知不存在");
        }
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long notificationId) {
        int changed = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, notificationId).eq(Notification::getUserId, userId));
        if (changed == 0) throw BizException.of(ErrorCode.NOT_FOUND, "通知不存在");
    }

    @Override
    @Transactional
    public void clearRead(Long userId) {
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId).isNotNull(Notification::getReadAt));
    }
}
