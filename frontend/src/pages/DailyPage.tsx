import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Coffee, ExternalLink, Flame } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Button, Notice } from '@/components/ui/Button';
import { Countdown, OverdueNotice, StatusBadge } from '@/components/assignment/AssignmentBits';
import { fetchDailyHistory, fetchTodayDaily, markProblemDone } from '@/api/assignment';
import type { Assignment } from '@/types/assignment';

/**
 * 每日一题。
 *
 * 「今天发布了没有」「我今天完成了没有」全部以**后端按服务器时间**的判定为准，
 * 不在前端自行推算日期。
 */
export default function DailyPage() {
  const today = useQuery({ queryKey: ['daily-today'], queryFn: fetchTodayDaily });
  const history = useQuery({ queryKey: ['daily-history'], queryFn: () => fetchDailyHistory(30) });

  return (
    <>
      <PageHeader title="每日一题" description="每天一道题，坚持出成绩" />

      {today.isLoading && <Muted>加载中…</Muted>}
      {today.isError && (
        <Notice tone="danger">加载失败：{(today.error as Error).message}</Notice>
      )}

      {today.data === null && !today.isLoading && !today.isError && (
        <Card>
          <div className="flex flex-col items-center gap-2 py-12 text-center">
            <Coffee className="h-8 w-8 text-muted-foreground" />
            <div className="text-sm font-medium">今天还没有出题</div>
            <div className="text-xs text-muted-foreground">等站主发布后这里会出现今日题目</div>
          </div>
        </Card>
      )}

      {today.data && <TodayCard daily={today.data} />}

      {/* 历史 */}
      <div className="mt-6">
        <div className="mb-3 flex items-center gap-2 text-sm font-medium">
          <Flame className="h-4 w-4 text-muted-foreground" />
          历史每日一题
        </div>

        {history.isLoading && <Muted>加载中…</Muted>}
        {history.isError && <Notice tone="danger">{history.error.message}</Notice>}
        {history.data && history.data.length === 0 && <Muted>还没有历史记录</Muted>}

        {history.data && history.data.length > 0 && (
          <Card flush>
            {history.data.map((d, i) => (
              <div
                key={d.id}
                className="flex flex-wrap items-center gap-3 px-4 py-3"
                style={{
                  borderTop: i === 0 ? 'none' : '1px solid var(--border)',
                }}
              >
                <span className="w-24 flex-none text-xs tabular-nums text-muted-foreground">
                  {d.publishDate ?? '—'}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm">{d.problems[0]?.title ?? d.title}</span>
                    {d.mine && <StatusBadge status={d.mine.status} />}
                  </div>
                  {d.problems[0]?.url && (
                    <a
                      href={d.problems[0].url}
                      target="_blank"
                      rel="noreferrer"
                      className="mt-0.5 inline-flex items-center gap-1 text-[11px] text-muted-foreground hover:text-foreground"
                    >
                      {d.problems[0].platform} {d.problems[0].problemNo}
                      <ExternalLink className="h-2.5 w-2.5" />
                    </a>
                  )}
                </div>
                {d.scoreReward > 0 && (
                  <span className="flex-none text-xs text-muted-foreground">
                    {d.scoreReward} 分
                  </span>
                )}
              </div>
            ))}
          </Card>
        )}
      </div>
    </>
  );
}

/* ---------------- 今日卡片 ---------------- */

function TodayCard({ daily }: { daily: Assignment }) {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const problem = daily.problems[0];
  const mine = daily.mine;

  const mark = useMutation({
    mutationFn: (problemId: number) => markProblemDone(daily.id, problemId),
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ['daily-today'] });
      void qc.invalidateQueries({ queryKey: ['daily-history'] });
      void qc.invalidateQueries({ queryKey: ['my-assignments'] });
    },
    onError: (e: Error) => setError(e.message),
  });

  const finished = mine?.status === 'COMPLETED' || mine?.status === 'OVERDUE_DONE';
  const canSubmit = daily.timeStatus !== 'NOT_STARTED' && !!problem;

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="badge" style={{ backgroundColor: 'color-mix(in oklab, var(--warning) 25%, transparent)', color: 'var(--warning)' }}>
              今日
            </span>
            <span className="text-base font-medium">{problem?.title ?? daily.title}</span>
            {mine && <StatusBadge status={mine.status} />}
          </div>

          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
            <span>{daily.publishDate}</span>
            <span>截止 {fmt(daily.dueAt)}</span>
            {!finished && <Countdown seconds={daily.secondsUntilDue} />}
            {daily.scoreReward > 0 && <span>奖励 {daily.scoreReward} 分</span>}
          </div>

          {problem && (
            <div className="mt-3 flex flex-wrap items-center gap-3">
              <span className="badge text-[10px]">
                {problem.platform}
                {problem.problemNo ? ` ${problem.problemNo}` : ''}
              </span>
              {problem.difficulty && (
                <span className="text-xs text-muted-foreground">{problem.difficulty}</span>
              )}
              {problem.tags && (
                <span className="text-xs text-muted-foreground">{problem.tags}</span>
              )}
            </div>
          )}

          {daily.description && (
            <p className="mt-3 text-sm text-muted-foreground">{daily.description}</p>
          )}

          {mine?.status === 'OVERDUE' && (
            <div className="mt-3">
              <OverdueNotice text="今日题目已过截止时间。仍可补完，逾期是否给分由站主配置。" />
            </div>
          )}

          {error && (
            <div className="mt-3">
              <Notice tone="danger">{error}</Notice>
            </div>
          )}
        </div>

        <div className="flex flex-none flex-col items-end gap-2">
          {finished ? (
            <div
              className="flex items-center gap-1.5 text-sm"
              style={{ color: 'var(--success)' }}
            >
              <CheckCircle2 className="h-4 w-4" />
              今日已完成
            </div>
          ) : (
            <>
              {problem?.url && (
                <a href={problem.url} target="_blank" rel="noreferrer">
                  <Button size="sm">
                    去完成
                    <ExternalLink className="h-3.5 w-3.5" />
                  </Button>
                </a>
              )}
              {problem && (
                <Button
                  size="sm"
                  variant="primary"
                  disabled={!canSubmit || mark.isPending}
                  title={canSubmit ? '' : '题目尚未开始'}
                  onClick={() => mark.mutate(problem.id)}
                >
                  {mark.isPending ? '提交中…' : '标记已完成'}
                </Button>
              )}
            </>
          )}
          {mine?.scored && (
            <span className="text-[11px] text-muted-foreground">积分已结算</span>
          )}
        </div>
      </div>
    </Card>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}

function fmt(s: string | null) {
  if (!s) return '—';
  return s.slice(0, 16);
}
