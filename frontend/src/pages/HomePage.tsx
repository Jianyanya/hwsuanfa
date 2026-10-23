import { lazy, Suspense, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Megaphone } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button, Notice } from '@/components/ui/Button';
import { fetchBulletins } from '@/api/community';
import { fetchRank } from '@/api/rank';
import { fetchTodayDaily, fetchMyAssignments } from '@/api/assignment';
import { fetchInsights, fetchMySubmissions } from '@/api/submissions';
import { fetchMyScore } from '@/api/score';
import { fetchTeamDetail } from '@/api/team';
import { useAuth } from '@/stores/auth';
import { calendarToday, localDateKey, shanghaiDateKey } from '@/lib/date';
import { countAcceptedProblems, countSubmissionDays } from '@/lib/submissions';

const HomeInsights = lazy(() => import('@/components/home/HomeInsights').then((module) => ({
  default: module.HomeInsights,
})));

/**
 * 首页 —— 严格对齐 design/prototype/index.html 的布局：
 * 今日一题醒目卡 → 数据统计 6 联排 → 待完成作业/积分趋势/热力图 → 右栏队伍/公告/最近完成
 * → 刷题画像（能力雷达/平台过题/难度分布/标签词云/标签 AC Top）。
 */
export default function HomePage() {
  const me = useAuth((s) => s.user);
  const activeTeamId = useAuth((s) => s.activeTeamId);
  const currentTeam = me?.teams.find((team) => team.id === activeTeamId) ?? me?.teams[0];

  const daily = useQuery({ queryKey: ['today-daily'], queryFn: fetchTodayDaily, enabled: !!me });
  const rank = useQuery({ queryKey: ['rank'], queryFn: fetchRank, enabled: !!me });
  const subs = useQuery({ queryKey: ['my-submissions'], queryFn: fetchMySubmissions, enabled: !!me });
  const score = useQuery({ queryKey: ['my-score'], queryFn: fetchMyScore, enabled: !!me });
  const teamDetail = useQuery({
    queryKey: ['team-detail', currentTeam?.id],
    queryFn: () => fetchTeamDetail(currentTeam!.id),
    enabled: !!me && !!currentTeam,
  });
  const assignments = useQuery({
    queryKey: ['my-assignments-all'],
    queryFn: () => fetchMyAssignments('ALL'),
    enabled: !!me,
  });
  const bulletins = useQuery({ queryKey: ['bulletins'], queryFn: fetchBulletins });
  const insights = useQuery({
    queryKey: ['insights', me?.id],
    queryFn: fetchInsights,
    enabled: !!me,
    staleTime: 0,
    refetchOnWindowFocus: true,
  });

  const myRank = rank.data?.users.find((u) => u.userId === me?.id);
  const myTeam = rank.data?.teams.find((team) => team.teamId === currentTeam?.id);
  const teammates = teamDetail.data?.members ?? [];
  const memberScores = useMemo(() => new Map(rank.data?.users.map((user) => [user.userId, user.totalScore]) ?? []), [rank.data?.users]);
  const pendingAssignments = (assignments.data ?? []).filter(
    (a) => a.mine && a.mine.rawStatus !== 'COMPLETED',
  );
  const recentAc = (subs.data ?? []).filter((s) => s.result === 'AC').slice(0, 4);

  const todayKey = shanghaiDateKey();
  const monthKey = todayKey.slice(0, 7);
  const solvedCount = useMemo(() => countAcceptedProblems(subs.data ?? []), [subs.data]);
  const acToday = useMemo(() => countAcceptedProblems(subs.data ?? [], todayKey), [subs.data, todayKey]);
  const monthScore = (score.data?.events ?? [])
    .filter((e) => e.createdAt.slice(0, 7) === monthKey)
    .reduce((sum, e) => sum + Number(e.score), 0);

  return (
    <>
      <PageHeader
        title={`你好，${me?.nickname ?? '队员'}`}
        description="今天要做什么，一眼看清"
      />
      {!me && <div className="mb-4"><Notice><Link to="/login" className="text-primary underline">登录</Link>后查看你的训练记录和能力画像。</Notice></div>}
      {[daily, subs, score, assignments].some((query) => query.isError) && <div className="mb-4"><Notice tone="danger">部分训练数据加载失败，请稍后刷新重试。</Notice></div>}

      {/* 今日一题 醒目卡 */}
      <div
        className="card mb-4 p-5"
        style={{ borderColor: 'color-mix(in oklab, var(--primary) 35%, var(--border))' }}
      >
        {daily.isLoading ? (
          <Muted>加载中…</Muted>
        ) : !daily.data ? (
          <div className="flex items-center gap-3">
            <div className="text-sm text-muted-foreground">
              今天还没有发布每日一题，去训练页面看看别的 ☕
            </div>
            <Link to="/problemset">
              <Button size="sm">去刷题</Button>
            </Link>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-4">
            <div
              className="flex h-11 w-11 flex-none items-center justify-center rounded-lg"
              style={{ background: 'var(--primary)' }}
            >
              <span style={{ color: 'var(--primary-foreground)' }}>📝</span>
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">
                  今日一题 · {Number(todayKey.slice(5, 7))}月{Number(todayKey.slice(8, 10))}日
                </span>
                {daily.data.timeStatus === 'OVERDUE' ? (
                  <Badge tone="destructive">已截止</Badge>
                ) : (
                  <Badge tone="warning">进行中</Badge>
                )}
                {daily.data.mine?.rawStatus === 'COMPLETED' && <Badge tone="success">已完成</Badge>}
              </div>
              <div className="mt-1 text-lg font-semibold">{daily.data.title}</div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                {daily.data.problems?.[0]?.platform ?? ''} · 截止 {daily.data.dueAt?.slice(5, 16)} ·
                奖励 {daily.data.scoreReward} 分
              </div>
            </div>
            <Link to="/daily" className="flex-none">
              <Button size="sm" variant="primary">去完成</Button>
            </Link>
          </div>
        )}
      </div>

      {/* 数据统计 6 联排 */}
      <div className="mb-4 text-base font-semibold">我的数据统计</div>
      <div className="mb-6 grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6">
        <StatCard label="生涯" sub="累计积分" value={Number(score.data?.total ?? myRank?.totalScore ?? 0).toFixed(0)} />
        <StatCard label="总题数" sub="累计通过" value={subs.data ? solvedCount : insights.data?.totalAc ?? 0} />
        <StatCard label="今日" sub="今日完成" value={acToday} />
        <StatCard label="本月" sub="本月积分" value={monthScore.toFixed(0)} />
        <StatCard
          label="排名"
          sub="全站排名"
          value={myRank ? `#${myRank.rank}` : '—'}
        />
        <StatCard
          label="作业"
          sub="待完成作业"
          value={pendingAssignments.length}
          danger={pendingAssignments.length > 0}
        />
      </div>

      <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-3">
        {/* 左 2/3 */}
        <div className="min-w-0 space-y-4 lg:col-span-2">
          {/* 待完成作业 */}
          <Card>
            <div className="mb-3 flex items-center justify-between">
              <div className="text-base font-semibold">待完成作业</div>
              <Link to="/assignment" className="text-xs text-muted-foreground hover:text-foreground">
                查看全部
              </Link>
            </div>
            {assignments.isLoading ? <Muted>加载中…</Muted> : assignments.isError ? <Notice tone="danger">{assignments.error.message}</Notice> : pendingAssignments.length === 0 && (
              <Muted>全部完成，干得漂亮 🎉</Muted>
            )}
            <div className="space-y-2">
              {pendingAssignments.slice(0, 3).map((a) => {
                const done = a.mine?.completedCount ?? 0;
                const total = a.totalCount || 1;
                const overdue = a.timeStatus === 'OVERDUE';
                return (
                  <Link to={`/assignment/${a.id}`} key={a.id} className="block">
                    <div
                      className="flex items-center gap-3 rounded-md p-3"
                      style={{ border: '1px solid var(--border)' }}
                    >
                      {overdue ? (
                        <Badge tone="destructive">已逾期</Badge>
                      ) : a.timeStatus === 'NOT_STARTED' ? (
                        <Badge>未开始</Badge>
                      ) : (
                        <Badge tone="warning">进行中</Badge>
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-sm font-medium">{a.title}</div>
                        <div className="text-xs text-muted-foreground">
                          {a.type === 'TEAM' ? '队伍作业' : a.type === 'DAILY' ? '每日一题' : '全体作业'} ·{' '}
                          {a.totalCount} 题 · 截止 {a.dueAt.slice(5, 16)} · +{a.scoreReward} 分
                        </div>
                      </div>
                      <div className="w-20 text-right">
                        <div className="text-xs text-muted-foreground">
                          {done}/{a.totalCount}
                        </div>
                        <div
                          className="mt-1 h-1.5 w-full overflow-hidden rounded-full"
                          style={{ backgroundColor: 'var(--muted)' }}
                        >
                          <div
                            className="h-full rounded-full"
                            style={{
                              width: `${Math.round((done / total) * 100)}%`,
                              backgroundColor: 'var(--primary)',
                            }}
                          />
                        </div>
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>
          </Card>

          {/* 积分趋势 */}
          <TrendCard events={score.data?.events ?? []} />

          {/* 做题热力图 */}
          <HeatmapCard submissions={subs.data ?? []} />

        </div>

        {/* 右 1/3 */}
        <div className="min-w-0 space-y-4">
          {/* 队伍卡片 */}
          {currentTeam && (
            <Card>
              <div className="mb-3 flex items-center justify-between">
                <div className="text-base font-semibold">{teamDetail.data?.name ?? currentTeam.name}</div>
                <Link to={`/team/${currentTeam.id}`} className="text-xs text-muted-foreground hover:text-foreground">
                  队伍详情
                </Link>
              </div>
              <div className="grid grid-cols-3 gap-2 text-center">
                <div>
                  <div className="text-xl font-bold">{myTeam ? `#${myTeam.rank}` : '—'}</div>
                  <div className="text-[11px] text-muted-foreground">全站排名</div>
                </div>
                <div>
                  <div className="text-xl font-bold">{myTeam ? Number(myTeam.totalScore).toFixed(0) : '—'}</div>
                  <div className="text-[11px] text-muted-foreground">队伍积分</div>
                </div>
                <div>
                  <div className="text-xl font-bold">
                    {teamDetail.data?.memberCount ?? myTeam?.memberCount ?? '—'}
                    {teamDetail.data && <span className="text-xs font-normal text-muted-foreground">/{teamDetail.data.maxMembers}</span>}
                  </div>
                  <div className="text-[11px] text-muted-foreground">成员数</div>
                </div>
              </div>
              <div className="mt-3 border-t pt-3" style={{ borderColor: 'var(--border)' }}>
                <div className="mb-2 text-xs text-muted-foreground">当前队伍成员</div>
                {teamDetail.isLoading && <MutedSmall>队伍成员加载中…</MutedSmall>}
                {teamDetail.isError && <Notice tone="danger">{teamDetail.error.message}</Notice>}
                <div className="space-y-2">
                  {teammates.map((u) => (
                    <div key={u.userId} className="flex items-center gap-2 text-sm">
                      <span
                        className="badge"
                        style={{
                          background: 'var(--muted)',
                          color: 'var(--foreground)',
                        }}
                      >
                        {u.leader ? '队长' : '队员'}
                      </span>
                      <span className="flex-1 truncate">{u.nickname}</span>
                      <span className="text-xs text-muted-foreground">
                        {u.userId === me?.id && score.data ? Number(score.data.total).toFixed(0)
                          : memberScores.has(u.userId) ? Number(memberScores.get(u.userId)).toFixed(0) : '未公开积分'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </Card>
          )}

          {/* 公告 */}
          <Card>
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2 text-base font-semibold">
                <Megaphone className="h-4 w-4" />
                公告
              </div>
              <Link to="/bulletin" className="text-xs text-muted-foreground hover:text-foreground">
                查看全部
              </Link>
            </div>
            <div className="space-y-1">
              {bulletins.data?.slice(0, 3).map((b) => (
                <Link to={`/bulletin/${b.id}`} key={b.id} className="block">
                  <div
                    className="flex items-center gap-2 py-2.5 last:border-0"
                    style={{ borderBottom: '1px solid var(--border)' }}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm">
                        {b.pinned && (
                          <span className="mr-1 text-[10px]" style={{ color: 'var(--warning)' }}>
                            置顶
                          </span>
                        )}
                        {b.title}
                      </div>
                      <div className="text-[11px] text-muted-foreground">
                        {b.createdAt.slice(5, 16)}
                      </div>
                    </div>
                  </div>
                </Link>
              ))}
              {bulletins.data?.length === 0 && <MutedSmall>暂无公告</MutedSmall>}
            </div>
          </Card>

          {/* 最近完成 */}
          <Card>
            <div className="mb-3 text-base font-semibold">最近完成</div>
            {recentAc.length === 0 && (
              <MutedSmall>还没有 AC 记录，去刷题吧</MutedSmall>
            )}
            <div className="space-y-2.5">
              {recentAc.map((s) => (
                <div key={s.id} className="flex items-start gap-2">
                  <span
                    className="mt-0.5 flex-none"
                    style={{ color: 'oklch(75% .16 150)', fontSize: 14 }}
                  >
                    ✓
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-mono text-sm">{s.problemNo}</div>
                    <div className="text-[11px] text-muted-foreground">
                      {s.submittedAt.slice(0, 10)} · {s.platform === 'CODEFORCES' ? 'CF' : s.platform === 'ATCODER' ? 'AtCoder' : s.platform === 'LUOGU' ? '洛谷' : s.platform}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>
      <Suspense fallback={<Card className="mt-6"><Muted>刷题画像加载中…</Muted></Card>}>
        <HomeInsights insights={insights.data} loading={insights.isLoading}
          error={insights.isError ? insights.error.message : null} />
      </Suspense>
    </>
  );
}

/* ==================== 统计卡 ==================== */

function StatCard({ label, sub, value, danger }: {
  label: string; sub: string; value: string | number; danger?: boolean;
}) {
  return (
    <div className="card p-4">
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>{label}</span>
        <span>{sub}</span>
      </div>
      <div
        className="stat-num mt-1"
        style={{ fontSize: '1.6rem', fontWeight: 700, color: danger ? 'var(--destructive)' : 'inherit' }}
      >
        {value}
      </div>
    </div>
  );
}

/* ==================== 积分趋势（SVG 折线） ==================== */

function TrendCard({ events }: { events: { score: number; createdAt: string }[] }) {
  // 近 30 天逐日累计
  const days: { key: string; cum: number }[] = [];
  const now = calendarToday();
  for (let i = 29; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(d.getDate() - i);
    days.push({ key: localDateKey(d), cum: 0 });
  }
  const byDay = new Map<string, number>();
  for (const e of events) {
    const key = e.createdAt.slice(0, 10);
    byDay.set(key, (byDay.get(key) ?? 0) + Number(e.score));
  }
  let acc = 0;
  for (const d of days) {
    acc += byDay.get(d.key) ?? 0;
    d.cum = acc;
  }
  const max = Math.max(1, ...days.map((d) => d.cum));
  const min = Math.min(0, ...days.map((d) => d.cum));
  const W = 700; const H = 160;
  const pts = days.map((d, i) => {
    const x = (i / (days.length - 1)) * W;
    const y = H - 12 - ((d.cum - min) / (max - min)) * (H - 32);
    return `${x},${y}`;
  });

  return (
    <Card className="min-w-0">
      <div className="mb-3 flex items-center justify-between">
        <div>
          <div className="text-base font-semibold">个人积分趋势</div>
          <div className="text-xs text-muted-foreground">近 30 天累计积分变化</div>
        </div>
      </div>
      <svg className="block max-w-full" viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 160 }}>
        <defs>
          <linearGradient id="trend-g" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.28" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[20, 60, 100, 140].map((y) => (
          <line key={y} x1="0" y1={y} x2={W} y2={y} stroke="oklch(100% 0 0 / .08)" />
        ))}
        {days.length > 1 && (
          <>
            <path
              d={`M${pts.join(' L')} L${W},${H} L0,${H} Z`}
              fill="url(#trend-g)"
            />
            <path
              d={`M${pts.join(' L')}`}
              fill="none"
              stroke="var(--primary)"
              strokeWidth="2.5"
              strokeLinejoin="round"
            />
          </>
        )}
      </svg>
      <div className="mt-1 flex justify-between text-[11px] text-muted-foreground">
        <span>{days[0]?.key.slice(5)}</span>
        <span>今天</span>
      </div>
    </Card>
  );
}

/* ==================== 做题热力图（紧凑版） ==================== */

function HeatmapCard({ submissions }: { submissions: { submittedAt: string; result: string }[] }) {
  const daily = useMemo(() => {
    return countSubmissionDays(submissions);
  }, [submissions]);

  const today = calendarToday();
  const cells: { key: string; count: number }[] = [];
  const start = new Date(today);
  start.setDate(start.getDate() - 27 * 7);
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
  const cursor = new Date(start);
  while (cursor <= today) {
    const key = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}-${String(cursor.getDate()).padStart(2, '0')}`;
    cells.push({ key, count: daily.get(key) ?? 0 });
    cursor.setDate(cursor.getDate() + 1);
  }
  const colors = [
    'color-mix(in oklab, var(--muted) 55%, transparent)',
    'color-mix(in oklab, var(--success) 25%, transparent)',
    'color-mix(in oklab, var(--success) 50%, transparent)',
    'color-mix(in oklab, var(--success) 75%, transparent)',
    'var(--success)',
  ];
  const level = (c: number) => (c === 0 ? 0 : c <= 2 ? 1 : c <= 4 ? 2 : c <= 6 ? 3 : 4);
  const cols: (typeof cells)[] = [];
  for (let i = 0; i < cells.length; i += 7) cols.push(cells.slice(i, i + 7));

  return (
    <Card className="min-w-0">
      <div className="mb-3 flex items-center justify-between">
        <div className="text-base font-semibold">做题热力图</div>
        <Badge>提交</Badge>
      </div>
      <div className="flex min-w-0 w-full max-w-full gap-[3px] overflow-x-auto pb-1">
        {cols.map((week, wi) => (
          <div key={wi} className="flex shrink-0 flex-col gap-[3px]">
            {week.map((cell) => (
              <div
                key={cell.key}
                title={`${cell.key}: ${cell.count} 次提交`}
                className="h-[11px] w-[11px] rounded-[2px]"
                style={{ backgroundColor: colors[level(cell.count)] }}
              />
            ))}
          </div>
        ))}
      </div>
      <div className="mt-3 flex items-center justify-between text-[11px] text-muted-foreground">
        <div className="flex gap-3">
          <span>周一</span><span>周三</span><span>周五</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span>少</span>
          {colors.map((c, i) => (
            <i key={i} className="h-[10px] w-[10px] rounded-[2px]" style={{ backgroundColor: c }} />
          ))}
          <span>多</span>
        </div>
      </div>
    </Card>
  );
}


function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-4 text-center text-sm text-muted-foreground">{children}</div>;
}

function MutedSmall({ children }: { children: React.ReactNode }) {
  return <div className="py-3 text-center text-xs text-muted-foreground">{children}</div>;
}
