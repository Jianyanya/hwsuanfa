import { create } from 'zustand';

import { ApiError, clearSession, get, markSessionChange, post, setSessionHandlers, TOKEN_KEY, REFRESH_KEY } from '@/lib/http';
import { queryClient } from '@/lib/query-client';

/* ---------------- 类型（与后端 LoginVO 对齐） ---------------- */

export interface TeamBrief {
  id: number;
  name: string;
  /** 是否该队队长 */
  leader: boolean;
}

export interface AuthUser {
  id: number;
  /** 登录账号 = 学号 */
  username: string;
  /** 名称 / 站内显示名 */
  nickname: string;
  avatar: string | null;
  /** 本人真实姓名（仅本人与站主可见） */
  realName: string | null;
  /** 本人学号（仅本人与站主可见） */
  studentNo: string | null;
  className: string | null;
  /** SITE_OWNER / MEMBER */
  role: string;
  /** 便捷布尔：是否站主 */
  siteOwner: boolean;
  privacyLevel: 'PRIVATE' | 'PARTIAL' | 'PUBLIC';
  showCode: boolean;
  teams: TeamBrief[];
  lastLoginAt: string | null;
}

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUser;
}

/** 注册表单 —— 登录账号就是学号，所以没有单独的 username 字段 */
export interface RegisterPayload {
  nickname: string;
  realName: string;
  studentNo: string;
  className: string;
  password: string;
  inviteCode: string;
}

interface AuthState {
  user: AuthUser | null;
  /** 首次挂载时用 /auth/me 恢复登录态，期间不要重定向到登录页 */
  initializing: boolean;
  sessionError: string | null;
  activeTeamId: number | null;
  setCurrentTeam: (id: number) => void;
  login: (username: string, password: string) => Promise<void>;
  /**
   * 邀请码注册。成功后直接写入登录态（后端会一并签发令牌），无需再登录一次。
   * 角色由后端按邀请码决定，前端不传、也传不了角色。
   */
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  /** 应用启动时调用，恢复会话 */
  bootstrap: () => Promise<void>;
  /** 权限判断集中在这里，页面不要各自写 role === 'SITE_OWNER' */
  isSiteOwner: () => boolean;
  /** 当前队伍（默认第一个），侧栏用 */
  currentTeam: () => TeamBrief | null;
  /** 是否为某队队长 */
  isLeaderOf: (teamId: number) => boolean;
}

let bootstrapTask: Promise<void> | null = null;
let sessionVersion = 0;

function savedTeam(user: AuthUser) {
  const id = Number(localStorage.getItem(`hwsuanfa.current_team:${user.id}`));
  return user.teams.some((team) => team.id === id) ? id : user.teams[0]?.id ?? null;
}

function clearCache() {
  void queryClient.cancelQueries();
  queryClient.clear();
}

export const useAuth = create<AuthState>((set, getState) => ({
  user: null,
  initializing: true,
  sessionError: null,
  activeTeamId: null,

  setCurrentTeam(id) {
    const user = getState().user;
    if (!user?.teams.some((team) => team.id === id)) return;
    localStorage.setItem(`hwsuanfa.current_team:${user.id}`, String(id));
    set({ activeTeamId: id });
  },

  async login(username, password) {
    const data = await post<LoginResponse>('/auth/login', { username, password });
    sessionVersion++;
    markSessionChange();
    clearCache();
    localStorage.setItem(TOKEN_KEY, data.accessToken);
    localStorage.setItem(REFRESH_KEY, data.refreshToken);
    set({ user: data.user, activeTeamId: savedTeam(data.user), initializing: false, sessionError: null });
  },

  async register(payload) {
    const data = await post<LoginResponse>('/auth/register', payload);
    sessionVersion++;
    markSessionChange();
    clearCache();
    localStorage.setItem(TOKEN_KEY, data.accessToken);
    localStorage.setItem(REFRESH_KEY, data.refreshToken);
    set({ user: data.user, activeTeamId: savedTeam(data.user), initializing: false, sessionError: null });
  },

  async logout() {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    try {
      // 后端会把 refresh 作废、access 进黑名单
      await post('/auth/logout', { refreshToken });
    } catch {
      // 注销失败也要清本地态，否则用户会卡在登录页面出不去
    } finally {
      clearSession();
    }
  },

  async bootstrap() {
    if (bootstrapTask) return bootstrapTask;
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      set({ user: null, activeTeamId: null, initializing: false, sessionError: null });
      return;
    }
    const version = sessionVersion;
    set({ initializing: true, sessionError: null });
    const task = (async () => {
      try {
        const me = await get<AuthUser>('/auth/me');
        if (version !== sessionVersion) return;
        if (getState().user?.id !== me.id) clearCache();
        set({ user: me, activeTeamId: savedTeam(me), initializing: false, sessionError: null });
      } catch (error) {
        if (version !== sessionVersion) return;
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) clearSession();
        else set({ initializing: false, sessionError: error instanceof Error ? error.message : '恢复登录失败，请重试' });
      }
    })();
    bootstrapTask = task;
    return task.finally(() => { if (bootstrapTask === task) bootstrapTask = null; });
  },

  isSiteOwner() {
    return getState().user?.siteOwner === true;
  },

  currentTeam() {
    const state = getState();
    return state.user?.teams.find((team) => team.id === state.activeTeamId) ?? state.user?.teams[0] ?? null;
  },

  isLeaderOf(teamId) {
    return getState().user?.teams?.some((t) => t.id === teamId && t.leader) ?? false;
  },
}));

setSessionHandlers(() => {
  sessionVersion++;
  bootstrapTask = null;
  clearCache();
  useAuth.setState({ user: null, activeTeamId: null, initializing: false, sessionError: null });
}, (user) => {
  const current = useAuth.getState().user;
  if (current && user && typeof user === 'object' && 'id' in user && user.id === current.id) {
    const updated = user as AuthUser;
    useAuth.setState({ user: updated, activeTeamId: savedTeam(updated) });
  }
});

window.addEventListener('storage', (event) => {
  if (event.key !== TOKEN_KEY && event.key !== null) return;
  const subject = (token: string | null) => {
    try { return token ? String(JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub) : null; }
    catch { return null; }
  };
  const previousUser = subject(event.oldValue);
  if (previousUser && previousUser === subject(event.newValue)) {
    void useAuth.getState().bootstrap();
    return;
  }
  sessionVersion++;
  bootstrapTask = null;
  markSessionChange();
  clearCache();
  useAuth.setState({ user: null, activeTeamId: null, initializing: !!event.newValue, sessionError: null });
  void useAuth.getState().bootstrap();
});
