import { del, get, post, put } from '@/lib/http';
import type {
  Assignment,
  AssignmentPayload,
  AssignmentType,
  DifficultyTier,
  ProblemBrief,
} from '@/types/assignment';

/* ---------------- 队员 ---------------- */

export function fetchMyAssignments(type?: AssignmentType | 'ALL') {
  return get<Assignment[]>('/assignments', type ? { type } : undefined);
}

export function fetchAssignmentDetail(id: number) {
  return get<Assignment>(`/assignments/${id}`);
}

/**
 * 标记作业里某道题完成。
 * 同一道题的首次完成积分与同一作业的完成奖励都由后端幂等，重复调用安全。
 */
export function markProblemDone(assignmentId: number, problemId: number) {
  return post<Assignment>(`/assignments/${assignmentId}/problems/${problemId}/done`);
}

/**
 * 结算作业。
 *
 * 当作业里的题早已在别处做过时，每题都显示已完成、没有按钮可按，
 * 作业会停在未结算状态 —— 用这个接口正式结算并领奖励。
 */
export function completeAssignment(assignmentId: number) {
  return post<Assignment>(`/assignments/${assignmentId}/complete`);
}

/* ---------------- 每日一题 ---------------- */

/** 今日一题；尚未发布时返回 null */
export function fetchTodayDaily() {
  return get<Assignment | null>('/daily/today');
}

export function fetchDailyHistory(limit = 30) {
  return get<Assignment[]>('/daily/history', { limit });
}

/* ---------------- 站主：作业 ---------------- */

export function adminListAssignments(type?: AssignmentType | 'ALL', withDetail = true) {
  const params: Record<string, unknown> = { withDetail };
  if (type) params.type = type;
  return get<Assignment[]>('/admin/assignments', params);
}

export function adminAssignmentDetail(id: number) {
  return get<Assignment>(`/admin/assignments/${id}`);
}

export function adminCreateAssignment(body: AssignmentPayload) {
  return post<Assignment>('/admin/assignments', body);
}

export function adminUpdateAssignment(id: number, body: AssignmentPayload) {
  return put<Assignment>(`/admin/assignments/${id}`, body);
}

export function adminDeleteAssignment(id: number) {
  return del<void>(`/admin/assignments/${id}`);
}

/* ---------------- 站主：题目 ---------------- */

export function adminSearchProblems(keyword?: string, platform?: string, limit = 50) {
  const params: Record<string, unknown> = { limit };
  if (keyword) params.keyword = keyword;
  if (platform) params.platform = platform;
  return get<ProblemBrief[]>('/admin/problems', params);
}

export function adminCreateProblem(body: {
  title: string;
  platform: string;
  problemNo?: string;
  url?: string;
  difficulty?: string;
  tags?: string;
  note?: string;
}) {
  return post<ProblemBrief>('/admin/problems', body);
}

export function adminDeleteProblem(id: number) {
  return del<void>(`/admin/problems/${id}`);
}

/* ---------------- 站主：难度分档 ---------------- */

/** 某平台的档位，按由易到难排序（录题时给下拉用） */
export function fetchDifficultyTiers(platform: string) {
  return get<DifficultyTier[]>(`/admin/difficulty-tiers/${platform}`);
}

/** 所有平台的档位，按平台分组（后台配置页用） */
export function fetchAllDifficultyTiers() {
  return get<Record<string, DifficultyTier[]>>('/admin/difficulty-tiers');
}

/** 整组替换某平台的档位配置 */
export function saveDifficultyTiers(platform: string, tiers: DifficultyTier[]) {
  return put<void>(`/admin/difficulty-tiers/${platform}`, { tiers });
}

/** 站主：贴链接批量导入题目到作业 */
export function importAssignmentProblems(assignmentId: number | string, text: string, defaultPlatform?: string) {
  return post<Assignment>(`/assignments/${assignmentId}/problems/import`, { text, defaultPlatform });
}
