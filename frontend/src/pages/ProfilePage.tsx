import { useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CloudDownload, LogOut, Pencil, Save, Shield, Trophy, Upload,
} from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Notice, Select } from '@/components/ui/Button';
import { bindHandles, changePassword, fetchMyProfile, updateMe } from '@/api/profile';
import { fetchMySubmissions, importSubmissions, syncSubmissions, type SubmissionImportItem } from '@/api/submissions';
import { fetchMyScore } from '@/api/score';
import { deleteAward, fetchMyAwards, submitAward, updateAward, uploadAwardCertificate } from '@/api/awards';
import { useAuth } from '@/stores/auth';
import { clearSession } from '@/lib/http';
import { calendarToday } from '@/lib/date';
import type { MySubmission, Profile } from '@/types/assignment';

/* ==================== 平台元数据 ==================== */

const PLATFORMS: {
  code: string;
  label: string;
  handleKey: string;
  color: string;
  capability: 'auto' | 'script' | 'manual';
}[] = [
  { code: 'ATCODER', label: 'AtCoder', handleKey: 'atcoderHandle', color: '#4bc0c8', capability: 'auto' },
  { code: 'NOWCODER', label: '牛客', handleKey: 'nowcoderHandle', color: '#7dd3fc', capability: 'manual' },
  { code: 'CODEFORCES', label: 'Codeforces', handleKey: 'cfHandle', color: '#60a5fa', capability: 'auto' },
  { code: 'LUOGU', label: '洛谷', handleKey: 'luoguHandle', color: '#4ade80', capability: 'script' },
  { code: 'LEETCODE', label: '力扣', handleKey: 'leetcodeHandle', color: '#fbbf24', capability: 'manual' },
  { code: 'QOJ', label: 'QOJ', handleKey: 'qojHandle', color: '#c084fc', capability: 'manual' },
];

const PLATFORM_LABEL: Record<string, string> = {
  CODEFORCES: 'Codeforces', ATCODER: 'AtCoder', LUOGU: '洛谷',
  NOWCODER: '牛客', LEETCODE: '力扣', QOJ: 'QOJ',
};

const PLATFORM_COLOR: Record<string, string> = {
  CODEFORCES: '#60a5fa', ATCODER: '#4bc0c8', LUOGU: '#4ade80',
  NOWCODER: '#7dd3fc', LEETCODE: '#fbbf24', QOJ: '#c084fc',
};

const RESULT_LABEL: Record<string, string> = {
  AC: 'Accepted', WA: 'Wrong Answer', TLE: 'Time Limit',
  MLE: 'Memory Limit', RE: 'Runtime Error', CE: 'Compile Error',
};

const RESULT_COLOR: Record<string, string> = {
  AC: 'var(--success)', WA: 'var(--destructive)', TLE: '#f5a524',
  MLE: '#f5a524', RE: '#fb7185', CE: '#a78bfa', OTHER: 'var(--muted-foreground)',
};

const CAP_NOTE: Record<string, string> = {
  auto: '一键同步', script: '油猴脚本同步', manual: '手动登记',
};

const EVENT_LABEL: Record<string, string> = {
  FIRST_AC: '首次完成', DAILY_SOLVE: '每日一题', ASSIGNMENT_DONE: '完成作业',
  CONTEST_JOIN: '参加比赛', CONTEST_RANK: '比赛名次', MANUAL_ADJUST: '手动调整',
};

const HEAT_COLORS = [
  'color-mix(in oklab, var(--muted) 55%, transparent)',
  'color-mix(in oklab, var(--success) 25%, transparent)',
  'color-mix(in oklab, var(--success) 50%, transparent)',
  'color-mix(in oklab, var(--success) 75%, transparent)',
  'var(--success)',
];

function dayKey(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function useDailyCounts(submissions: MySubmission[]) {
  return useMemo(() => {
    const map = new Map<string, number>();
    for (const sub of submissions) {
      const key = sub.submittedAt.slice(0, 10);
      map.set(key, (map.get(key) ?? 0) + 1);
    }
    return map;
  }, [submissions]);
}

/* ==================== 主页（仪表盘） ==================== */

export default function ProfilePage() {
  return <ProfileBody />;
}

function ProfileBody() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchMyProfile });
  const subs = useQuery({ queryKey: ['my-submissions'], queryFn: fetchMySubmissions });
  const awards = useQuery({ queryKey: ['my-awards'], queryFn: fetchMyAwards });

  if (profile.isLoading || subs.isLoading) return <Muted>加载中…</Muted>;
  if (profile.isError) return <Notice tone="danger">加载失败：{(profile.error as Error).message}</Notice>;
  if (subs.isError) return <Notice tone="danger">做题记录加载失败：{subs.error.message}</Notice>;

  const publicAwards = (awards.data ?? []).filter(
    (a) => a.auditStatus === 'APPROVED' && a.isPublic,
  );

  return (
    <>
      <PageHeader
        title="个人资料"
        description="做题数据、平台绑定与刷题动态"
      />
      <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-[300px_minmax(0,1fr)]">
        <IdentityCard profile={profile.data!} submissions={subs.data ?? []} />
        <div className="min-w-0 space-y-4">
          <StatsCard submissions={subs.data ?? []} />
          <HeatmapCard submissions={subs.data ?? []} />
          <ActivityCard submissions={subs.data ?? []} />
          <ScoreCard />
          <AwardsCard awards={publicAwards} />
          {awards.isError && <Notice tone="danger">获奖记录加载失败：{awards.error.message}</Notice>}
        </div>
      </div>
    </>
  );
}

/* ==================== 左栏：身份卡 ==================== */

