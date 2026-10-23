import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';

export interface ApiResult<T = unknown> {
  code: number;
  message: string;
  data: T;
}

export const TOKEN_KEY = 'hwsuanfa.access_token';
export const REFRESH_KEY = 'hwsuanfa.refresh_token';

export class ApiError extends Error {
  constructor(message: string, readonly status?: number, readonly code?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

interface SessionTokens {
  accessToken: string;
  refreshToken: string;
  user: unknown;
}

type SessionRequest = InternalAxiosRequestConfig & {
  retried?: boolean;
  sessionRevision?: number;
  accessToken?: string | null;
};

let revision = 0;
let refreshTask: Promise<void> | null = null;
let expiredHandler = () => {};
let refreshedHandler = (_user: unknown) => {};

export function setSessionHandlers(expired: () => void, refreshed: (user: unknown) => void) {
  expiredHandler = expired;
  refreshedHandler = refreshed;
}

export function markSessionChange() {
  revision++;
}

export function clearSession() {
  markSessionChange();
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  expiredHandler();
}

export const http: AxiosInstance = axios.create({ baseURL: '/api', timeout: 15000 });
const refreshHttp = axios.create({ baseURL: '/api', timeout: 15000 });

function errorFrom(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (axios.isAxiosError<ApiResult>(error)) {
    const status = error.response?.status;
    return new ApiError(error.response?.data?.message
      ?? (status ? `请求失败（HTTP ${status}）` : '网络异常，请稍后重试'), status, error.response?.data?.code);
  }
  return new ApiError(error instanceof Error ? error.message : '请求失败，请稍后重试');
}

function refreshSession() {
  if (refreshTask) return refreshTask;
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  const previousAccess = localStorage.getItem(TOKEN_KEY);
  const startedRevision = revision;
  const execute = async () => {
    if (startedRevision !== revision) throw new ApiError('登录状态已切换，请重试');
    if (localStorage.getItem(TOKEN_KEY) && localStorage.getItem(TOKEN_KEY) !== previousAccess
        && localStorage.getItem(REFRESH_KEY) !== refreshToken) return;
    if (!refreshToken) {
      clearSession();
      throw new ApiError('登录已过期，请重新登录', 401);
    }
    try {
      const { data } = await refreshHttp.post<ApiResult<SessionTokens>>('/auth/refresh', { refreshToken });
      if (data.code !== 0 || !data.data?.accessToken || !data.data?.refreshToken) {
        throw new ApiError(data.message || '刷新登录失败', 401, data.code);
      }
      if (startedRevision !== revision || localStorage.getItem(REFRESH_KEY) !== refreshToken) {
        throw new ApiError('登录状态已切换，请重试');
      }
      localStorage.setItem(TOKEN_KEY, data.data.accessToken);
      localStorage.setItem(REFRESH_KEY, data.data.refreshToken);
      refreshedHandler(data.data.user);
    } catch (error) {
      const failure = errorFrom(error);
      if (startedRevision === revision && localStorage.getItem(REFRESH_KEY) === refreshToken
          && (failure.status === 401 || failure.status === 403)) clearSession();
      throw failure;
    }
  };
  refreshTask = (typeof navigator !== 'undefined' && navigator.locks
    ? navigator.locks.request('hwsuanfa.auth.refresh', execute) : execute()).finally(() => { refreshTask = null; });
  return refreshTask;
}

http.interceptors.request.use((config: SessionRequest) => {
  const token = localStorage.getItem(TOKEN_KEY);
  config.sessionRevision ??= revision;
  config.accessToken = token;
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  else config.headers.delete('Authorization');
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResult>) => {
    const config = error.config as SessionRequest | undefined;
    const status = error.response?.status;
    const authRequest = /^\/?auth\/(login|register|refresh|logout)(?:\?|$)/.test(config?.url ?? '');
    if (config && config.sessionRevision !== revision) throw new ApiError('登录状态已切换，请重试');
    if (status === 401 && config && !authRequest && !config.retried && config.accessToken) {
      config.retried = true;
      if (config.accessToken === localStorage.getItem(TOKEN_KEY)) await refreshSession();
      if (config.sessionRevision !== revision) throw new ApiError('登录状态已切换，请重试');
      return http.request(config);
    }
    if (status === 401 && config?.accessToken && !authRequest) clearSession();
    throw errorFrom(error);
  },
);

function unwrap<T>(result: ApiResult<T>) {
  if (result.code !== 0) throw new ApiError(result.message || '请求失败', undefined, result.code);
  return result.data;
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const { data } = await http.get<ApiResult<T>>(url, { params });
  return unwrap(data);
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const { data } = await http.post<ApiResult<T>>(url, body);
  return unwrap(data);
}

export async function upload<T>(url: string, file: File, field = 'file'): Promise<T> {
  const form = new FormData();
  form.append(field, file);
  const { data } = await http.post<ApiResult<T>>(url, form);
  return unwrap(data);
}

export async function download(url: string): Promise<Blob> {
  const response = await http.get(url, { responseType: 'blob' });
  return response.data as Blob;
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const { data } = await http.put<ApiResult<T>>(url, body);
  return unwrap(data);
}

export async function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const { data } = await http.delete<ApiResult<T>>(url, { params });
  return unwrap(data);
}
