import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck, ExternalLink, Trash2 } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button, Notice } from '@/components/ui/Button';
import {
  clearReadNotifications, deleteNotification, fetchNotifications,
  markAllNotificationsRead, markNotificationRead,
} from '@/api/notifications';

export default function NotificationsPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const query = useQuery({ queryKey: ['notifications', page, unreadOnly], queryFn: () => fetchNotifications({ page, size: 20, unreadOnly }) });
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['notifications'] });
    void qc.invalidateQueries({ queryKey: ['notification-unread'] });
  };
  const read = useMutation({ mutationFn: markNotificationRead, onSuccess: refresh });
  const readAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: refresh });
  const remove = useMutation({ mutationFn: deleteNotification, onSuccess: refresh });
  const clear = useMutation({ mutationFn: clearReadNotifications, onSuccess: refresh });
  const items = query.data?.items ?? [];
  const pages = query.data?.pages ?? 1;
  const targetPath = (item: { targetType: string | null; targetId: number | null }) => {
    if (!item.targetId) return null;
    if (item.targetType === 'ARTICLE') return `/blog/${item.targetId}`;
    if (item.targetType === 'ANNOUNCEMENT') return `/bulletin/${item.targetId}`;
    if (item.targetType === 'AWARD') return '/profile';
    if (item.targetType === 'TEAM') return `/team/${item.targetId}`;
    return null;
  };
  const unread = useMemo(() => query.data?.unreadCount ?? 0, [query.data?.unreadCount]);
  return (
    <div className="space-y-5">
      <PageHeader title="通知中心" description="查看博客互动、审核结果和站内公告" />
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" variant={!unreadOnly ? 'subtle' : 'ghost'} onClick={() => { setUnreadOnly(false); setPage(1); }}>全部</Button>
        <Button size="sm" variant={unreadOnly ? 'subtle' : 'ghost'} onClick={() => { setUnreadOnly(true); setPage(1); }}>未读{unread ? ` · ${unread}` : ''}</Button>
        <Button size="sm" variant="ghost" disabled={!unread || readAll.isPending} onClick={() => readAll.mutate()}><CheckCheck className="h-3.5 w-3.5" />全部标为已读</Button>
        <Button size="sm" variant="ghost" disabled={clear.isPending} onClick={() => clear.mutate()}><Trash2 className="h-3.5 w-3.5" />清理已读</Button>
      </div>
      {query.isError && <Notice tone="danger">{(query.error as Error).message}</Notice>}
      <Card>
        {query.isLoading && <div className="py-10 text-center text-sm text-muted-foreground">正在加载通知…</div>}
        {!query.isLoading && items.length === 0 && <div className="py-12 text-center text-sm text-muted-foreground"><Bell className="mx-auto mb-2 h-7 w-7" />暂时没有通知</div>}
        <div className="divide-y divide-[var(--border)]">
          {items.map((item) => {
            const path = targetPath(item);
            return <div key={item.id} className={`flex items-start gap-3 py-4 first:pt-0 last:pb-0 ${item.readAt ? '' : 'bg-[color-mix(in_srgb,var(--primary)_5%,transparent)]'}`}>
              <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${item.readAt ? 'bg-transparent' : 'bg-primary'}`} />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2"><span className="font-medium">{item.title}</span>{!item.readAt && <Badge tone="primary">未读</Badge>}</div>
                <p className="mt-1 break-words text-sm leading-6 text-muted-foreground">{item.content}</p>
                <time className="mt-1 block text-xs text-muted-foreground">{formatDate(item.createdAt)}</time>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                {path && <Button size="sm" variant="ghost" aria-label="查看详情" onClick={() => { if (!item.readAt) read.mutate(item.id); navigate(path); }}><ExternalLink className="h-3.5 w-3.5" /></Button>}
                {!item.readAt && <Button size="sm" variant="ghost" aria-label="标记已读" onClick={() => read.mutate(item.id)}>已读</Button>}
                <Button size="sm" variant="ghost" aria-label="删除通知" onClick={() => remove.mutate(item.id)}><Trash2 className="h-3.5 w-3.5" /></Button>
              </div>
            </div>;
          })}
        </div>
      </Card>
      {pages > 1 && <div className="flex items-center justify-center gap-2"><Button size="sm" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>上一页</Button><span className="text-xs text-muted-foreground">第 {page} / {pages} 页</span><Button size="sm" disabled={page >= pages} onClick={() => setPage((value) => value + 1)}>下一页</Button></div>}
      <p className="text-xs text-muted-foreground"><Link to="/bulletin" className="text-primary hover:underline">查看全部公告</Link></p>
    </div>
  );
}

function formatDate(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '';
}
