import { del, get, post, put } from '@/lib/http';
import type {
  ImportPayload,
  ListScope,
  ParsePreview,
  ProblemList,
  ProblemListPayload,
} from '@/types/assignment';

/* ---------------- 题单 ---------------- */

export function fetchMyProblemLists() {
  return get<ProblemList[]>('/problem-lists');
}

export function fetchProblemListDetail(id: number) {
  return get<ProblemList>(`/problem-lists/${id}`);
}

export function createProblemList(body: ProblemListPayload) {
  return post<ProblemList>('/problem-lists', body);
}

export function updateProblemList(id: number, body: ProblemListPayload) {
  return put<ProblemList>(`/problem-lists/${id}`, body);
}

export function deleteProblemList(id: number) {
  return del<void>(`/problem-lists/${id}`);
}

/* ---------------- 题目 ---------------- */

export function importProblems(listId: number, body: ImportPayload) {
  return post<ProblemList>(`/problem-lists/${listId}/problems`, body);
}

export function removeProblem(listId: number, problemId: number) {
  return del<ProblemList>(`/problem-lists/${listId}/problems/${problemId}`);
}

export function reorderProblems(listId: number, problemIds: number[]) {
  return put<ProblemList>(`/problem-lists/${listId}/order`, { problemIds });
}

/* ---------------- 审核 ---------------- */

export function auditProblemList(id: number, approve: boolean, rejectReason?: string) {
  return post<ProblemList>(`/problem-lists/${id}/audit`, { approve, rejectReason });
}

/* ---------------- 粘贴解析预览 ---------------- */

/**
 * 只解析不落库。
 * 粘贴后先让用户看清楚「能识别出什么、哪几行没认出来」，
 * 避免粘了一大段结果发现一半没进去。
 */
export function parsePreview(text: string, defaultPlatform?: string) {
  return post<ParsePreview>('/problem-lists/parse-preview', { text, defaultPlatform });
}

/* ---------------- 站主 ---------------- */

export function adminListProblemLists(scope?: ListScope) {
  return get<ProblemList[]>('/admin/problem-lists', scope ? { scope } : undefined);
}