function IdentityCard({ profile, submissions }: { profile: Profile; submissions: MySubmission[] }) {
  const navigate = useNavigate();
  const counts = useMemo(() => {
    const map: Record<string, number> = {};
    for (const sub of submissions) {
      map[sub.platform] = (map[sub.platform] ?? 0) + 1;
    }
    return map;
  }, [submissions]);

  return (
    <Card className="min-w-0">
      <div className="flex flex-col items-center py-2 text-center">
        {profile.avatar ? (
          <img src={profile.avatar} alt="" className="h-20 w-20 rounded-full object-cover" />
        ) : (
          <div
            className="flex h-20 w-20 items-center justify-center rounded-full text-2xl font-medium"
            style={{
              backgroundColor: 'color-mix(in oklab, var(--primary) 18%, transparent)',
              color: 'var(--primary)',
            }}
          >
            {(profile.nickname ?? '?').slice(0, 1)}
          </div>
        )}
        <div className="mt-3 text-lg font-semibold">{profile.nickname}</div>
        <div className="text-xs text-muted-foreground">@{profile.username}</div>
        {profile.className && (
          <div className="mt-1 text-xs text-muted-foreground">{profile.className}</div>
        )}
      </div>

      <div className="mt-3 border-t pt-3" style={{ borderColor: 'var(--border)' }}>
        <div className="mb-2 text-xs font-medium text-muted-foreground">OJ 账号</div>
        <div className="flex flex-wrap gap-1.5">
          {PLATFORMS.map((p) => {
            const handle = profile[p.handleKey as keyof Profile] as string | null;
            const count = counts[p.code] ?? 0;
            return (
              <span
                key={p.code}
                className="rounded-[var(--radius)] border px-2 py-1 text-[11px]"
                style={{
                  borderColor: handle ? p.color : 'var(--border)',
                  color: handle ? p.color : 'var(--muted-foreground)',
                }}
                title={handle ? `${p.label}: ${handle}` : `${p.label}: 未绑定`}
              >
                {p.label}
                {handle && ` (${count})`}
              </span>
            );
          })}
        </div>
      </div>

      <div className="mt-4 space-y-2">
        <Button
          size="sm"
          variant="primary"
          className="w-full"
          onClick={() => navigate('/profile/settings')}
        >
          <Pencil className="h-3.5 w-3.5" />
          编辑资料与账号设置
        </Button>
        <SyncAllButton />
        <Button
          size="sm"
          variant="ghost"
          className="w-full"
          onClick={() => useAuth.getState().logout()}
        >
          <LogOut className="h-3.5 w-3.5" />
          退出登录
        </Button>
      </div>

      <div className="mt-3 text-center text-[11px] text-muted-foreground">
        学号 {profile.username} · {profile.realName ?? ''}
      </div>
    </Card>
  );
}

