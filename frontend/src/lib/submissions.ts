import type { MySubmission } from '@/types/assignment';

export function countAcceptedProblems(submissions: readonly MySubmission[], day?: string) {
  const problemIds = new Map<string, number>();
  const problemKey = (item: MySubmission) => `${item.platform?.trim().toUpperCase() ?? ''}:${item.problemNo?.trim().toUpperCase() ?? ''}`;
  for (const item of submissions) {
    if (item.problemId != null && item.problemNo?.trim()) problemIds.set(problemKey(item), item.problemId);
  }
  const accepted = new Set<string>();
  for (const item of submissions) {
    if (item.result !== 'AC' || (day && item.submittedAt.slice(0, 10) !== day)) continue;
    const key = problemKey(item);
    const id = item.problemId ?? problemIds.get(key);
    accepted.add(id != null ? `id:${id}` : item.problemNo?.trim() ? key : `submission:${item.id}`);
  }
  return accepted.size;
}

export function countSubmissionDays(submissions: readonly { submittedAt: string }[]) {
  const daily = new Map<string, number>();
  for (const item of submissions) {
    const day = item.submittedAt.slice(0, 10);
    daily.set(day, (daily.get(day) ?? 0) + 1);
  }
  return daily;
}
