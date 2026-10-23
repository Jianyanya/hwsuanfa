import { del, get, post, put } from '@/lib/http';
import type { Contest, ContestLevel, ContestLogPayload, ContestPayload } from '@/types/assignment';

/* ---------------- 队员 / 队长 ---------------- */

export function fetchContests() {
  return get<Contest[]>('/contests');
}

export function fetchContestDetail(id: number) {
  return get<Contest>(`/contests/${id}`);
}

/** 本队队长（或站主）选择参赛，按等级发参赛分 */
export function selectParticipation(contestId: number, teamIds: number[]) {
  return post<Contest>(`/contests/${contestId}/participation`, { teamIds });
}

/** 取消参赛（不追回已发的参赛分） */
export function cancelParticipation(contestId: number, teamId: number) {
  return del<Contest>(`/contests/${contestId}/participation/${teamId}`);
}

/** 提交比赛日志。队长/站主直接确认；普通队员提交进待审核 */
export function submitContestLog(contestId: number, body: ContestLogPayload) {
  return post<Contest>(`/contests/${contestId}/logs`, body);
}

/** 本队队长确认日志 → 发名次分 */
export function confirmContestLog(contestId: number, logId: number) {
  return post<Contest>(`/contests/${contestId}/logs/${logId}/confirm`);
}

/* ---------------- 个人参赛 ---------------- */

/** 以个人身份报名（本队已报名时会被拒绝） */
export function joinIndividually(contestId: number) {
  return post<Contest>(`/contests/${contestId}/join-individual`);
}

/** 取消个人报名（不追回已发的参赛分） */
export function cancelIndividualJoin(contestId: number) {
  return del<Contest>(`/contests/${contestId}/join-individual`);
}

/** 同步官方榜单（Codeforces 可自动；其他平台无公开接口，回落手动录入） */
export function syncStandings(contestId: number) {
  return post<Contest>(`/contests/${contestId}/sync-standings`);
}

/** 比赛记录：个人 + 本队；range=7d/1m/1y/all，platform 可选 */
export function fetchContestRecords(range: string, platform?: string) {
  const params: Record<string, unknown> = { range };
  if (platform) params.platform = platform;
  return get<Contest[]>('/contests/records', params);
}

/* ---------------- 站主 ---------------- */

export function adminListContests() {
  return get<Contest[]>('/admin/contests');
}

export function adminCreateContest(body: ContestPayload) {
  return post<Contest>('/admin/contests', body);
}

export function adminUpdateContest(id: number, body: ContestPayload) {
  return put<Contest>(`/admin/contests/${id}`, body);
}

export function adminDeleteContest(id: number) {
  return del<void>(`/admin/contests/${id}`);
}

export type { ContestLevel };
