import { get, post } from '@/lib/http';
import type { MySubmission, SyncResult } from '@/types/assignment';

/** 同步我在某平台的 AC 记录（每平台每天 3 次） */
export function syncSubmissions(platform: string) {
  if (platform !== 'CODEFORCES' && platform !== 'ATCODER') return Promise.reject(new Error('该平台暂不支持服务器自动同步，洛谷请使用油猴脚本'));
  return post<SyncResult>(`/submissions/sync/${platform}`);
}

export interface SubmissionImportItem {
  problemNo: string;
  submittedAt: string;
  language?: string;
  url?: string;
}

export function importSubmissions(platform: string, records: SubmissionImportItem[]) {
  return post<SyncResult>(`/submissions/import/${platform}`, { records });
}

/** 我的 AC 提交记录 */
export function fetchMySubmissions() {
  return get<MySubmission[]>('/submissions/mine');
}

export interface InsightCount {
  name: string;
  count: number;
}

export interface SubmissionInsights {
  totalAc: number;
  byPlatform: InsightCount[];
  byDifficulty: InsightCount[];
  unknownDifficulty: number;
  byAbility: InsightCount[];
  abilityTaggedAc: number;
  byTag: InsightCount[];
}

export async function fetchInsights() {
  const data = await get<SubmissionInsights>('/submissions/insights');
  if (!Array.isArray(data.byAbility) || typeof data.unknownDifficulty !== 'number') {
    throw new Error('统计服务仍返回旧版本数据，请重启后端服务后刷新页面');
  }
  return data;
}
