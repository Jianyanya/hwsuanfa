import { get, put } from '@/lib/http';
import type { CfVerifyResult, HandlePayload, Profile } from '@/types/assignment';

/** 我的个人资料 */
export function fetchMyProfile() {
  return get<Profile>('/profile/me');
}

/** 绑定各平台 OJ 账号（handle，不收密码） */
export function bindHandles(payload: HandlePayload) {
  return put<Profile>('/profile/handles', payload);
}

/**
 * 在线校验某平台用户名是否存在。
 * 能力随平台不同：CF/AtCoder 真实校验，LeetCode 尽力，牛客无法校验。
 */
export function verifyHandle(platform: string, handle: string) {
  return get<CfVerifyResult>('/profile/verify-handle', { platform, handle });
}

/** 更新基本资料（昵称/头像/邮箱/隐私档位） */
export function updateMe(payload: {
  nickname?: string;
  avatar?: string;
  email?: string;
  qq?: string;
  wechat?: string;
  phone?: string;
  privacyLevel?: string;
  showCode?: boolean;
}) {
  return put<Profile>('/profile/me', payload);
}

/** 修改密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return put<void>('/profile/password', { oldPassword, newPassword });
}
