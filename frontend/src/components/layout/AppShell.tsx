import { Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';

import { AppHeader } from './AppHeader';
import { AppSidebar } from './AppSidebar';
import { MobileNav } from './MobileNav';
import { useAuth } from '@/stores/auth';
import { usePreferences } from '@/stores/preferences';

/**
 * 应用外壳：桌面端 = 256px 侧栏 + 56px 顶栏；移动端 = 顶栏 + 底部导航。
 * 布局尺寸由 CSS 变量控制（见 index.css），不要在此硬编码像素。
 */
export function AppShell() {
  const user = useAuth((s) => s.user);
  const initializing = useAuth((s) => s.initializing);
  const bootstrap = useAuth((s) => s.bootstrap);
  const activeTeamId = useAuth((s) => s.activeTeamId);
  const sidebarCollapsed = usePreferences((s) => s.sidebarCollapsed);
  const toggleSidebar = usePreferences((s) => s.toggleSidebar);
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  // 首次挂载恢复会话（刷新页面后保持登录）
  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  useEffect(() => { setMobileOpen(false); }, [location.pathname]);
  useEffect(() => {
    if (!mobileOpen) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setMobileOpen(false); document.querySelector<HTMLButtonElement>('button[aria-label="打开菜单"]')?.focus(); }
      if (event.key !== 'Tab') return;
      const controls = [...document.querySelectorAll<HTMLElement>('#app-sidebar a[href], #app-sidebar button:not(:disabled), #app-sidebar select')];
      const first = controls[0];
      const last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const media = window.matchMedia('(min-width: 768px)');
    const resized = () => { if (media.matches) setMobileOpen(false); };
    window.addEventListener('keydown', close);
    media.addEventListener('change', resized);
    document.querySelector<HTMLButtonElement>('#app-sidebar button[aria-label="关闭菜单"]')?.focus();
    return () => { document.body.style.overflow = previous; window.removeEventListener('keydown', close); media.removeEventListener('change', resized); };
  }, [mobileOpen]);

  const isAdmin = user?.siteOwner ?? false;
  const currentTeam = user?.teams.find((team) => team.id === activeTeamId) ?? user?.teams[0];
  const currentTeamName = currentTeam?.name ?? null;
  const isLeader = currentTeam?.leader ?? false;

  return (
    <>
      {mobileOpen && <button type="button" className="fixed inset-0 z-40 bg-black/50 md:hidden" aria-label="关闭菜单遮罩" onClick={() => setMobileOpen(false)} />}
      <AppSidebar
        isAdmin={isAdmin}
        currentTeamName={currentTeamName}
        isLeader={isLeader}
        loading={initializing}
        collapsed={sidebarCollapsed}
        mobileOpen={mobileOpen}
        onClose={() => setMobileOpen(false)}
      />
      <div className={sidebarCollapsed ? '' : 'md:pl-[var(--sidebar-width)]'}>
        <AppHeader isAdmin={isAdmin} sidebarCollapsed={sidebarCollapsed} mobileOpen={mobileOpen} onToggleSidebar={toggleSidebar} onOpenMobile={() => setMobileOpen(true)} />
        <main className="mx-auto max-w-[1400px] p-4 md:p-6">
          <div key={user?.id ?? 'anonymous'}><Suspense fallback={<div className="py-10 text-center text-sm text-muted-foreground">页面加载中…</div>}><Outlet /></Suspense></div>
        </main>
        {/* 移动端底部导航的占位，避免内容被遮挡 */}
        <div className="h-16 md:hidden" />
      </div>
      <MobileNav />
    </>
  );
}
