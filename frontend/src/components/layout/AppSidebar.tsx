import { NavLink, useNavigate } from 'react-router-dom';
import { Crown, LogIn, LogOut, Monitor, Moon, Sparkles, Sun, Users, X } from 'lucide-react';

import { NAV_GROUPS } from '@/config/nav';
import { useAuth } from '@/stores/auth';
import { cn } from '@/lib/utils';
import { usePreferences } from '@/stores/preferences';

interface AppSidebarProps {
  /** 当前是否站主（决定「管理」分组是否渲染） */
  isAdmin?: boolean;
  /** 当前队伍名，null 表示未加入任何队伍 */
  currentTeamName?: string | null;
  /** 是否当前队伍的队长 */
  isLeader?: boolean;
  /** 会话恢复中，底部区域显示占位而不是「未登录」 */
  loading?: boolean;
  collapsed?: boolean;
  mobileOpen?: boolean;
  onClose?: () => void;
}

export function AppSidebar({
  isAdmin = false,
  currentTeamName = null,
  isLeader = false,
  loading = false,
  collapsed = false,
  mobileOpen = false,
  onClose,
}: AppSidebarProps) {
  const user = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
  const navigate = useNavigate();
  const activeTeamId = useAuth((s) => s.activeTeamId);
  const setCurrentTeam = useAuth((s) => s.setCurrentTeam);
  const theme = usePreferences((s) => s.theme);
  const cycleTheme = usePreferences((s) => s.cycleTheme);
  const themeLabel = theme === 'system' ? '跟随系统' : theme === 'light' ? '浅色模式' : '深色模式';
  const ThemeIcon = theme === 'system' ? Monitor : theme === 'light' ? Sun : Moon;

  async function handleLogout() {
    await logout();
    onClose?.();
    navigate('/login', { replace: true });
  }

  return (
    <aside
      id="app-sidebar"
      aria-label="网站导航"
      role={mobileOpen ? 'dialog' : undefined}
      aria-modal={mobileOpen || undefined}
      className={cn('fixed inset-y-0 left-0 z-50 flex-col border-r', mobileOpen ? 'flex' : 'hidden', !collapsed && 'md:flex')}
      style={{
        width: 'var(--sidebar-width)',
        backgroundColor: 'var(--sidebar)',
        borderColor: 'var(--sidebar-border)',
      }}
    >
      {/* 品牌区 */}
      <div
        className="flex items-center gap-2 border-b px-3"
        style={{ height: 'var(--header-height)', borderColor: 'var(--sidebar-border)' }}
      >
        <div
          className="flex h-8 w-8 items-center justify-center rounded-md"
          style={{ backgroundColor: 'var(--primary)' }}
        >
          <Sparkles className="h-4 w-4" style={{ color: 'var(--primary-foreground)' }} />
        </div>
        <div className="leading-tight">
          <div className="text-sm font-semibold">校队训练平台</div>
          <div className="text-[11px] text-muted-foreground">Algo Training</div>
        </div>
        <button type="button" className="ml-auto p-2 md:hidden" aria-label="关闭菜单" onClick={onClose}><X className="h-4 w-4" /></button>
      </div>

      {/* 导航 */}
      <nav className="flex-1 overflow-y-auto px-2 py-2">
        {NAV_GROUPS.map((group) => {
          // 「管理」分组仅站主渲染 —— 权限的第一道防线是后端，这里只是不给入口
          if (group.label === '管理' && !isAdmin) return null;

          return (
            <div key={group.label}>
              <div className="section-label">{group.label}</div>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  onClick={onClose}
                  className={({ isActive }) =>
                    cn(
                      'nav-item',
                      isActive &&
                        'bg-[color-mix(in_oklab,var(--primary)_18%,transparent)] font-medium text-foreground',
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      <item.icon style={isActive ? { color: 'var(--primary)' } : undefined} />
                      <span>{item.label}</span>
                    </>
                  )}
                </NavLink>
              ))}
            </div>
          );
        })}
      </nav>

      {/* 侧栏底部 */}
      <div className="border-t p-2" style={{ borderColor: 'var(--sidebar-border)' }}>
        {loading ? (
          <div className="px-2 py-3 text-xs text-muted-foreground">加载中…</div>
        ) : user ? (
          <>
            <div className="section-label" style={{ marginTop: 0 }}>
              当前队伍
            </div>
            <div className="nav-item w-full justify-between">
              <span className="flex min-w-0 items-center gap-2">
                {isLeader ? <Crown /> : <Users />}
                {user.teams.length ? <select
                  aria-label="当前队伍"
                  className="min-w-0 flex-1 bg-transparent text-xs"
                  value={activeTeamId ?? user.teams[0].id}
                  onChange={(event) => { const id = Number(event.target.value); setCurrentTeam(id); navigate(`/team/${id}`); onClose?.(); }}
                >{user.teams.map((team) => <option key={team.id} value={team.id} style={{ backgroundColor: 'var(--sidebar)' }}>{team.name}</option>)}</select> : <span className="truncate">{currentTeamName ?? '未加入队伍'}</span>}
              </span>
              {isLeader && (
                <span className="pill pill-active shrink-0 text-[10px]">队长</span>
              )}
            </div>

            <button type="button" className="nav-item w-full" onClick={cycleTheme} aria-label={`切换主题，当前${themeLabel}`}>
              <ThemeIcon />
              <span>{themeLabel}</span>
            </button>

            <button type="button" className="nav-item w-full" onClick={handleLogout}>
              <LogOut />
              <span>退出登录</span>
            </button>
          </>
        ) : (
          <>
            <div className="section-label" style={{ marginTop: 0 }}>
              未登录
            </div>
            <NavLink to="/login" className="nav-item w-full" onClick={onClose}>
              <LogIn />
              <span>去登录</span>
            </NavLink>
          </>
        )}
      </div>
    </aside>
  );
}
