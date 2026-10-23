import { get } from '@/lib/http';

export interface AdminOverview {
  teamCount: number;
  activeUsers: number;
  assignmentCount: number;
  dailyCount: number;
  pendingAwards: number;
  pendingProblemLists: number;
  totalScore: number;
  assignmentCompletionRate: number;
  dailyCompletionRate: number;
  recentScores: { date: string; score: number }[];
}

export interface AdminAuditLog {
  id: number;
  operatorName: string | null;
  action: string;
  targetType: string | null;
  targetId: number | null;
  detail: string | null;
  createdAt: string;
}

export function fetchAdminOverview() {
  return get<AdminOverview>('/admin/overview');
}

export function fetchAdminLogs(params: { limit?: number; beforeId?: number; keyword?: string } = {}) {
  return get<AdminAuditLog[]>('/admin/logs', params);
}

