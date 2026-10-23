import { del, get, post } from '@/lib/http';

export interface NotificationItem {
  id: number;
  userId: number;
  type: string;
  title: string;
  content: string;
  targetType: string | null;
  targetId: number | null;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  page: number;
  size: number;
  total: number;
  pages: number;
  unreadCount: number;
}

export function fetchNotifications(params: { page?: number; size?: number; unreadOnly?: boolean } = {}) {
  return get<NotificationPage>('/notifications', params);
}

export function fetchUnreadNotificationCount() {
  return get<{ count: number }>('/notifications/unread-count');
}

export function markNotificationRead(id: number) {
  return post<void>(`/notifications/${id}/read`);
}

export function markAllNotificationsRead() {
  return post<void>('/notifications/read-all');
}

export function deleteNotification(id: number) {
  return del<void>(`/notifications/${id}`);
}

export function clearReadNotifications() {
  return del<void>('/notifications/read');
}
