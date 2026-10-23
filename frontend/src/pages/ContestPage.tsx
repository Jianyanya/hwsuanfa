import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarDays, ExternalLink, List, Medal, RefreshCw, Trophy, Users } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Input, Notice, Select } from '@/components/ui/Button';
import { Countdown } from '@/components/assignment/AssignmentBits';
import {
  cancelIndividualJoin,
  cancelParticipation,
  confirmContestLog,
  fetchContestDetail,
  fetchContestRecords,
  fetchContests,
  joinIndividually,
  selectParticipation,
  submitContestLog,
  syncStandings,
} from '@/api/contest';
import { fetchMyTeams, fetchTeamDetail } from '@/api/team';
import { useAuth } from '@/stores/auth';
import type { TeamBrief } from '@/types/team';
import { cn } from '@/lib/utils';
import type { Contest, ContestLevel, ContestTimeStatus } from '@/types/assignment';
import { calendarToday } from '@/lib/date';

/** 比赛记录 / 比赛日历 两种视图 */
type View = 'records' | 'calendar';

/**
 * 比赛。
 *
 * 定位：比赛本体在 OJ 上，这里只记录报名决定、成绩与复盘。
 * 成绩来源：Codeforces 走官方 API 自动抓；其他平台无公开接口，手动录入。
 * 状态（未开始/进行中/已结束）一律用后端按服务器时间算的值。
 */
export default function ContestPage() {
  const { contestId } = useParams();
  const id = contestId ? Number(contestId) : null;
  const [view, setView] = useState<View>('records');

  if (id) return <ContestDetail id={id} />;

  return (
    <>
      <PageHeader
        title="比赛"
        description="比赛本体在 OJ 上，这里记录我们的报名、成绩与复盘"
        actions={
          <div className="flex gap-2">
            <Button
              size="sm"
              variant={view === 'records' ? 'primary' : 'subtle'}
              onClick={() => setView('records')}
            >
              <List className="h-3.5 w-3.5" />
              比赛记录
            </Button>
            <Button
              size="sm"
              variant={view === 'calendar' ? 'primary' : 'subtle'}
              onClick={() => setView('calendar')}
            >
              <CalendarDays className="h-3.5 w-3.5" />
              比赛日历
            </Button>
          </div>
        }
      />
      {view === 'records' ? <RecordsView /> : <CalendarView />}
    </>
  );
}

/* ==================== 视图一：比赛记录 ==================== */

const RANGES: { key: string; label: string }[] = [
  { key: '7d', label: '近 7 天' },
  { key: '1m', label: '近一个月' },
  { key: '1y', label: '近一年' },
  { key: 'all', label: '全部' },
];

