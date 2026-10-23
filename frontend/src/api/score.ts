import { get } from '@/lib/http';
import type { ScoreEventRow } from '@/types/assignment';

/** 我的积分明细 */
export function fetchMyScore() {
  return get<{ total: number; events: ScoreEventRow[] }>('/score/mine');
}
