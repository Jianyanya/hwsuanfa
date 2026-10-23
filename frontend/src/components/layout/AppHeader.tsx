import { Link, useLocation } from 'react-router-dom';
import { Bell, PanelLeft } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { fetchUnreadNotificationCount } from '@/api/notifications';
import { useAuth } from '@/stores/auth';

import { findNavLabel } from '@/config/nav';

interface AppHeaderProps {
  /** 站主标识，非站主不显示徽章 */
  isAdmin?: boolean;
  sidebarCollapsed?: boolean;
  mobileOpen?: boolean;
  onToggleSidebar: () => void;
  onOpenMobile: () => void;
}

export function AppHeader({ isAdmin = false, sidebarCollapsed = false, mobileOpen = false, onToggleSidebar, onOpenMobile }: AppHeaderProps) {
  const { pathname } = useLocation();
  const title = findNavLabel(pathname);
  const loggedIn = !!useAuth((state) => state.user);
  const unread = useQuery({ queryKey: ['notification-unread'], queryFn: fetchUnreadNotificationCount, enabled: loggedIn, refetchInterval: 60000 });

  return (
    <header
      className="sticky top-0 z-10 flex items-center gap-2 border-b px-4"
      style={{
        height: 'var(--header-height)',
        backgroundColor: 'var(--background)',
        borderColor: 'var(--border)',
      }}
    >
      {/* 移动端：无侧栏，此处留出菜单入口（桌面端由侧栏承担导航） */}
      <button
        type="button"
        className="nav-item !p-1.5 md:hidden"
        aria-label="打开菜单"
        aria-expanded={mobileOpen}
        aria-controls="app-sidebar"
        onClick={onOpenMobile}
      >
        <PanelLeft className="!h-[18px] !w-[18px]" />
      </button>
      <button
        type="button"
        className="nav-item !hidden !p-1.5 md:!flex"
        aria-label={sidebarCollapsed ? '展开侧栏' : '收起侧栏'}
        aria-expanded={!sidebarCollapsed}
        aria-controls="app-sidebar"
        onClick={onToggleSidebar}
      >
        <PanelLeft className="!h-[18px] !w-[18px]" />
      </button>

      <div className="text-sm font-medium">{title}</div>

      <div className="ml-auto flex items-center gap-2">
        {isAdmin && <span className="badge">站主</span>}
        <Link to={loggedIn ? '/notifications' : '/bulletin'} className="nav-item relative !p-1.5" aria-label={loggedIn ? '查看通知' : '查看公告通知'} title={loggedIn ? '通知中心' : '公告通知'}>
          <Bell className="!h-[18px] !w-[18px]" />
          {loggedIn && (unread.data?.count ?? 0) > 0 && <span className="absolute -right-1 -top-1 min-w-4 rounded-full bg-[var(--destructive)] px-1 text-center text-[10px] leading-4 text-white">{(unread.data?.count ?? 0) > 99 ? '99+' : unread.data?.count}</span>}
        </Link>
      </div>
    </header>
  );
}
