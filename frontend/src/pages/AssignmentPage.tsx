import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarX2, ChevronRight, ExternalLink, Users } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Button, Notice } from '@/components/ui/Button';
import { Pill } from '@/components/ui/Badge';
import {
  Countdown,
  DoneMark,
  OverdueNotice,
  ProgressBar,
  StatusBadge,
} from '@/components/assignment/AssignmentBits';
import { fetchAssignmentDetail, fetchMyAssignments, completeAssignment, markProblemDone } from '@/api/assignment';
import type { Assignment, AssignmentType } from '@/types/assignment';
import { cn } from '@/lib/utils';

type Tab = 'ALL' | AssignmentType;

const TABS: { key: Tab; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'TEAM', label: '队伍作业' },
  { key: 'GLOBAL', label: '全体作业' },
  { key: 'DAILY', label: '每日一题' },
];

/**
 * 作业。
 *
 * ⚠️ 所有状态一律用后端返回值渲染：
 *    - timeStatus / mine.status 由后端按**服务器时间**算
 *    - secondsUntilDue 也来自后端，前端只做本地递减展示
 *    不要在这里用 Date.now() 重算逾期，客户端时钟不可信。
 */
export default function AssignmentPage() {
  const { assignmentId } = useParams();
  const [tab, setTab] = useState<Tab>('ALL');

  const detailId = assignmentId ? Number(assignmentId) : null;

  if (detailId) {
    return <AssignmentDetail id={detailId} />;
  }

  return (
    <>
      <PageHeader title="作业" description="队伍作业、全体作业与每日一题" />

      <div className="mb-4 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <Pill key={t.key} active={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}
          </Pill>
        ))}
      </div>

      <AssignmentList tab={tab} />
    </>
  );
}

/* ---------------- 列表 ---------------- */

function AssignmentList({ tab }: { tab: Tab }) {
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: ['my-assignments', tab],
    queryFn: () => fetchMyAssignments(tab === 'ALL' ? undefined : tab),
  });

  // 逾期未完成排最前，其次进行中，再未开始，最后已完成
  const sorted = useMemo(() => sortForAttention(query.data ?? []), [query.data]);

  if (query.isLoading) return <Muted>加载中…</Muted>;
  if (query.isError) return <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>;
  if (sorted.length === 0) {
    return (
      <Card>
        <div className="flex flex-col items-center gap-2 py-10 text-center">
          <CalendarX2 className="h-7 w-7 text-muted-foreground" />
          <div className="text-sm text-muted-foreground">当前没有作业</div>
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-3">
      {sorted.map((a) => (
        <Card key={a.id}>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <TypeTag type={a.type} />
                <Link to={`/assignment/${a.id}`} className="text-sm font-medium hover:underline">
                  {a.title}
                </Link>
                {a.mine && <StatusBadge status={a.mine.status} />}
              </div>

              <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                <span>{fmt(a.startAt)} 起</span>
                <span>截止 {fmt(a.dueAt)}</span>
                {a.mine && a.mine.status !== 'COMPLETED' && a.mine.status !== 'OVERDUE_DONE' && (
                  <Countdown
                    seconds={a.secondsUntilDue}
                    onExpire={() => void qc.invalidateQueries({ queryKey: ['my-assignments'] })}
                  />
                )}
                {a.scoreReward > 0 && <span>奖励 {a.scoreReward} 分</span>}
                {a.targetTeamCount > 0 && (
                  <span className="inline-flex items-center gap-1">
                    <Users className="h-3 w-3" />
                    {a.targetTeamCount} 支队伍
                  </span>
                )}
              </div>

              {a.mine && a.totalCount > 0 && (
                <div className="mt-2 max-w-xs">
                  <ProgressBar done={a.mine.completedCount} total={a.mine.totalCount} />
                </div>
              )}

              {/* 逾期未完成要显眼 */}
              {a.mine?.status === 'OVERDUE' && (
                <div className="mt-2">
                  <OverdueNotice text="已逾期且未完成，请尽快补完（逾期是否给分由站主配置决定）。" />
                </div>
              )}
            </div>

            <Link to={`/assignment/${a.id}`} className="flex-none">
              <Button size="sm">
                查看
                <ChevronRight className="h-3.5 w-3.5" />
              </Button>
            </Link>
          </div>
        </Card>
      ))}
    </div>
  );
}

/* ---------------- 详情 ---------------- */

