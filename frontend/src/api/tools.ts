import { get, post, put, del } from '@/lib/http';

/** 粘贴板（short_link 表） */
export interface PasteRow {
  id: number;
  code: string;
  title: string | null;
  contentType: string;
  content: string;
  language: string | null;
  expiresAt: string | null;
  viewCount: number;
  createdAt: string;
}

export function createPaste(body: {
  content: string;
  title?: string;
  contentType?: string;
  language?: string;
  expireDays?: number;
}) {
  return post<{ code: string }>('/tools/pastes', body);
}

export function fetchPaste(code: string) {
  return get<PasteRow>(`/tools/pastes/${code}`);
}

export function fetchMyPastes() {
  return get<PasteRow[]>('/tools/pastes/mine');
}

export function deletePaste(code: string) {
  return del<void>(`/tools/pastes/${code}`);
}

/* ---------- 友情链接 ---------- */

export interface FriendlyLinkRow {
  id: number;
  name: string;
  url: string;
  logo: string | null;
  description: string | null;
  sortOrder: number;
}

export function fetchLinks() {
  return get<FriendlyLinkRow[]>('/tools/links');
}

export function createLink(body: { name: string; url: string; description?: string; sortOrder?: number }) {
  return post<FriendlyLinkRow>('/tools/links', body);
}

export function updateLink(id: number, body: { name: string; url: string; description?: string; sortOrder?: number }) {
  return put<FriendlyLinkRow>(`/tools/links/${id}`, body);
}

export function deleteLink(id: number) {
  return del<void>(`/tools/links/${id}`);
}
