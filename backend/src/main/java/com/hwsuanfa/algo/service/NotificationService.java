package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.entity.Notification;

import java.util.List;
import java.util.Map;

public interface NotificationService {

    void send(Long userId, String type, String title, String content, String targetType, Long targetId);

    Map<String, Object> list(Long userId, int page, int size, boolean unreadOnly);

    long unreadCount(Long userId);

    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);

    void delete(Long userId, Long notificationId);

    void clearRead(Long userId);
}
