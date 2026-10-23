import { del, get, post, put } from '@/lib/http';
import type { TeamBrief, TeamDetail, UserBrief } from '@/types/team';

/* ---------------- 普通用户（含队长） ---------------- */

/** 我所属的队伍列表 */
export function fetchMyTeams() {
  return get<TeamBrief[]>('/teams');
}

/** 队伍详情。非本队成员会被后端拒绝（403） */
export function fetchTeamDetail(teamId: number) {
  return get<TeamDetail>(`/teams/${teamId}`);
}

/* ---------------- 站主后台 ---------------- */

export function adminListTeams() {
  return get<TeamBrief[]>('/admin/teams');
}

export function adminTeamDetail(teamId: number) {
  return get<TeamDetail>(`/admin/teams/${teamId}`);
}

export function adminCreateTeam(body: {
  name: string;
  description?: string;
  leaderUserId?: number | null;
}) {
  return post<TeamDetail>('/admin/teams', body);
}

export function adminUpdateTeam(teamId: number, body: { name?: string; description?: string }) {
  return put<TeamDetail>(`/admin/teams/${teamId}`, body);
}

export function adminDissolveTeam(teamId: number) {
  return del<void>(`/admin/teams/${teamId}`);
}

export function adminFreezeTeam(teamId: number, reason?: string) {
  return post<void>(`/admin/teams/${teamId}/freeze`, { reason });
}

export function adminUnfreezeTeam(teamId: number) {
  return post<void>(`/admin/teams/${teamId}/unfreeze`);
}

export function adminAddMember(teamId: number, userId: number, asLeader = false) {
  return post<TeamDetail>(`/admin/teams/${teamId}/members`, { userId, asLeader });
}

/**
 * 移除成员。
 * 若被移除者是队长，必须传 newLeaderId，否则后端会返回 409 要求先指定新队长。
 */
export function adminRemoveMember(teamId: number, userId: number, newLeaderId?: number) {
  return del<TeamDetail>(`/admin/teams/${teamId}/members/${userId}`, {
    newLeaderId: newLeaderId ?? null,
  });
}

export function adminChangeLeader(teamId: number, newLeaderId: number) {
  return put<TeamDetail>(`/admin/teams/${teamId}/leader`, { newLeaderId });
}

/** 搜索候选用户（添加成员用） */
export function adminSearchUsers(keyword?: string) {
  return get<UserBrief[]>('/admin/users', keyword ? { keyword } : undefined);
}
