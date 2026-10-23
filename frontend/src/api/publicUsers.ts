import { get } from '@/lib/http';

export interface PublicUser {
  id: number;
  nickname: string;
  avatar: string | null;
  privacyLevel: 'PARTIAL' | 'PUBLIC';
  className?: string | null;
  cfHandle?: string | null;
  atcoderHandle?: string | null;
  luoguHandle?: string | null;
  leetcodeHandle?: string | null;
  nowcoderHandle?: string | null;
  qojHandle?: string | null;
}

export interface PublicUserDetail {
  user: PublicUser;
  stats: { solvedCount: number; submissionCount: number; totalScore: number };
  awards: Array<{ id: number; awardName: string; contestName: string | null; awardLevel: string | null; awardedAt: string | null; certUrl: string | null; note: string | null }>;
  submissions: Array<{ id: number; platform: string; problemNo: string; title: string | null; url: string | null; language: string | null; code: string | null; submittedAt: string | null }>;
}

export function fetchPublicUser(id: number | string) {
  return get<PublicUserDetail>(`/public/users/${id}`);
}

export function searchPublicUsers(keyword: string) {
  return get<Array<{ id: number; nickname: string; avatar: string | null }>>('/public/users', { keyword });
}
