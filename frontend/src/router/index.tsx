import { createBrowserRouter, Navigate } from 'react-router-dom';
import { lazy } from 'react';

import { AppShell } from '@/components/layout/AppShell';
import { RequireAuth, RequireSiteOwner } from '@/components/auth/Guards';
const HomePage = lazy(() => import('@/pages/HomePage'));
const TeamPage = lazy(() => import('@/pages/TeamPage'));
const AssignmentPage = lazy(() => import('@/pages/AssignmentPage'));
const DailyPage = lazy(() => import('@/pages/DailyPage'));
const ProblemsetPage = lazy(() => import('@/pages/ProblemsetPage'));
const ContestPage = lazy(() => import('@/pages/ContestPage'));
const RankPage = lazy(() => import('@/pages/RankPage'));
const BulletinPage = lazy(() => import('@/pages/BulletinPage'));
const BlogPage = lazy(() => import('@/pages/BlogPage'));
const ToolsPage = lazy(() => import('@/pages/ToolsPage'));
const ProfilePage = lazy(() => import('@/pages/ProfilePage'));
const ProfileSettingsPage = lazy(() => import('@/pages/ProfilePage').then((module) => ({ default: module.ProfileSettingsPage })));
const ProfilePasswordPage = lazy(() => import('@/pages/ProfilePage').then((module) => ({ default: module.ProfilePasswordPage })));
const PasteViewPage = lazy(() => import('@/pages/PasteViewPage'));
const AdminPage = lazy(() => import('@/pages/AdminPage'));
const LoginPage = lazy(() => import('@/pages/LoginPage'));
const RegisterPage = lazy(() => import('@/pages/RegisterPage'));
const NotificationsPage = lazy(() => import('@/pages/NotificationsPage'));
const PublicUserPage = lazy(() => import('@/pages/PublicUserPage'));

/**
 * 路由表 —— 与 src/config/nav.ts 的导航项一一对应。
 *
 * 权限分两层：
 *   - 前端守卫（RequireAuth / RequireSiteOwner）只负责跳转体验；
 *   - 后端才是最终防线，任何敏感数据都不能仅靠前端隐藏。
 *
 * 公开可访问：/（首页总览）、/rank、/bulletin、/blog（按隐私规则裁剪）
 * 需要登录：/team、/assignment、/daily、/problemset、/contest、/tools、/profile
 * 仅站主：/admin
 */
export const router = createBrowserRouter([
  // 登录 / 注册页不套应用外壳
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  // 粘贴分享页：公开（分享出去的人未登录也要能看）
  { path: '/paste/:code', element: <PasteViewPage /> },
  {
    path: '/',
    element: <AppShell />,
    children: [
      /* ---- 公开 ---- */
      { index: true, element: <HomePage /> },
      { path: 'bulletin', element: <BulletinPage /> },
      { path: 'bulletin/:bulletinId', element: <BulletinPage /> },
      { path: 'blog', element: <BlogPage /> },
      { path: 'blog/:articleId', element: <BlogPage /> },
      { path: 'users/:userId', element: <PublicUserPage /> },

      /* ---- 需要登录 ---- */
      {
        element: <RequireAuth />,
        children: [
          { path: 'rank', element: <RankPage /> },
          { path: 'team', element: <TeamPage /> },
          { path: 'team/:teamId', element: <TeamPage /> },
          { path: 'assignment', element: <AssignmentPage /> },
          { path: 'assignment/:assignmentId', element: <AssignmentPage /> },
          { path: 'daily', element: <DailyPage /> },
          { path: 'problemset', element: <ProblemsetPage /> },
          { path: 'problemset/:listId', element: <ProblemsetPage /> },
          { path: 'contest', element: <ContestPage /> },
          { path: 'contest/:contestId', element: <ContestPage /> },
          { path: 'blog/new', element: <BlogPage mode="editor" /> },
          { path: 'blog/mine', element: <BlogPage mode="mine" /> },
          { path: 'blog/:articleId/edit', element: <BlogPage mode="editor" /> },
          { path: 'tools', element: <ToolsPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'profile/settings', element: <ProfileSettingsPage /> },
          { path: 'profile/password', element: <ProfilePasswordPage /> },
          { path: 'notifications', element: <NotificationsPage /> },

          /* ---- 仅站主 ---- */
          {
            element: <RequireSiteOwner />,
            children: [{ path: 'admin', element: <AdminPage /> }, { path: 'bulletin/new', element: <BulletinPage /> }],
          },
        ],
      },

      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);
