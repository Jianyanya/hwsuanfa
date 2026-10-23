import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from '@/stores/auth';
import { Button, Notice } from '@/components/ui/Button';

/**
 * 登录守卫。
 *
 * ⚠️ 这只是「不给入口/帮用户跳转」的体验层措施。
 * 真正的权限判定必须在后端完成 —— 前端隐藏按钮不等于数据安全。
 */
export function RequireAuth() {
  const user = useAuth((s) => s.user);
  const initializing = useAuth((s) => s.initializing);
  const sessionError = useAuth((s) => s.sessionError);
  const bootstrap = useAuth((s) => s.bootstrap);
  const location = useLocation();

  // 恢复会话期间不能直接重定向，否则刷新页面会被误踢到登录页
  if (initializing) {
    return <FullScreenLoading />;
  }

  if (sessionError) return <div className="space-y-3 p-6"><Notice tone="danger">{sessionError}</Notice><Button onClick={() => void bootstrap()}>重试登录恢复</Button></div>;

  if (!user) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  return <Outlet />;
}

/** 站主守卫。非站主访问 /admin 时回首页，不展示「无权限」以免暴露后台存在 */
export function RequireSiteOwner() {
  const user = useAuth((s) => s.user);
  const initializing = useAuth((s) => s.initializing);

  if (initializing) {
    return <FullScreenLoading />;
  }

  if (!user?.siteOwner) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

function FullScreenLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-sm text-muted-foreground">加载中…</div>
    </div>
  );
}
