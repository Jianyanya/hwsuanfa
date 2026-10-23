import { get } from '@/lib/http';
import type { RankData } from '@/types/assignment';

/** 积分排行榜（个人 + 队伍） */
export function fetchRank() {
  return get<RankData>('/rank');
}
