import type { LucideIcon } from 'lucide-react';
import {
  BookOpen,
  Bell,
  Calendar,
  CalendarCheck,
  ClipboardList,
  House,
  Megaphone,
  PenLine,
  Trophy,
  User,
  Users,
  Wrench,
  Shield,
} from 'lucide-react';

export interface NavItem {
  /** 路由路径 */
  to: string;
  /** 侧栏显示名 */
  label: string;
  icon: LucideIcon;
  /** 仅站主可见 */
  adminOnly?: boolean;
  /** 需要登录 */
  requiresAuth?: boolean;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

/**
 * 导航结构 —— 详见 design/界面样式规格说明书.md 第 5 章。
 * 注意：分组顺序、图标、可见性都由这里驱动，页面里不要再硬编码。
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: '训练',
    items: [
      { to: '/', label: '首页', icon: House },
      { to: '/team', label: '我的队伍', icon: Users },
      { to: '/assignment', label: '作业', icon: ClipboardList, requiresAuth: true },
      { to: '/daily', label: '每日一题', icon: CalendarCheck, requiresAuth: true },
      { to: '/problemset', label: '题单', icon: BookOpen },
      { to: '/contest', label: '比赛', icon: Calendar },
      { to: '/rank', label: '积分排行', icon: Trophy },
    ],
  },
  {
    label: '社区',
    items: [
      { to: '/bulletin', label: '公告', icon: Megaphone },
      { to: '/blog', label: '博客', icon: PenLine },
      { to: '/tools', label: '工具', icon: Wrench },
    ],
  },
  {
    label: '个人',
    items: [{ to: '/profile', label: '个人中心', icon: User, requiresAuth: true }, { to: '/notifications', label: '通知中心', icon: Bell, requiresAuth: true }],
  },
  {
    label: '管理',
    items: [{ to: '/admin', label: '站主后台', icon: Shield, adminOnly: true }],
  },
];

/** 移动端底部导航：最多 5 项 */
export const MOBILE_NAV: NavItem[] = [
  { to: '/', label: '首页', icon: House },
  { to: '/assignment', label: '作业', icon: ClipboardList, requiresAuth: true },
  { to: '/daily', label: '每日一题', icon: CalendarCheck, requiresAuth: true },
  { to: '/rank', label: '排行', icon: Trophy },
  { to: '/profile', label: '我的', icon: User, requiresAuth: true },
];

/** 由路径反查标题，用于顶栏显示 */
export function findNavLabel(pathname: string): string {
  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if (item.to === pathname) return item.label;
    }
  }
  // 子路由（如 /team/12）退回一级路径的标题
  const base = '/' + pathname.split('/').filter(Boolean)[0];
  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if (item.to === base) return item.label;
    }
  }
  return '校队算法训练平台';
}