function AssignmentDetail({ id }: { id: number }) {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ['assignment', id],
    queryFn: () => fetchAssignmentDetail(id),
  });

  const mark = useMutation({
    mutationFn: (problemId: number) => markProblemDone(id, problemId),
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ['assignment', id] });
      void qc.invalidateQueries({ queryKey: ['my-assignments'] });
    },
    onError: (e: Error) => setError(e.message),
  });

  const settle = useMutation({
    mutationFn: () => completeAssignment(id),
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ['assignment', id] });
      void qc.invalidateQueries({ queryKey: ['my-assignments'] });
    },
    onError: (e: Error) => setError(e.message),
  });

  if (query.isLoading) return <Muted>加载中…</Muted>;
  if (query.isError) return <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>;

  const a = query.data!;
  const mine = a.mine;
  const done = mine?.status === 'COMPLETED' || mine?.status === 'OVERDUE_DONE';
  // 未开始不允许提交，与服务端校验保持一致（服务端仍是最终防线）
  const canSubmit = a.timeStatus !== 'NOT_STARTED';

  /**
   * 题目完成状态是**全局**的：题在别处做过，这里就会显示已完成。
   * 于是可能出现「每题都完成了，但作业还没结算」——
   * 这时要给一个显式的结算入口，否则作业会永远停在未结算。
   */
  const needsSettle =
    !done &&
    canSubmit &&
    a.totalCount > 0 &&
    (mine?.completedCount ?? 0) >= a.totalCount &&
    mine?.rawStatus !== 'COMPLETED';

  return (
    <>
      <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
        <Link to="/assignment" className="hover:text-foreground">
          作业
        </Link>
        <ChevronRight className="h-3 w-3" />
        <span className="truncate">{a.title}</span>
      </div>

      <PageHeader
        title={a.title}
        description={a.description || undefined}
        actions={mine ? <StatusBadge status={mine.status} /> : undefined}
      />

      <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
        {/* 题目清单 */}
        <div className="space-y-3">
          {error && <Notice tone="danger">{error}</Notice>}

          {mine?.status === 'OVERDUE' && (
            <OverdueNotice text="该作业已逾期。仍可标记完成，但会记为「逾期完成」，是否给分由站主配置。" />
          )}

          {a.timeStatus === 'NOT_STARTED' && (
            <Notice>作业尚未开始，暂不能提交。开始后每道题会出现「标记完成」按钮。</Notice>
          )}

          {needsSettle && (
            <Notice tone="warning">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span>
                  这份作业的题你都已经做过了，但作业还没结算。点右侧按钮领取完成奖励。
                </span>
                <Button
                  size="sm"
                  variant="primary"
                  disabled={settle.isPending}
                  onClick={() => settle.mutate()}
                >
                  {settle.isPending ? '结算中…' : '结算完成'}
                </Button>
              </div>
            </Notice>
          )}

          <Card flush>
            <div
              className="flex items-center justify-between border-b px-4 py-3"
              style={{ borderColor: 'var(--border)' }}
            >
              <div className="text-sm font-medium">
                题目清单
                <span className="ml-2 text-xs text-muted-foreground">{a.totalCount} 道</span>
              </div>
              {mine && (
                <span className="text-xs text-muted-foreground">
                  你已完成 {mine.completedCount} 道
                </span>
              )}
            </div>

            <div>
              {a.problems.map((p, i) => (
                <div
                  key={p.id}
                  className={cn(
                    'flex flex-wrap items-center gap-3 px-4 py-3',
                    i !== a.problems.length - 1 && 'border-b',
                  )}
                  style={{ borderColor: 'var(--border)' }}
                >
                  <span className="w-5 flex-none text-xs tabular-nums text-muted-foreground">
                    {i + 1}
                  </span>
                  <DoneMark done={p.done} />

                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm">{p.title}</span>
                      <PlatformTag platform={p.platform} problemNo={p.problemNo} />
                      {p.difficulty && (
                        <span className="text-[11px] text-muted-foreground">{p.difficulty}</span>
                      )}
                    </div>
                    {p.tags && (
                      <div className="mt-0.5 text-[11px] text-muted-foreground">{p.tags}</div>
                    )}
                  </div>

                  <div className="flex flex-none items-center gap-2">
                    {/* 首次完成得分：后端按难度档位算好，前端不自己推算 */}
                    {p.score != null && !p.done && (
                      <span className="text-[11px] text-muted-foreground">{p.score} 分</span>
                    )}
                    {p.url && (
                      <a
                        href={p.url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
                      >
                        去完成
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    )}
                    {!p.done && canSubmit && (
                      <Button
                        size="sm"
                        variant="primary"
                        disabled={mark.isPending}
                        onClick={() => mark.mutate(p.id)}
                      >
                        标记完成
                      </Button>
                    )}
                    {p.done && (
                      <span className="text-xs" style={{ color: 'var(--success)' }}>
                        已完成
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>

        {/* 侧栏信息 */}
        <div className="space-y-4">
          <Card>
            <div className="space-y-3 text-sm">
              <Row label="类型" value={<TypeTag type={a.type} />} />
              <Row label="开始" value={fmt(a.startAt)} />
              <Row label="截止" value={fmt(a.dueAt)} />
              {a.publishDate && <Row label="发布日" value={a.publishDate} />}
              <Row label="奖励" value={a.scoreReward > 0 ? `${a.scoreReward} 分` : '无'} />
              <Row label="逾期给分" value={a.allowLateReward ? '给分' : '不给分'} />
              {mine && (
                <>
                  <Row label="我的进度" value={`${mine.completedCount}/${mine.totalCount}`} />
                  <Row label="奖励状态" value={mine.scored ? '已结算' : '未结算'} />
                  {mine.completedAt && <Row label="完成于" value={fmt(mine.completedAt)} />}
                </>
              )}
            </div>

            {!done && a.timeStatus !== 'NOT_STARTED' && a.totalCount > 0 && (
              <div className="mt-4">
                <ProgressBar done={a.mine?.completedCount ?? 0} total={a.totalCount} />
              </div>
            )}
          </Card>

          {a.targets.length > 0 && (
            <Card>
              <div className="mb-2 text-sm font-medium">
                目标队伍
                <span className="ml-2 text-xs text-muted-foreground">{a.targetTeamCount} 支</span>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {a.targets.map((t) => (
                  <span key={t.id} className="badge">
                    {t.name}
                  </span>
                ))}
              </div>
              <div className="mt-2 text-[11px] text-muted-foreground">
                作业范围在「发布时」确定，之后新增的队伍不会被算进来。
              </div>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

/* ---------------- 小组件 ---------------- */

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{value}</span>
    </div>
  );
}

function TypeTag({ type }: { type: AssignmentType }) {
  const map: Record<AssignmentType, { label: string; tone: 'primary' | 'default' | 'warning' }> = {
    TEAM: { label: '队伍作业', tone: 'default' },
    GLOBAL: { label: '全体作业', tone: 'primary' },
    DAILY: { label: '每日一题', tone: 'warning' },
  };
  const m = map[type];
  const style =
    m.tone === 'primary'
      ? {
          backgroundColor: 'color-mix(in oklab, var(--primary) 22%, transparent)',
          color: 'var(--primary)',
        }
      : m.tone === 'warning'
        ? {
            backgroundColor: 'color-mix(in oklab, var(--warning) 25%, transparent)',
            color: 'var(--warning)',
          }
        : {};
  return (
    <span className="badge flex-none" style={style}>
      {m.label}
    </span>
  );
}

function PlatformTag({ platform, problemNo }: { platform: string; problemNo: string | null }) {
  const label = PLATFORM_LABEL[platform] ?? platform;
  return (
    <span className="badge flex-none text-[10px]">
      {label}
      {problemNo ? ` ${problemNo}` : ''}
    </span>
  );
}

const PLATFORM_LABEL: Record<string, string> = {
  CODEFORCES: 'CF',
  ATCODER: 'AtCoder',
  NOWCODER: '牛客',
  LEETCODE: 'LeetCode',
  LUOGU: '洛谷',
  OTHER: '其他',
};

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}

/** 逾期未完成 → 进行中 → 未开始 → 逾期完成 → 已完成 */
function sortForAttention(list: Assignment[]): Assignment[] {
  const rank = (a: Assignment) => {
    switch (a.mine?.status) {
      case 'OVERDUE':
        return 0;
      case 'IN_PROGRESS':
        return 1;
      case 'NOT_STARTED':
        return 2;
      case 'OVERDUE_DONE':
        return 3;
      case 'COMPLETED':
        return 4;
      default:
        return 5;
    }
  };
  return [...list].sort((x, y) => {
    const d = rank(x) - rank(y);
    if (d !== 0) return d;
    return (x.secondsUntilDue ?? 0) - (y.secondsUntilDue ?? 0);
  });
}

function fmt(s: string | null) {
  if (!s) return '—';
  // 后端返回 "yyyy-MM-dd HH:mm:ss"
  return s.slice(0, 16);
}
