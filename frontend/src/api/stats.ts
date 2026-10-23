import { get } from '@/lib/http';

export interface TeamCompletion {
  teamId: number;
  assignmentCount: number;
  completedCount: number;
  totalCount: number;
  members: Array<{ userId: number; nickname: string; completedCount: number; totalCount: number; completionRate: number }>;
}

export interface ScoreTrend {
  teamId: number | null;
  days: number;
  points: Array<{ date: string; score: number }>;
}

export function fetchTeamCompletion(teamId: number) {
  return get<TeamCompletion>(`/stats/teams/${teamId}/completion`);
}

export function fetchScoreTrend(teamId?: number, days = 30) {
  return get<ScoreTrend>('/stats/score-trend', { teamId, days });
}
