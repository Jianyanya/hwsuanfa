import { del, get, post, put } from '@/lib/http';
import type { AwardRow } from '@/types/assignment';

/** 提交获奖记录（待站主审核） */
export function submitAward(body: {
  awardName: string;
  contestName?: string;
  awardLevel?: string;
  awardedAt?: string;
  certUrl?: string;
  note?: string;
  isPublic?: boolean;
}) {
  return post<AwardRow>('/awards', body);
}

export function updateAward(id: number, body: {
  awardName: string;
  contestName?: string;
  awardLevel?: string;
  awardedAt?: string;
  certUrl?: string;
  note?: string;
  isPublic?: boolean;
}) {
  return put<AwardRow>(`/awards/${id}`, body);
}

export function deleteAward(id: number) {
  return del<void>(`/awards/${id}`);
}

export function uploadAwardCertificate(id: number, file: File) {
  const body = new FormData();
  body.append('file', file);
  return post<AwardRow>(`/awards/${id}/certificate`, body);
}

/** 我的获奖记录（含审核状态） */
export function fetchMyAwards() {
  return get<AwardRow[]>('/awards/mine');
}

/** 某用户公开的获奖记录（个人页展示用） */
export function fetchPublicAwards(userId: number) {
  return get<AwardRow[]>(`/awards/mine`, { userId });
}

/** 站主：按状态查看 */
export function adminListAwards(status: string) {
  return get<AwardRow[]>('/awards/admin', { status });
}

/** 站主：审核 */
export function auditAward(id: number, approved: boolean, rejectReason?: string) {
  return post<AwardRow>(`/awards/admin/${id}/audit`, { approved, rejectReason });
}