function RecordsView() {
  const [range, setRange] = useState('all');
  const [platform, setPlatform] = useState('');

  const query = useQuery({
    queryKey: ['contest-records', range, platform],
    queryFn: () => fetchContestRecords(range, platform || undefined),
  });

  const records = query.data ?? [];

  return (
    <div className="space-y-4">
      <Card>
        <div className="space-y-3">
          <div>
            <div className="mb-1.5 text-xs text-muted-foreground">比赛时间</div>
            <div className="flex flex-wrap gap-2">
              {RANGES.map((r) => (
                <Pill key={r.key} active={range === r.key} onClick={() => setRange(r.key)}>
                  {r.label}
                </Pill>
              ))}
            </div>
          </div>
          <div>
            <div className="mb-1.5 text-xs text-muted-foreground">平台</div>
            <div className="flex flex-wrap gap-2">
              <Pill active={platform === ''} onClick={() => setPlatform('')}>
                全部平台
              </Pill>
              {Object.entries(PLATFORM_LABEL).map(([code, label]) => (
                <Pill key={code} active={platform === code} onClick={() => setPlatform(code)}>
                  {label}
                </Pill>
              ))}
            </div>
          </div>
          <div className="text-[11px] text-muted-foreground">
            记录包含「我以个人身份参加的」和「我所在队伍参加的」，两类成绩独立排名。
          </div>
        </div>
      </Card>

      {query.isLoading && <Muted>加载中…</Muted>}
      {query.isError && <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>}
      {!query.isLoading && !query.isError && records.length === 0 && (
        <Card>
          <div className="flex flex-col items-center gap-2 py-10 text-center">
            <Trophy className="h-7 w-7 text-muted-foreground" />
            <div className="text-sm text-muted-foreground">这个范围里没有参赛记录</div>
            <div className="text-xs text-muted-foreground">
              去「比赛日历」挑一场，以个人或队伍身份报名。
            </div>
          </div>
        </Card>
      )}

      <div className="space-y-3">
        {records.map((c) => (
          <Card key={c.id}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <LevelBadge level={c.level} />
                  <Link to={`/contest/${c.id}`} className="text-sm font-medium hover:underline">
                    {c.name}
                  </Link>
                  <TimeBadge status={c.timeStatus} />
                  {(c.myRoles ?? []).map((r) =>
                    r === 'TEAM' ? (
                      <Badge key={r} tone="primary">
                        以队伍参赛
                      </Badge>
                    ) : (
                      <Badge key={r} tone="warning">
                        以个人参赛
                      </Badge>
                    ),
                  )}
                </div>
                <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                  <span>
                    {fmt(c.startAt)} → {fmt(c.endAt)}
                  </span>
                  <span>{PLATFORM_LABEL[c.platform] ?? c.platform}</span>
                </div>
              </div>
              <Link to={`/contest/${c.id}`} className="flex-none">
                <Button size="sm">查看详情</Button>
              </Link>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

/* ==================== 视图二：比赛日历 ==================== */

function CalendarView() {
  const [status, setStatus] = useState<'ALL' | ContestTimeStatus>('ALL');
  const [platform, setPlatform] = useState('');
  const [month, setMonth] = useState(() => {
    const d = calendarToday();
    return { y: d.getFullYear(), m: d.getMonth() };
  });

  const query = useQuery({ queryKey: ['contests'], queryFn: fetchContests });
  const all = query.data ?? [];

  const filtered = all.filter(
    (c) =>
      (status === 'ALL' || c.timeStatus === status) &&
      (platform === '' || c.platform === platform),
  );

  // 每天对应的比赛，日历格子上色用
  const byDay = useMemo(() => {
    const map = new Map<string, Contest[]>();
    for (const c of filtered) {
      if (!c.startAt) continue;
      const key = c.startAt.slice(0, 10);
      map.set(key, [...(map.get(key) ?? []), c]);
    }
    return map;
  }, [filtered]);

  const first = new Date(month.y, month.m, 1);
  const daysInMonth = new Date(month.y, month.m + 1, 0).getDate();
  const leadingBlanks = (first.getDay() + 6) % 7; // 周一开头
  const todayKey = todayStr();

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
      <div className="space-y-3">
        <Card>
          <div className="space-y-3">
            <div>
              <div className="mb-1.5 text-xs text-muted-foreground">状态</div>
              <div className="flex flex-wrap gap-2">
                <Pill active={status === 'ALL'} onClick={() => setStatus('ALL')}>
                  全部
                </Pill>
                <Pill
                  active={status === 'NOT_STARTED'}
                  onClick={() => setStatus('NOT_STARTED')}
                >
                  即将开始
                </Pill>
                <Pill
                  active={status === 'IN_PROGRESS'}
                  onClick={() => setStatus('IN_PROGRESS')}
                >
                  进行中
                </Pill>
                <Pill active={status === 'ENDED'} onClick={() => setStatus('ENDED')}>
                  已结束
                </Pill>
              </div>
            </div>
            <div>
              <div className="mb-1.5 text-xs text-muted-foreground">平台</div>
              <div className="flex flex-wrap gap-2">
                <Pill active={platform === ''} onClick={() => setPlatform('')}>
                  全部平台
                </Pill>
                {Object.entries(PLATFORM_LABEL).map(([code, label]) => (
                  <Pill key={code} active={platform === code} onClick={() => setPlatform(code)}>
                    {label}
                  </Pill>
                ))}
              </div>
            </div>
          </div>
        </Card>

        {query.isLoading && <Muted>加载中…</Muted>}
        {query.isError && <Notice tone="danger">{query.error.message}</Notice>}
        {!query.isLoading && !query.isError && filtered.length === 0 && <Muted>没有符合条件的比赛</Muted>}
        {sortContests(filtered).map((c) => (
          <ContestCard key={c.id} contest={c} />
        ))}
      </div>

      {/* 月历 */}
      <Card>
        <div className="mb-2 flex items-center justify-between">
          <div className="text-sm font-medium">
            {month.y} 年 {month.m + 1} 月
          </div>
          <div className="flex gap-1">
            <Button size="sm" onClick={() => setMonth((v) => shiftMonth(v, -1))}>
              ‹
            </Button>
            <Button size="sm" onClick={() => setMonth((v) => shiftMonth(v, 1))}>
              ›
            </Button>
          </div>
        </div>
        <div className="grid grid-cols-7 gap-1 text-center text-[10px] text-muted-foreground">
          {['一', '二', '三', '四', '五', '六', '日'].map((d) => (
            <span key={d}>{d}</span>
          ))}
        </div>
        <div className="mt-1 grid grid-cols-7 gap-1">
          {Array.from({ length: leadingBlanks }).map((_, i) => (
            <span key={`b${i}`} />
          ))}
          {Array.from({ length: daysInMonth }).map((_, i) => {
            const day = i + 1;
            const key = `${month.y}-${pad2(month.m + 1)}-${pad2(day)}`;
            const items = byDay.get(key) ?? [];
            const isToday = key === todayKey;
            return (
              <div
                key={key}
                title={items.map((x) => x.name).join(' / ')}
                className="flex min-h-[34px] flex-col items-center rounded-[var(--radius)] px-0.5 py-0.5 text-[11px]"
                style={{
                  backgroundColor: items.length
                    ? 'color-mix(in oklab, var(--primary) 16%, transparent)'
                    : isToday
                      ? 'color-mix(in oklab, var(--warning) 18%, transparent)'
                      : 'transparent',
                  outline: isToday ? '1px solid var(--warning)' : 'none',
                }}
              >
                <span className="tabular-nums">{day}</span>
                {items.length > 0 && (
                  <span
                    className="mt-0.5 h-1.5 w-1.5 rounded-full"
                    style={{ backgroundColor: 'var(--primary)' }}
                  />
                )}
              </div>
            );
          })}
        </div>
        <div className="mt-2 text-[11px] text-muted-foreground">
          有比赛的日期会高亮，悬停可看比赛名。
        </div>
      </Card>
    </div>
  );
}

function ContestCard({ contest: c }: { contest: Contest }) {
  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <LevelBadge level={c.level} />
            <Link to={`/contest/${c.id}`} className="text-sm font-medium hover:underline">
              {c.name}
            </Link>
            <TimeBadge status={c.timeStatus} />
            {c.myTeamSelected && <Badge tone="success">本队参赛</Badge>}
            {(c.myRoles ?? []).includes('USER') && <Badge tone="warning">个人参赛</Badge>}
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
            <span>
              {fmt(c.startAt)} → {fmt(c.endAt)}
            </span>
            <span>{PLATFORM_LABEL[c.platform] ?? c.platform}</span>
            {c.joinScore != null && c.joinScore > 0 && <span>参赛 {c.joinScore} 分</span>}
            {c.timeStatus === 'NOT_STARTED' && (
              <Countdown seconds={c.secondsUntilStart} prefix="开赛" expiredText="已开赛" />
            )}
            {c.timeStatus === 'IN_PROGRESS' && (
              <Countdown seconds={c.secondsUntilEnd} expiredText="已结束" />
            )}
            <span className="inline-flex items-center gap-1">
              <Users className="h-3 w-3" />
              {c.teamCount} 支队伍
            </span>
          </div>
        </div>
        <Link to={`/contest/${c.id}`} className="flex-none">
          <Button size="sm">详情</Button>
        </Link>
      </div>
    </Card>
  );
}

/* ==================== 详情 ==================== */

function ContestDetail({ id }: { id: number }) {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [showLogForm, setShowLogForm] = useState(false);
  const me = useAuth((s) => s.user);
  const myTeams = useQuery({ queryKey: ['my-teams'], queryFn: fetchMyTeams });

  const query = useQuery({ queryKey: ['contest', id], queryFn: () => fetchContestDetail(id) });

  function invalidate() {
    void qc.invalidateQueries({ queryKey: ['contest', id] });
    void qc.invalidateQueries({ queryKey: ['contests'] });
    void qc.invalidateQueries({ queryKey: ['contest-records'] });
  }

  const select = useMutation({
    mutationFn: (teamIds: number[]) => selectParticipation(id, teamIds),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e: Error) => setError(e.message),
  });

  const cancel = useMutation({
    mutationFn: (teamId: number) => cancelParticipation(id, teamId),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e: Error) => setError(e.message),
  });

  const individual = useMutation({
    mutationFn: () => joinIndividually(id),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e: Error) => setError(e.message),
  });

  const cancelIndividual = useMutation({
    mutationFn: () => cancelIndividualJoin(id),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e: Error) => setError(e.message),
  });

  const sync = useMutation({
    // Codeforces 走官方 API（大比赛 30s+）；其他平台会明确提示手动录入
    mutationFn: () => syncStandings(id),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (e: Error) => setError(e.message),
  });

  if (query.isLoading) return <Muted>加载中…</Muted>;
  if (query.isError) return <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>;

  const c = query.data!;
  const logs = c.logs ?? [];
  const joinedIndividual = (c.myRoles ?? []).includes('USER');

  return (
    <>
      <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
        <Link to="/contest" className="hover:text-foreground">
          比赛
        </Link>
        <span>/</span>
        <span className="truncate">{c.name}</span>
      </div>

      <PageHeader
        title={c.name}
        actions={
          <div className="flex items-center gap-2">
            <LevelBadge level={c.level} />
            <TimeBadge status={c.timeStatus} />
            <Button
              size="sm"
              disabled={sync.isPending}
              title="Codeforces 可自动抓取官方榜单；其他平台无公开接口"
              onClick={() => {
                setError(null);
                sync.mutate();
              }}
            >
              <RefreshCw className={cn('h-3.5 w-3.5', sync.isPending && 'animate-spin')} />
              {sync.isPending ? '同步中…' : '同步榜单'}
            </Button>
          </div>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
        <span>
          {fmt(c.startAt)} → {fmt(c.endAt)}
        </span>
        {c.timeStatus === 'NOT_STARTED' && (
          <Countdown seconds={c.secondsUntilStart} prefix="开赛" expiredText="已开赛" />
        )}
        {c.timeStatus === 'IN_PROGRESS' && (
          <Countdown seconds={c.secondsUntilEnd} expiredText="已结束" />
        )}
        {c.joinScore != null && c.joinScore > 0 && <span>参赛积分 {c.joinScore} 分/人</span>}
      </div>

      {error && (
        <div className="mb-3">
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        {/* 站内排名榜 */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div className="text-sm font-medium">
              站内排名
              <span className="ml-2 text-xs text-muted-foreground">
                队伍与个人混排 · AC 降序 → 罚时升序 → 用时升序
              </span>
            </div>
            <Button size="sm" onClick={() => setShowLogForm((v) => !v)}>
              录入成绩
            </Button>
          </div>

          {showLogForm && (
            <LogForm contestId={id} teams={myTeams.data ?? []} onDone={() => setShowLogForm(false)} />
          )}

          {logs.length === 0 && (
            <Card>
              <div className="py-8 text-center text-sm text-muted-foreground">
                还没有成绩。可「同步榜单」（Codeforces 自动抓取）或「录入成绩」。
              </div>
            </Card>
          )}

          {logs.length > 0 && (
            <Card flush>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr
                      className="text-left text-[11px] text-muted-foreground"
                      style={{ borderBottom: '1px solid var(--border)' }}
                    >
                      <th className="px-3 py-2 font-normal">站内</th>
                      <th className="px-3 py-2 font-normal">选手</th>
                      <th className="px-3 py-2 text-right font-normal">AC</th>
                      <th className="px-3 py-2 text-right font-normal">用时</th>
                      <th className="px-3 py-2 text-right font-normal">罚时</th>
                      <th className="px-3 py-2 text-right font-normal">官方排名</th>
                      <th className="px-3 py-2 font-normal" />
                    </tr>
                  </thead>
                  <tbody>
                    {logs.map((l) => (
                      <tr key={l.id} style={{ borderBottom: '1px solid var(--border)' }}>
                        <td className="px-3 py-2">
                          {l.siteRank ? (
                            <span className="inline-flex items-center gap-1 font-medium tabular-nums">
                              {l.siteRank <= 3 && (
                                <Medal
                                  className="h-3.5 w-3.5"
                                  style={{
                                    color:
                                      l.siteRank === 1
                                        ? '#f5a524'
                                        : l.siteRank === 2
                                          ? '#9ca3af'
                                          : '#b87333',
                                  }}
                                />
                              )}
                              #{l.siteRank}
                            </span>
                          ) : (
                            <span className="text-muted-foreground">—</span>
                          )}
                        </td>
                        <td className="px-3 py-2">
                          {l.participantType === 'USER' ? (
                            <span className="flex items-center gap-2">
                              {l.userAvatar ? (
                                <img
                                  src={l.userAvatar}
                                  alt=""
                                  className="h-6 w-6 rounded-full object-cover"
                                />
                              ) : (
                                <span
                                  className="flex h-6 w-6 items-center justify-center rounded-full text-[11px]"
                                  style={{
                                    backgroundColor:
                                      'color-mix(in oklab, var(--primary) 20%, transparent)',
                                    color: 'var(--primary)',
                                  }}
                                >
                                  {(l.userNickname ?? '?').slice(0, 1)}
                                </span>
                              )}
                              <span>{l.userNickname}</span>
                              <span className="badge text-[10px]">个人</span>
                            </span>
                          ) : (
                            <span className="flex items-center gap-2">
                              <span
                                className="flex h-6 w-6 items-center justify-center rounded-full text-[11px]"
                                style={{
                                  backgroundColor:
                                    'color-mix(in oklab, var(--warning) 22%, transparent)',
                                  color: 'var(--warning)',
                                }}
                              >
                                队
                              </span>
                              <span>{l.teamName ?? `队伍 #${l.teamId}`}</span>
                              {l.members.length > 0 && (
                                <span className="text-[11px] text-muted-foreground">
                                  {l.members.map((m) => m.nickname).join('、')}
                                </span>
                              )}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums">{l.acCount ?? '—'}</td>
                        <td className="px-3 py-2 text-right tabular-nums">
                          {fmtDuration(l.durationSeconds)}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums">
                          {l.penaltyMinutes != null ? `${l.penaltyMinutes} 分` : '—'}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums">{l.rankResult ?? '—'}</td>
                        <td className="px-3 py-2 text-right">
                          {l.status === 'PENDING' ? (
                            canConfirm(l, me?.id, myTeams.data ?? []) ? (
                              <Button
                                size="sm"
                                variant="primary"
                                onClick={() =>
                                  confirmContestLog(id, l.id)
                                    .then(() => {
                                      setError(null);
                                      invalidate();
                                    })
                                    .catch((e: Error) => setError(e.message))
                                }
                              >
                                确认
                              </Button>
                            ) : (
                              <Badge tone="warning">待确认</Badge>
                            )
                          ) : null}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}
        </div>

        {/* 侧栏：报名与链接 */}
        <div className="space-y-4">
          <Card>
            <div className="mb-2 text-sm font-medium">
              参赛队伍
              <span className="ml-2 text-xs text-muted-foreground">{c.teamCount} 支</span>
            </div>
            {c.teams.length === 0 && (
              <div className="py-3 text-center text-xs text-muted-foreground">还没有队伍报名</div>
            )}
            <div className="space-y-1.5">
              {c.teams.map((t) => (
                <div key={t.id} className="flex items-center justify-between gap-2 text-sm">
                  <span>{t.name}</span>
                  {myTeams.data?.some((mt) => mt.id === t.id) && c.myTeamSelected && (
                    <button
                      type="button"
                      className="text-[11px] text-muted-foreground hover:text-[var(--destructive)]"
                      onClick={() =>
                        cancel.mutate(t.id, {
                          onSuccess: () => setError(null),
                          onError: (e: Error) => setError(e.message),
                        })
                      }
                    >
                      取消队伍报名
                    </button>
                  )}
                </div>
              ))}
            </div>

            {c.myTeamSelected ? (
              <div className="mt-2">
                <Notice>本队已报名。参赛积分按「首次报名」发放，取消后重新报名不会补发。</Notice>
              </div>
            ) : (
              (myTeams.data ?? []).length > 0 &&
              c.timeStatus !== 'ENDED' && (
                <div className="mt-3">
                  <Button
                    size="sm"
                    variant="primary"
                    disabled={select.isPending}
                    onClick={() => {
                      setError(null);
                      select.mutate((myTeams.data ?? []).map((t) => t.id));
                    }}
                  >
                    {select.isPending ? '提交中…' : '以队伍参赛'}
                  </Button>
                  <div className="mt-1.5 text-[11px] text-muted-foreground">
                    参赛后全队每人得 {c.joinScore ?? 0} 分。
                  </div>
                </div>
              )
            )}

            {/* 个人参赛 */}
            <div className="mt-3 border-t pt-3" style={{ borderColor: 'var(--border)' }}>
              <div className="mb-1.5 text-xs font-medium">以个人身份参赛</div>
              {joinedIndividual ? (
                <>
                  <Notice>你已个人报名，成绩将独立排名。</Notice>
                  {c.timeStatus !== 'ENDED' && (
                    <Button
                      size="sm"
                      variant="ghost"
                      className="mt-2"
                      disabled={cancelIndividual.isPending}
                      onClick={() => {
                        setError(null);
                        cancelIndividual.mutate();
                      }}
                    >
                      取消个人报名
                    </Button>
                  )}
                </>
              ) : c.individualJoinAllowed === false ? (
                <div className="text-[11px] text-muted-foreground">
                  你的队伍已报名这场比赛，成员不能再以个人身份参加。
                </div>
              ) : (
                c.timeStatus !== 'ENDED' && (
                  <>
                    <Button
                      size="sm"
                      variant="subtle"
                      disabled={individual.isPending}
                      onClick={() => {
                        setError(null);
                        individual.mutate();
                      }}
                    >
                      {individual.isPending ? '提交中…' : '以个人身份参赛'}
                    </Button>
                    <div className="mt-1.5 text-[11px] text-muted-foreground">
                      个人报名后若队长再给队伍报名，你的成绩将独立展示、不占队伍名额。
                    </div>
                  </>
                )
              )}
            </div>
          </Card>

          {c.url && (
            <Card>
              <a
                href={c.url}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 text-sm hover:underline"
              >
                打开比赛页面
                <ExternalLink className="h-3.5 w-3.5" />
              </a>
              <div className="mt-1 text-[11px] text-muted-foreground">
                比赛本体在 OJ 上，这里只记录报名与成绩。
              </div>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

/** 能否确认日志：本队队长（或站主）。按钮显隐用，后端才是最终防线 */
function canConfirm(
  log: { teamId: number | null },
  myId: number | undefined,
  myTeams: TeamBrief[],
): boolean {
  if (!myId) return false;
  return myTeams.some((t) => t.id === log.teamId && t.leaderUserId === myId);
}

/* ==================== 录入成绩 ==================== */

function LogForm({
  contestId,
  teams,
  onDone,
}: {
  contestId: number;
  teams: TeamBrief[];
  onDone: () => void;
}) {
  const qc = useQueryClient();
  const [teamId, setTeamId] = useState<number | ''>('');
  const [memberIds, setMemberIds] = useState<number[]>([]);
  const [rankResult, setRankResult] = useState('');
  const [acCount, setAcCount] = useState('');
  const [duration, setDuration] = useState('');
  const [penalty, setPenalty] = useState('');
  const [error, setError] = useState<string | null>(null);

  const chosen = teams.find((t) => t.id === Number(teamId));

  // 队伍列表接口不含成员，选中后单独拉详情
  const detail = useQuery({
    queryKey: ['team-detail', teamId],
    queryFn: () => fetchTeamDetail(Number(teamId)),
    enabled: teamId !== '',
  });
  const candidateMembers = detail.data?.members ?? [];
  const viewerIsLeader = detail.data?.viewerIsLeader === true;

  const submit = useMutation({
    mutationFn: () =>
      submitContestLog(contestId, {
        teamId: Number(teamId),
        memberIds,
        rankResult: rankResult.trim() || undefined,
        acCount: acCount ? Number(acCount) : undefined,
        durationSeconds: duration ? Number(duration) : undefined,
        penaltyMinutes: penalty ? Number(penalty) : undefined,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['contest', contestId] });
      void qc.invalidateQueries({ queryKey: ['contests'] });
      onDone();
    },
    onError: (e: Error) => setError(e.message),
  });

  if (teams.length === 0) {
    return (
      <Card>
        <Notice>
          你还没有加入任何队伍，队伍成绩需要队长填写；个人成绩请先在侧栏以个人身份报名。
        </Notice>
      </Card>
    );
  }

  return (
    <Card>
      <div className="space-y-3">
        <div className="text-sm font-medium">录入队伍成绩</div>

        <Select
          label="队伍"
          name="logTeam"
          value={teamId}
          onChange={(e) => {
            setTeamId(e.target.value === '' ? '' : Number(e.target.value));
            setMemberIds([]);
          }}
        >
          <option value="">选择队伍…</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </Select>

        {chosen && (
          <div>
            <div className="mb-1 text-xs text-muted-foreground">
              实际参赛成员（已个人报名的成员会自动排除）
            </div>
            <div className="flex flex-wrap gap-2">
              {candidateMembers.length === 0 && (
                <span className="text-xs text-muted-foreground">该队伍没有成员信息</span>
              )}
              {candidateMembers.map((m) => {
                const on = memberIds.includes(m.userId);
                return (
                  <button
                    key={m.userId}
                    type="button"
                    onClick={() =>
                      setMemberIds((v) =>
                        on ? v.filter((x) => x !== m.userId) : [...v, m.userId],
                      )
                    }
                    className="rounded-[var(--radius)] border px-2.5 py-1 text-xs"
                    style={{
                      borderColor: on ? 'var(--primary)' : 'var(--border)',
                      backgroundColor: on
                        ? 'color-mix(in oklab, var(--primary) 14%, transparent)'
                        : 'transparent',
                    }}
                  >
                    {m.nickname}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <div className="grid gap-3 sm:grid-cols-2">
          <Input
            label="官方排名（如 12/230）"
            name="rankResult"
            value={rankResult}
            onChange={(e) => setRankResult(e.target.value)}
            maxLength={64}
          />
          <Input
            label="AC 题数"
            name="acCount"
            type="number"
            min={0}
            value={acCount}
            onChange={(e) => setAcCount(e.target.value)}
          />
          <Input
            label="用时（秒）"
            name="durationSeconds"
            type="number"
            min={0}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
          />
          <Input
            label="罚时（分钟）"
            name="penaltyMinutes"
            type="number"
            min={0}
            value={penalty}
            onChange={(e) => setPenalty(e.target.value)}
          />
        </div>

        {error && <Notice tone="danger">{error}</Notice>}

        <div className="flex gap-2">
          <Button
            size="sm"
            variant="primary"
            disabled={teamId === '' || memberIds.length === 0 || submit.isPending}
            onClick={() => {
              setError(null);
              submit.mutate();
            }}
          >
            {submit.isPending ? '提交中…' : viewerIsLeader ? '提交并确认' : '提交（待队长确认）'}
          </Button>
          <Button size="sm" onClick={onDone}>
            取消
          </Button>
        </div>
      </div>
    </Card>
  );
}

/* ==================== 小组件 ==================== */

function LevelBadge({ level }: { level: ContestLevel }) {
  const tone =
    level === 'S'
      ? 'var(--destructive)'
      : level === 'A'
        ? 'var(--primary)'
        : level === 'B'
          ? 'var(--warning)'
          : 'var(--muted-foreground)';
  return (
    <span
      className="badge flex-none"
      style={{
        backgroundColor: `color-mix(in oklab, ${tone} 22%, transparent)`,
        color: tone,
      }}
    >
      {level === 'OTHER' ? '其他' : `${level} 级`}
    </span>
  );
}

function TimeBadge({ status }: { status: ContestTimeStatus }) {
  if (status === 'NOT_STARTED') return <Badge>未开始</Badge>;
  if (status === 'ENDED') return <Badge>已结束</Badge>;
  return <Badge tone="success">进行中</Badge>;
}

const PLATFORM_LABEL: Record<string, string> = {
  CODEFORCES: 'Codeforces',
  ATCODER: 'AtCoder',
  NOWCODER: '牛客',
  LEETCODE: 'LeetCode',
  LUOGU: '洛谷',
  OTHER: '其他',
};

/** 进行中在前，其次未开始，最后已结束 */
function sortContests(list: Contest[]): Contest[] {
  const rank = (c: Contest) =>
    c.timeStatus === 'IN_PROGRESS' ? 0 : c.timeStatus === 'NOT_STARTED' ? 1 : 2;
  return [...list].sort((a, b) => {
    const d = rank(a) - rank(b);
    if (d !== 0) return d;
    return (a.secondsUntilStart ?? 0) - (b.secondsUntilStart ?? 0);
  });
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}

function fmt(s: string | null) {
  if (!s) return '—';
  return s.slice(0, 16);
}

function pad2(n: number) {
  return String(n).padStart(2, '0');
}

function shiftMonth(v: { y: number; m: number }, delta: number) {
  const d = new Date(v.y, v.m + delta, 1);
  return { y: d.getFullYear(), m: d.getMonth() };
}

function todayStr() {
  const d = calendarToday();
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** 秒 → 2:05:30 */
function fmtDuration(seconds: number | null) {
  if (seconds == null) return '—';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return `${h}:${pad2(m)}:${pad2(seconds % 60)}`;
}