/** 一键刷新全部可自动同步的平台（限流 3 次/天/平台） */
function SyncAllButton() {
  const qc = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchMyProfile });

  async function run() {
    setBusy(true);
    setMsg(null);
    const parts: string[] = [];
    try {
      const p = profile.data!;
      const targets: [string, string][] = [];
      if (p.cfHandle) targets.push(['CODEFORCES', p.cfHandle]);
      if (p.atcoderHandle) targets.push(['ATCODER', p.atcoderHandle]);
      if (targets.length === 0) {
        setMsg('请先绑定 Codeforces 或 AtCoder 用户名');
        return;
      }
      for (const [platform] of targets) {
        const r = await syncSubmissions(platform);
        parts.push(`${platform === 'CODEFORCES' ? 'CF' : 'AtCoder'} 新增 ${r.inserted}`);
      }
      setMsg(`已刷新：${parts.join('，')}`);
      void qc.invalidateQueries({ queryKey: ['my-submissions'] });
    } catch (e) {
      setMsg(e instanceof Error ? e.message : '同步失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <Button size="sm" className="w-full" disabled={busy || !profile.data} onClick={run}>
        <CloudDownload className="h-3.5 w-3.5" />
        {busy ? '正在刷新…' : '刷新做题记录'}
      </Button>
      {msg && <div className="mt-1.5 text-center text-[11px] text-muted-foreground">{msg}</div>}
    </div>
  );
}

/* ==================== 右栏：刷题数据 ==================== */

function StatsCard({ submissions }: { submissions: MySubmission[] }) {
  const daily = useDailyCounts(submissions);
  const today = dayKey(calendarToday());

  const inLastDays = (days: number) => {
    const from = calendarToday();
    from.setDate(from.getDate() - days + 1);
    const fromKey = dayKey(from);
    let n = 0;
    for (const [key, count] of daily) {
      if (key >= fromKey && key <= today) n += count;
    }
    return n;
  };

  const stats: [string, number][] = [
    ['今日 AC', daily.get(today) ?? 0],
    ['本周 AC', inLastDays(7)],
    ['本月 AC', inLastDays(30)],
    ['累计 AC', submissions.filter((x) => x.result === 'AC').length],
    ['累计提交', submissions.length],
  ];

  return (
    <Card>
      <div className="mb-2 text-sm font-medium">🔥 刷题数据</div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
        {stats.map(([label, value]) => (
          <div
            key={label}
            className="rounded-[var(--radius)] border px-3 py-2"
            style={{ borderColor: 'var(--border)' }}
          >
            <div className="text-[11px] text-muted-foreground">{label}</div>
            <div className="text-xl font-semibold tabular-nums">{value}</div>
          </div>
        ))}
      </div>
    </Card>
  );
}

/* ==================== 热力图（力扣样式） ==================== */

function HeatmapCard({ submissions }: { submissions: MySubmission[] }) {
  const daily = useDailyCounts(submissions);
  const [year, setYear] = useState<number | 'past'>('past');
  const todayKey = dayKey(calendarToday());
  const today = useMemo(() => new Date(`${todayKey}T12:00:00`), [todayKey]);

  const years = useMemo(() => {
    const set = new Set<number>();
    for (const sub of submissions) {
      set.add(Number(sub.submittedAt.slice(0, 4)));
    }
    set.add(today.getFullYear());
    return [...set].sort((a, b) => b - a);
  }, [submissions, today]);

  const { weekCols, monthMarks, rangeLabel, totalCount, activeDays, streak } = useMemo(() => {
    let from: Date;
    let to: Date;
    if (year === 'past') {
      to = new Date(today);
      from = new Date(today);
      from.setDate(from.getDate() - 364);
    } else {
      from = new Date(year, 0, 1);
      to = new Date(year, 11, 31);
    }
    const rangeStart = new Date(from);
    from.setDate(from.getDate() - ((from.getDay() + 6) % 7));

    const cells: { key: string; count: number; inFuture: boolean }[] = [];
    const marks: { label: string; weekIndex: number }[] = [];
    const cursor = new Date(from);
    let lastMonth = -1;
    let weekIndex = -1;
    const total = Math.round((to.getTime() - from.getTime()) / 86400000) + 1;
    for (let i = 0; i < total; i++) {
      if (cursor.getDay() === 1) weekIndex++;
      if (cursor.getMonth() !== lastMonth) {
        lastMonth = cursor.getMonth();
        marks.push({ label: `${lastMonth + 1}月`, weekIndex: Math.max(weekIndex, 0) });
      }
      cells.push({
        key: dayKey(cursor),
        count: daily.get(dayKey(cursor)) ?? 0,
        inFuture: cursor > today || cursor < rangeStart || (year !== 'past' && cursor.getFullYear() !== year),
      });
      cursor.setDate(cursor.getDate() + 1);
    }

    let totalCount = 0;
    let activeDays = 0;
    let maxStreak = 0;
    let curStreak = 0;
    for (const c of cells) {
      if (c.inFuture) continue;
      const n = daily.get(c.key) ?? 0;
      totalCount += n;
      if (n > 0) {
        activeDays++;
        curStreak++;
        maxStreak = Math.max(maxStreak, curStreak);
      } else if (c.key <= dayKey(today)) {
        curStreak = 0;
      }
    }

    const cols: { key: string; count: number; inFuture: boolean }[][] = [];
    for (let i = 0; i < cells.length; i += 7) {
      cols.push(cells.slice(i, i + 7));
    }
    return {
      weekCols: cols,
      monthMarks: marks,
      rangeLabel: year === 'past' ? '过去一年' : `${year} 年`,
      totalCount,
      activeDays,
      streak: maxStreak,
    };
  }, [daily, year, today]);

  const level = (count: number) =>
    count === 0 ? 0 : count <= 2 ? 1 : count <= 4 ? 2 : count <= 6 ? 3 : 4;

  const maxWeekIndex = monthMarks.length
    ? monthMarks[monthMarks.length - 1].weekIndex + 1
    : weekCols.length;

  return (
    <Card className="min-w-0">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-muted-foreground">
          <span>
            {rangeLabel}共提交{' '}
            <strong className="text-foreground tabular-nums">{totalCount}</strong> 次
          </span>
          <span>累计提交天数: <strong className="text-foreground tabular-nums">{activeDays}</strong></span>
          <span>连续提交: <strong className="text-foreground tabular-nums">{streak}</strong></span>
        </div>
        <select
          className="rounded-[var(--radius)] border bg-transparent px-2 py-1 text-xs outline-none"
          style={{ borderColor: 'var(--input)', color: 'var(--foreground)' }}
          value={year === 'past' ? 'past' : String(year)}
          onChange={(e) => setYear(e.target.value === 'past' ? 'past' : Number(e.target.value))}
        >
          <option value="past">过去一年</option>
          {years.map((y) => (
            <option key={y} value={y}>{y} 年</option>
          ))}
        </select>
      </div>

      <div className="min-w-0 w-full max-w-full overflow-x-auto pb-1">
        <div style={{ width: maxWeekIndex * 14 }}>
          <div className="flex gap-[3px]">
            {weekCols.slice(0, maxWeekIndex).map((week, wi) => (
              <div key={wi} className="flex flex-col gap-[3px]">
                {week.map((cell) => (
                  <div
                    key={cell.key}
                    title={`${cell.key}: ${cell.count} 次 AC`}
                    className="h-[11px] w-[11px] rounded-[2px]"
                    style={{
                      backgroundColor: cell.inFuture ? 'transparent' : HEAT_COLORS[level(cell.count)],
                    }}
                  />
                ))}
              </div>
            ))}
          </div>
          <div className="relative mt-1.5 h-5">
            {monthMarks.map((m, i) => {
              const next = monthMarks[i + 1];
              const width = ((next ? next.weekIndex : maxWeekIndex) - m.weekIndex) * 14;
              // 宽度放不下文字就跳过标签，避免重叠
              if (width < 30) return null;
              return (
                <span
                  key={m.label + i}
                  className="absolute top-0 text-[11px] text-muted-foreground"
                  style={{ left: m.weekIndex * 14, width }}
                >
                  {m.label}
                </span>
              );
            })}
          </div>
        </div>
      </div>
      <div className="mt-2 flex items-center justify-end gap-1 text-[11px] text-muted-foreground">
        少
        {HEAT_COLORS.map((c, i) => (
          <span key={i} className="h-[10px] w-[10px] rounded-[2px]" style={{ backgroundColor: c }} />
        ))}
        多
      </div>
    </Card>
  );
}

/* ==================== 刷题动态（滑轨） ==================== */

function ActivityCard({ submissions }: { submissions: MySubmission[] }) {
  const recent = useMemo(
    () => [...submissions].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt)),
    [submissions],
  );
  const [filter, setFilter] = useState<'ALL' | 'AC' | 'NOT_AC'>('ALL');
  const shown = recent.filter((x) =>
    filter === 'ALL' ? true : filter === 'AC' ? x.result === 'AC' : x.result !== 'AC',
  );

  return (
    <Card>
      <div className="mb-2 flex items-center justify-between">
        <div className="text-sm font-medium">
          刷题动态
          <span className="ml-2 text-xs text-muted-foreground">{shown.length} 条</span>
        </div>
        <div className="flex gap-1.5">
          <Pill active={filter === 'ALL'} onClick={() => setFilter('ALL')}>全部</Pill>
          <Pill active={filter === 'AC'} onClick={() => setFilter('AC')}>仅 AC</Pill>
          <Pill active={filter === 'NOT_AC'} onClick={() => setFilter('NOT_AC')}>未通过</Pill>
        </div>
      </div>

      {shown.length === 0 && (
        <div className="py-6 text-center text-xs text-muted-foreground">
          还没有刷题记录，先在左栏绑定平台并点「刷新做题记录」
        </div>
      )}

      <div
        className="max-h-[420px] space-y-2 overflow-y-auto pr-1"
        style={{ scrollbarWidth: 'thin' }}
      >
        {shown.map((sub) => (
          <div
            key={sub.id}
            className="flex items-center justify-between gap-2 rounded-[var(--radius)] px-2 py-1.5 text-xs"
            style={{ backgroundColor: 'color-mix(in oklab, var(--muted) 35%, transparent)' }}
          >
            <div className="flex min-w-0 flex-wrap items-center gap-1.5">
              <span
                className="rounded px-1.5 py-0.5 text-[10px]"
                style={{
                  backgroundColor: `color-mix(in oklab, ${PLATFORM_COLOR[sub.platform] ?? 'var(--primary)'} 18%, transparent)`,
                  color: PLATFORM_COLOR[sub.platform] ?? 'var(--primary)',
                }}
              >
                {PLATFORM_LABEL[sub.platform] ?? sub.platform}
              </span>
              {sub.submissionUrl ? (
                <a href={sub.submissionUrl} target="_blank" rel="noreferrer" className="font-mono hover:underline">
                  {sub.problemNo}
                </a>
              ) : (
                <span className="font-mono">{sub.problemNo}</span>
              )}
              <span
                className="rounded px-1.5 py-0.5 text-[10px]"
                style={{
                  backgroundColor: `color-mix(in oklab, ${RESULT_COLOR[sub.result] ?? 'var(--muted-foreground)'} 18%, transparent)`,
                  color: RESULT_COLOR[sub.result] ?? 'var(--muted-foreground)',
                }}
              >
                {RESULT_LABEL[sub.result] ?? sub.result}
              </span>
              {sub.isFirstAc && <Badge tone="warning">首次完成</Badge>}
            </div>
            <span className="flex-none text-[11px] text-muted-foreground">
              {sub.submittedAt.slice(0, 16)}
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}

/* ==================== 积分明细 ==================== */

function ScoreCard() {
  const query = useQuery({ queryKey: ['my-score'], queryFn: fetchMyScore });
  const events = query.data?.events ?? [];

  return (
    <Card>
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-medium">
          <Trophy className="h-4 w-4" />
          积分明细
        </div>
        <span className="text-lg font-semibold tabular-nums" style={{ color: 'var(--primary)' }}>
          {Number(query.data?.total ?? 0).toFixed(1)} 分
        </span>
      </div>
      {query.isLoading && <Muted>加载中…</Muted>}
      {events.length === 0 && (
        <div className="py-4 text-center text-xs text-muted-foreground">还没有积分记录</div>
      )}
      <div className="max-h-64 space-y-1.5 overflow-y-auto pr-1" style={{ scrollbarWidth: 'thin' }}>
        {events.map((e, i) => (
          <div
            key={i}
            className="flex items-center justify-between gap-2 rounded-[var(--radius)] px-2 py-1.5 text-xs"
            style={{ backgroundColor: 'color-mix(in oklab, var(--muted) 35%, transparent)' }}
          >
            <span className="min-w-0 flex-1 truncate">
              <span className="badge mr-1.5 text-[10px]">{EVENT_LABEL[e.eventType] ?? e.eventType}</span>
              {e.reason}
            </span>
            <span className="flex-none flex items-center gap-2">
              <span
                className="font-medium tabular-nums"
                style={{ color: e.score >= 0 ? 'var(--success)' : 'var(--destructive)' }}
              >
                {e.score >= 0 ? '+' : ''}
                {Number(e.score).toFixed(1)}
              </span>
              <span className="text-[11px] text-muted-foreground">{e.createdAt.slice(0, 10)}</span>
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}

/* ==================== 获奖展示（主页） ==================== */

function AwardsCard({ awards }: {
  awards: {
    id: number; awardName: string; awardLevel: string | null;
    contestName: string | null; awardedAt: string | null;
  }[];
}) {
  return (
    <Card>
      <div className="mb-2 flex items-center gap-2 text-sm font-medium">
        🏅 获奖记录
        <span className="text-xs text-muted-foreground">{awards.length} 条</span>
      </div>
      {awards.length === 0 && (
        <div className="py-4 text-center text-xs text-muted-foreground">
          还没有公示的获奖记录。在「编辑资料与账号设置 → 获奖记录」里提交，站主审核通过后展示。
        </div>
      )}
      <div className="space-y-1.5">
        {awards.map((a) => (
          <div
            key={a.id}
            className="flex items-center justify-between gap-2 rounded-[var(--radius)] px-2 py-1.5 text-xs"
            style={{ backgroundColor: 'color-mix(in oklab, var(--muted) 35%, transparent)' }}
          >
            <span className="min-w-0 flex-1 truncate">
              <span className="mr-1.5">🏅</span>
              {a.awardName}
              {a.awardLevel && <span className="ml-1.5 badge text-[10px]">{a.awardLevel}</span>}
              {a.contestName && (
                <span className="ml-1.5 text-[11px] text-muted-foreground">@ {a.contestName}</span>
              )}
            </span>
            <span className="flex-none text-[11px] text-muted-foreground">{a.awardedAt ?? ''}</span>
          </div>
        ))}
      </div>
    </Card>
  );
}

/* ==================== 设置页（独立视图） ==================== */

export function ProfileSettingsPage() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchMyProfile });
  const subs = useQuery({ queryKey: ['my-submissions'], queryFn: fetchMySubmissions });
  const awards = useQuery({ queryKey: ['my-awards'], queryFn: fetchMyAwards });

  const counts = useMemo(() => {
    const map: Record<string, number> = {};
    for (const sub of subs.data ?? []) {
      map[sub.platform] = (map[sub.platform] ?? 0) + 1;
    }
    return map;
  }, [subs.data]);

  if (profile.isLoading || subs.isLoading) return <Muted>加载中…</Muted>;
  if (profile.isError) return <Notice tone="danger">加载失败：{(profile.error as Error).message}</Notice>;
  if (subs.isError) return <Notice tone="danger">做题记录加载失败：{subs.error.message}</Notice>;

  return (
    <>
      <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
        <Link to="/profile" className="hover:text-foreground">个人资料</Link>
        <span>/</span>
        <span>编辑资料与账号设置</span>
      </div>
      <PageHeader
        title="编辑资料与账号设置"
        description="头像、昵称、联系方式、OJ 绑定、隐私与获奖记录"
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <ProfileFormCard profile={profile.data!} />
        <PasswordCard />

        <Card className="lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <div className="text-sm font-medium">绑定 OJ</div>
            <span className="text-[11px] text-muted-foreground">
              填用户名即可（不需要密码）；「已过 N 题」为站内已同步的 AC 数
            </span>
          </div>
          <BindOJGrid profile={profile.data!} counts={counts} />
        </Card>

        <Card className="lg:col-span-2">
          <div className="mb-3 text-sm font-medium">🏆 获奖记录</div>
          <AwardSection awards={awards.data ?? []} />
        </Card>

        <ManualImportCard />
      </div>
    </>
  );
}

function ProfileFormCard({ profile }: { profile: Profile }) {
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [nickname, setNickname] = useState(profile.nickname ?? '');
  const [email, setEmail] = useState(profile.email ?? '');
  const [avatar, setAvatar] = useState(profile.avatar ?? '');
  const [qq, setQq] = useState(profile.qq ?? '');
  const [wechat, setWechat] = useState(profile.wechat ?? '');
  const [phone, setPhone] = useState(profile.phone ?? '');
  const [privacy, setPrivacy] = useState(profile.privacyLevel ?? 'PARTIAL');
  const [showCode, setShowCode] = useState(profile.showCode ?? false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  function pickAvatar(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const scale = Math.min(1, 256 / Math.max(img.width, img.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
        setAvatar(canvas.toDataURL('image/jpeg', 0.85));
      };
      img.src = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  const save = useMutation({
    mutationFn: () => updateMe({ nickname, avatar, email, qq, wechat, phone, privacyLevel: privacy, showCode }),
    onSuccess: (updated) => {
      setError(null);
      setSaved(true);
      // 用响应直接刷新缓存：左栏身份卡立刻跟着变（不等重新请求）
      qc.setQueryData(['profile'], updated);
      void qc.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (e: Error) => {
      setSaved(false);
      setError(e.message);
    },
  });

  return (
    <Card>
      <div className="mb-3 flex items-center gap-2 text-sm font-medium">
        <Pencil className="h-4 w-4" />
        资料设置
      </div>

      <div className="mb-3">
        <div className="mb-1 text-xs text-muted-foreground">头像</div>
        <div className="flex items-center gap-3">
          {avatar ? (
            <img src={avatar} alt="" className="h-14 w-14 rounded-full object-cover" />
          ) : (
            <div
              className="flex h-14 w-14 items-center justify-center rounded-full text-xl"
              style={{
                backgroundColor: 'color-mix(in oklab, var(--primary) 18%, transparent)',
                color: 'var(--primary)',
              }}
            >
              {(profile.nickname ?? '?').slice(0, 1)}
            </div>
          )}
          <div>
            <Button size="sm" onClick={() => fileRef.current?.click()}>上传图片</Button>
            {avatar && (
              <button
                type="button"
                className="ml-2 text-[11px] text-muted-foreground hover:text-[var(--destructive)]"
                onClick={() => setAvatar('')}
              >
                移除
              </button>
            )}
            <div className="mt-1 text-[10px] text-muted-foreground">jpg/png/webp，自动压缩</div>
          </div>
          <input
            ref={fileRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) pickAvatar(f);
              e.target.value = '';
            }}
          />
        </div>
      </div>

      <div className="mb-3 grid gap-3 sm:grid-cols-2">
        <div>
          <div className="mb-1 text-xs text-muted-foreground">真实姓名（注册后不可修改）</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)', opacity: 0.6 }}
            value={profile.realName ?? ''}
            disabled
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">站内名称（昵称，可修改）</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            maxLength={32}
          />
        </div>
      </div>

      <div className="mb-3">
        <div className="mb-1 text-xs text-muted-foreground">邮箱</div>
        <input
          className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
          style={{ borderColor: 'var(--input)' }}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="选填"
          maxLength={64}
        />
      </div>

      <div className="mb-3 grid gap-3 sm:grid-cols-3">
        <div>
          <div className="mb-1 text-xs text-muted-foreground">QQ</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={qq}
            onChange={(e) => setQq(e.target.value)}
            maxLength={32}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">微信</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={wechat}
            onChange={(e) => setWechat(e.target.value)}
            maxLength={64}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">手机号</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            maxLength={20}
          />
        </div>
      </div>

      <div className="mb-3">
        <div className="mb-1 text-xs text-muted-foreground">个人隐私</div>
        <Select name="privacyLevel" value={privacy} onChange={(e) => setPrivacy(e.target.value)}>
          <option value="PUBLIC">公开 —— 全站可见我的资料与动态</option>
          <option value="PARTIAL">部分私密 —— 隐藏班级等细节</option>
          <option value="PRIVATE">完全私密 —— 不上排行榜，他人不可见</option>
        </Select>
      </div>

      <label className="mb-4 flex cursor-pointer items-start gap-2 text-sm">
        <input
          type="checkbox"
          className="mt-0.5 h-4 w-4 accent-[var(--primary)]"
          checked={showCode}
          onChange={(e) => setShowCode(e.target.checked)}
        />
        <span>
          <span className="block">允许公开我的提交代码</span>
          <span className="text-xs text-muted-foreground">仅在隐私档位为“公开”时生效</span>
        </span>
      </label>

      {error && <Notice tone="danger">{error}</Notice>}
      {saved && <Notice>已保存</Notice>}

      <Button
        size="sm"
        variant="primary"
        disabled={save.isPending}
        onClick={() => {
          setError(null);
          save.mutate();
        }}
      >
        <Save className="h-3.5 w-3.5" />
        {save.isPending ? '保存中…' : '保存资料'}
      </Button>
    </Card>
  );
}

/** 绑定 OJ 卡片网格（GoAlgo 风格） */
function BindOJGrid({ profile, counts }: { profile: Profile; counts: Record<string, number> }) {
  const qc = useQueryClient();
  const [handles, setHandles] = useState<Record<string, string>>({
    CODEFORCES: profile.cfHandle ?? '',
    ATCODER: profile.atcoderHandle ?? '',
    LUOGU: profile.luoguHandle ?? '',
    NOWCODER: profile.nowcoderHandle ?? '',
    LEETCODE: profile.leetcodeHandle ?? '',
    QOJ: profile.qojHandle ?? '',
  });
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () => bindHandles({
      cfHandle: handles.CODEFORCES,
      atcoderHandle: handles.ATCODER,
      luoguHandle: handles.LUOGU,
      nowcoderHandle: handles.NOWCODER,
      leetcodeHandle: handles.LEETCODE,
      qojHandle: handles.QOJ,
    }),
    onSuccess: () => {
      setError(null);
      setSaved(true);
      void qc.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (e: Error) => {
      setSaved(false);
      setError(e.message);
    },
  });

  return (
    <div>
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {PLATFORMS.map((p) => (
          <div
            key={p.code}
            className="rounded-[var(--radius)] border p-3"
            style={{ borderColor: 'var(--border)' }}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium" style={{ color: p.color }}>
                {p.label}
              </span>
              <span className="text-[10px] text-muted-foreground">{CAP_NOTE[p.capability]}</span>
            </div>
            <input
              className="mt-2 w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1 text-xs outline-none"
              style={{ borderColor: 'var(--input)' }}
              value={handles[p.code] ?? ''}
              onChange={(e) => {
                setSaved(false);
                setHandles((v) => ({ ...v, [p.code]: e.target.value }));
              }}
              placeholder="用户名"
            />
            <div className="mt-1.5 text-[11px] text-muted-foreground">
              已过 {counts[p.code] ?? 0} 题
            </div>
          </div>
        ))}
      </div>
      {error && <div className="mt-3"><Notice tone="danger">{error}</Notice></div>}
      {saved && <div className="mt-3"><Notice>已保存</Notice></div>}
      <Button
        size="sm"
        variant="primary"
        className="mt-3"
        disabled={save.isPending}
        onClick={() => {
          setError(null);
          save.mutate();
        }}
      >
        <Save className="h-3.5 w-3.5" />
        {save.isPending ? '保存中…' : '保存绑定'}
      </Button>
      <div className="mt-2 text-[11px] text-muted-foreground">
        力扣 / QOJ / 牛客暂无公开抓取接口，成绩手动登记；洛谷用油猴脚本同步。
      </div>
    </div>
  );
}

function ManualImportCard() {
  const qc = useQueryClient();
  const [platform, setPlatform] = useState('LUOGU');
  const [text, setText] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: (records: SubmissionImportItem[]) => importSubmissions(platform, records),
    onSuccess: (result) => {
      setText('');
      setError(null);
      setMessage(`已导入 ${result.inserted} 条提交，新增首次完成 ${result.firstAcGranted} 条。`);
      void qc.invalidateQueries({ queryKey: ['my-submissions'] });
      void qc.invalidateQueries({ queryKey: ['profile'] });
      void qc.invalidateQueries({ queryKey: ['insights'] });
    },
    onError: (failure: Error) => { setMessage(null); setError(failure.message); },
  });

  function submit() {
    setMessage(null);
    setError(null);
    let parsed: unknown;
    try { parsed = JSON.parse(text); } catch { setError('请输入有效 JSON'); return; }
    const records = Array.isArray(parsed) ? parsed : parsed && typeof parsed === 'object' && Array.isArray((parsed as { records?: unknown }).records) ? (parsed as { records: unknown[] }).records : null;
    if (!records || records.length === 0 || records.length > 5000) { setError('JSON 应为 records 数组，数量范围为 1～5000'); return; }
    const normalized: SubmissionImportItem[] = [];
    for (const item of records) {
      if (!item || typeof item !== 'object') { setError('每条记录必须是对象'); return; }
      const row = item as Record<string, unknown>;
      if (typeof row.problemNo !== 'string' || !row.problemNo.trim() || typeof row.submittedAt !== 'string' || !row.submittedAt.trim()) { setError('每条记录都需要 problemNo 和 submittedAt'); return; }
      if (Number.isNaN(Date.parse(row.submittedAt))) { setError(`时间格式无法识别：${row.submittedAt}`); return; }
      normalized.push({ problemNo: row.problemNo.trim(), submittedAt: toShanghaiLocalDateTime(row.submittedAt), language: typeof row.language === 'string' ? row.language : undefined, url: typeof row.url === 'string' ? row.url : undefined });
    }
    save.mutate(normalized);
  }

  return <Card className="lg:col-span-2">
    <div className="mb-1 text-sm font-medium">手动导入提交记录</div>
    <p className="mb-3 text-xs leading-5 text-muted-foreground">适用于洛谷油猴脚本、牛客、力扣和 QOJ。也可以导入其他平台导出的 AC 记录；服务器会按用户、题目和时间去重。</p>
    <div className="mb-2 flex flex-wrap items-center gap-2"><Select value={platform} onChange={(event) => setPlatform(event.target.value)}><option value="LUOGU">洛谷</option><option value="NOWCODER">牛客</option><option value="LEETCODE">力扣</option><option value="QOJ">QOJ</option><option value="OTHER">其他平台</option></Select><span className="text-[11px] text-muted-foreground">每条至少包含 problemNo、submittedAt</span></div>
    <textarea className="min-h-32 w-full rounded-[var(--radius)] border bg-transparent px-3 py-2 font-mono text-xs leading-5 outline-none focus:border-[var(--primary)]" style={{ borderColor: 'var(--input)' }} value={text} onChange={(event) => setText(event.target.value)} placeholder={'[{"problemNo":"P1001","submittedAt":"2026-10-03T12:00:00+08:00","language":"C++","url":"https://www.luogu.com.cn/problem/P1001"}]'} disabled={save.isPending} />
    {error && <div className="mt-2"><Notice tone="danger">{error}</Notice></div>}
    {message && <div className="mt-2"><Notice>{message}</Notice></div>}
    <Button size="sm" variant="primary" className="mt-3" disabled={!text.trim() || save.isPending} onClick={submit}><Upload className="h-3.5 w-3.5" />{save.isPending ? '导入中…' : '导入记录'}</Button>
  </Card>;
}

function toShanghaiLocalDateTime(value: string) {
  const date = new Date(value);
  const parts = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).formatToParts(date);
  const values = Object.fromEntries(parts.filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}:${values.second}`;
}

/* ==================== 获奖记录（设置页内） ==================== */

function AwardSection({ awards }: {
  awards: {
    id: number; awardName: string; awardLevel: string | null;
    contestName: string | null; awardedAt: string | null;
    auditStatus: string; rejectReason: string | null; isPublic: boolean; certUrl?: string | null; certificateAvailable?: boolean;
  }[]
}) {
  const qc = useQueryClient();
  const [form, setForm] = useState({
    awardName: '', contestName: '', awardLevel: '', awardedAt: '', note: '', isPublic: true,
  });
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);

  const submit = useMutation({
    mutationFn: () =>
      submitAward({
        awardName: form.awardName.trim(),
        contestName: form.contestName.trim() || undefined,
        awardLevel: form.awardLevel.trim() || undefined,
        awardedAt: form.awardedAt || undefined,
        note: form.note.trim() || undefined,
        isPublic: form.isPublic,
      }),
    onSuccess: () => {
      setForm({ awardName: '', contestName: '', awardLevel: '', awardedAt: '', note: '', isPublic: true });
      void qc.invalidateQueries({ queryKey: ['my-awards'] });
    },
    onError: (e: Error) => setError(e.message),
  });

  const update = useMutation({
    mutationFn: () => updateAward(editingId!, {
      awardName: form.awardName.trim(), contestName: form.contestName.trim() || undefined,
      awardLevel: form.awardLevel.trim() || undefined, awardedAt: form.awardedAt || undefined,
      note: form.note.trim() || undefined, isPublic: form.isPublic,
    }),
    onSuccess: () => { setEditingId(null); setForm({ awardName: '', contestName: '', awardLevel: '', awardedAt: '', note: '', isPublic: true }); void qc.invalidateQueries({ queryKey: ['my-awards'] }); },
    onError: (e: Error) => setError(e.message),
  });
  const remove = useMutation({ mutationFn: deleteAward, onSuccess: () => void qc.invalidateQueries({ queryKey: ['my-awards'] }), onError: (e: Error) => setError(e.message) });
  const upload = useMutation({ mutationFn: ({ id, file }: { id: number; file: File }) => uploadAwardCertificate(id, file), onSuccess: () => void qc.invalidateQueries({ queryKey: ['my-awards'] }), onError: (e: Error) => setError(e.message) });

  const statusBadge = (status: string) =>
    status === 'APPROVED' ? (
      <Badge tone="success">已通过</Badge>
    ) : status === 'REJECTED' ? (
      <Badge tone="destructive">已驳回</Badge>
    ) : (
      <Badge tone="warning">待审核</Badge>
    );

  return (
    <div className="space-y-4">
      <div className="rounded-[var(--radius)] border p-3" style={{ borderColor: 'var(--border)' }}>
        <div className="mb-2 text-xs font-medium">提交获奖记录（站主审核通过后公示到个人页）</div>
        <div className="grid gap-2 sm:grid-cols-2">
          <input
            className="rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            placeholder="奖项名称（如 蓝桥杯省一等奖）"
            value={form.awardName}
            onChange={(e) => setForm((v) => ({ ...v, awardName: e.target.value }))}
            maxLength={128}
          />
          <input
            className="rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            placeholder="所属比赛（选填）"
            value={form.contestName}
            onChange={(e) => setForm((v) => ({ ...v, contestName: e.target.value }))}
            maxLength={200}
          />
          <input
            className="rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            placeholder="奖项等级（选填，如 省一）"
            value={form.awardLevel}
            onChange={(e) => setForm((v) => ({ ...v, awardLevel: e.target.value }))}
            maxLength={32}
          />
          <input
            className="rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            type="date"
            value={form.awardedAt}
            onChange={(e) => setForm((v) => ({ ...v, awardedAt: e.target.value }))}
          />
        </div>
        <input
          className="mt-2 w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
          style={{ borderColor: 'var(--input)' }}
          placeholder="备注（选填）"
          value={form.note}
          onChange={(e) => setForm((v) => ({ ...v, note: e.target.value }))}
          maxLength={500}
        />
        <label className="mt-2 flex items-center gap-1.5 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={form.isPublic}
            onChange={(e) => setForm((v) => ({ ...v, isPublic: e.target.checked }))}
          />
          审核通过后公开到我的个人页
        </label>
        {error && <div className="mt-2"><Notice tone="danger">{error}</Notice></div>}
        <Button
          size="sm"
          variant="primary"
          className="mt-2"
          disabled={!form.awardName.trim() || submit.isPending || update.isPending}
          onClick={() => {
            setError(null);
            if (editingId) update.mutate(); else submit.mutate();
          }}
        >
          {editingId ? (update.isPending ? '保存中…' : '保存修改') : (submit.isPending ? '提交中…' : '提交')}
        </Button>
        {editingId && <Button size="sm" className="mt-2 ml-2" onClick={() => { setEditingId(null); setForm({ awardName: '', contestName: '', awardLevel: '', awardedAt: '', note: '', isPublic: true }); }}>取消编辑</Button>}
      </div>

      {awards.length === 0 && (
        <div className="py-4 text-center text-xs text-muted-foreground">还没有提交过获奖记录</div>
      )}
      <div className="space-y-1.5">
        {awards.map((a) => (
          <div
            key={a.id}
            className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--radius)] px-2 py-1.5 text-xs"
            style={{ backgroundColor: 'color-mix(in oklab, var(--muted) 35%, transparent)' }}
          >
            <span className="min-w-0 flex-1 truncate">
              🏅 {a.awardName}
              {a.awardLevel && <span className="ml-1.5 badge text-[10px]">{a.awardLevel}</span>}
              {a.contestName && (
                <span className="ml-1.5 text-[11px] text-muted-foreground">@ {a.contestName}</span>
              )}
            </span>
            <span className="flex-none flex items-center gap-2">
              {statusBadge(a.auditStatus)}
              {a.certUrl && <a className="text-[11px] text-primary hover:underline" href={a.certUrl} target="_blank" rel="noreferrer">外链</a>}
              {a.certificateAvailable && <a className="text-[11px] text-primary hover:underline" href={`/api/awards/${a.id}/certificate`} target="_blank" rel="noreferrer">证书</a>}
              <label className="cursor-pointer text-[11px] text-primary hover:underline">上传证书<input type="file" className="hidden" accept="image/png,image/jpeg,image/webp,application/pdf" onChange={(e) => { const file = e.target.files?.[0]; if (file) upload.mutate({ id: a.id, file }); e.currentTarget.value = ''; }} /></label>
              <Button size="sm" variant="ghost" onClick={() => { setEditingId(a.id); setForm({ awardName: a.awardName, contestName: a.contestName ?? '', awardLevel: a.awardLevel ?? '', awardedAt: a.awardedAt ?? '', note: '', isPublic: a.isPublic }); }}>编辑</Button>
              <Button size="sm" variant="ghost" disabled={remove.isPending} onClick={() => { if (confirm('确定删除这条获奖记录？')) remove.mutate(a.id); }}>删除</Button>
              {a.auditStatus === 'REJECTED' && a.rejectReason && (
                <span className="text-[11px] text-muted-foreground">原因：{a.rejectReason}</span>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ==================== 改密页（独立视图） ==================== */

export function ProfilePasswordPage() {
  const navigate = useNavigate();
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () => changePassword(oldPassword, newPassword),
    onSuccess: () => {
      clearSession();
      navigate('/login?passwordChanged=1', { replace: true });
      setError(null);
      setSaved(true);
      setOld('');
      setNew('');
      setConfirm('');
    },
    onError: (e: Error) => {
      setSaved(false);
      setError(e.message);
    },
  });

  return (
    <>
      <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
        <Link to="/profile" className="hover:text-foreground">个人资料</Link>
        <span>/</span>
        <span>修改密码</span>
      </div>
      <PageHeader title="修改密码" description="修改后需要重新登录" />
      <div className="max-w-md">
        <Card>
          <div className="mb-3 flex items-center gap-2 text-sm font-medium">
            <Shield className="h-4 w-4" />
            账号安全
          </div>
          <div className="space-y-2">
            <div>
              <div className="mb-1 text-xs text-muted-foreground">原密码</div>
              <input
                type="password"
                className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
                style={{ borderColor: 'var(--input)' }}
                value={oldPassword}
                onChange={(e) => setOld(e.target.value)}
              />
            </div>
            <div>
              <div className="mb-1 text-xs text-muted-foreground">新密码（8 位以上）</div>
              <input
                type="password"
                className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
                style={{ borderColor: 'var(--input)' }}
                value={newPassword}
                onChange={(e) => setNew(e.target.value)}
              />
            </div>
            <div>
              <div className="mb-1 text-xs text-muted-foreground">确认新密码</div>
              <input
                type="password"
                className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
                style={{ borderColor: 'var(--input)' }}
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
              />
            </div>
          </div>
          {error && <div className="mt-2"><Notice tone="danger">{error}</Notice></div>}
          {saved && <div className="mt-2"><Notice>密码已修改</Notice></div>}
          <div className="mt-3 flex gap-2">
            <Button
              size="sm"
              variant="primary"
              disabled={!oldPassword || newPassword.length < 8 || newPassword !== confirm || save.isPending}
              onClick={() => {
                setError(null);
                save.mutate();
              }}
            >
              {save.isPending ? '提交中…' : '修改密码'}
            </Button>
            <Button size="sm" onClick={() => navigate('/profile/settings')}>返回设置</Button>
          </div>
        </Card>
      </div>
    </>
  );
}

function PasswordCard() {
  const navigate = useNavigate();
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () => changePassword(oldPassword, newPassword),
    onSuccess: () => {
      clearSession();
      navigate('/login?passwordChanged=1', { replace: true });
      setError(null);
      setSaved(true);
      setOld('');
      setNew('');
      setConfirm('');
    },
    onError: (e: Error) => {
      setSaved(false);
      setError(e.message);
    },
  });

  return (
    <Card>
      <div className="mb-3 flex items-center gap-2 text-sm font-medium">
        <Shield className="h-4 w-4" />
        账号安全
      </div>
      <div className="space-y-2">
        <div>
          <div className="mb-1 text-xs text-muted-foreground">原密码</div>
          <input
            type="password"
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={oldPassword}
            onChange={(e) => setOld(e.target.value)}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">新密码（8 位以上）</div>
          <input
            type="password"
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={newPassword}
            onChange={(e) => setNew(e.target.value)}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">确认新密码</div>
          <input
            type="password"
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </div>
      </div>
      {error && <div className="mt-2"><Notice tone="danger">{error}</Notice></div>}
      {saved && <div className="mt-2"><Notice>密码已修改</Notice></div>}
      <Button
        size="sm"
        variant="primary"
        className="mt-3"
        disabled={!oldPassword || newPassword.length < 8 || newPassword !== confirm || save.isPending}
        onClick={() => {
          setError(null);
          save.mutate();
        }}
      >
        {save.isPending ? '提交中…' : '修改密码'}
      </Button>
    </Card>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}
