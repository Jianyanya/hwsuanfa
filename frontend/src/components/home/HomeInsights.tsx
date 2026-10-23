import { useState, type ReactNode } from 'react';
import { BarChart3, PieChart as PieIcon, Target } from 'lucide-react';
import {
  Bar, BarChart, CartesianGrid, Cell, LabelList, Pie, PieChart,
  PolarAngleAxis, PolarGrid, PolarRadiusAxis, Radar, RadarChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';

import type { InsightCount, SubmissionInsights } from '@/api/submissions';
import { Card } from '@/components/ui/Card';
import { Notice } from '@/components/ui/Button';

const ABILITY_AXES = ['贪心', '模拟', '枚举', 'DP', '搜索', '图论', '数据结构', '数学'];
const DIFFICULTIES = ['简单', '中等', '困难'];
const PLATFORM_NAMES: Record<string, string> = {
  LUOGU: '洛谷', CODEFORCES: 'Codeforces', NOWCODER: 'NowCoder',
  ATCODER: 'AtCoder', LEETCODE: 'LeetCode', OTHER: '其他',
};
const PLATFORM_COLORS: Record<string, string> = {
  LUOGU: '#60a5fa', CODEFORCES: '#a78bfa', NOWCODER: '#4ade80',
  ATCODER: '#fbbf24', LEETCODE: '#fb7185', OTHER: '#94a3b8',
};
const TAG_COLORS = ['#60a5fa', '#4ade80', '#fbbf24', '#a78bfa', '#fb7185', '#22d3ee'];
const TOOLTIP_STYLE = {
  backgroundColor: 'var(--popover)', border: '1px solid var(--border)',
  borderRadius: 8, color: 'var(--foreground)', fontSize: 12,
};

function countScale(max: number) {
  const roughStep = Math.max(1, max / 5);
  const magnitude = 10 ** Math.floor(Math.log10(roughStep));
  const step = [1, 2, 5, 10].find((n) => n * magnitude >= roughStep)! * magnitude;
  const divisions = Math.max(4, Math.ceil(max / step));
  return { max: step * divisions, ticks: Array.from({ length: divisions + 1 }, (_, index) => index * step) };
}

function ChartCard({ title, subtitle, icon, children, className = '' }: {
  title: string;
  subtitle: string;
  icon: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`min-w-0 rounded-[var(--radius)] border p-4 ${className}`} aria-label={title}>
      <div className="mb-1 flex items-center gap-2 text-sm font-semibold">
        <span className="text-muted-foreground">{icon}</span>
        {title}
      </div>
      <p className="text-[11px] text-muted-foreground">{subtitle}</p>
      {children}
    </section>
  );
}

function AbilityRadar({ data, tagged, total }: { data: InsightCount[]; tagged: number; total: number }) {
  const abilities = ABILITY_AXES.map((name) => ({
    name, count: data.find((item) => item.name === name)?.count ?? 0,
  }));
  const scale = countScale(Math.max(0, ...abilities.map((item) => item.count)));

  return (
    <ChartCard title="能力雷达" subtitle="按算法标签统计通过题数，一题可对应多项能力" icon={<Target className="h-4 w-4" />}>
      <div className="mt-2 h-[280px]" role="img" aria-label={abilities.map((a) => `${a.name} ${a.count} 题`).join('，')}>
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart data={abilities} outerRadius="66%" margin={{ top: 16, right: 30, bottom: 16, left: 30 }}>
            <PolarGrid stroke="var(--border)" />
            <PolarAngleAxis dataKey="name" tick={{ fill: 'var(--foreground)', fontSize: 11 }} />
            <PolarRadiusAxis angle={67.5} domain={[0, scale.max]} tickCount={scale.ticks.length} allowDecimals={false}
              axisLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 10 }} />
            <Radar name="通过题数" dataKey="count" stroke="#60a5fa" fill="#60a5fa" fillOpacity={0.22}
              strokeWidth={2} dot={{ r: 3, fill: '#60a5fa' }} isAnimationActive={false} />
            <Tooltip contentStyle={TOOLTIP_STYLE} itemStyle={{ color: 'var(--foreground)' }}
              labelFormatter={(_, payload) => payload[0]?.payload.name ?? ''}
              formatter={(value) => [`${value} 题`, '通过题数']} />
          </RadarChart>
        </ResponsiveContainer>
      </div>
      <div className="flex items-center justify-between border-t pt-3 text-[11px] text-muted-foreground">
        <span>标尺单位：题</span>
        <span>{tagged} / {total} 题已标注能力</span>
      </div>
      {tagged === 0 && <p className="mt-2 text-[11px] text-muted-foreground">暂无算法标签，回填题目标签后自动生成。</p>}
    </ChartCard>
  );
}

function PlatformDonut({ data }: { data: InsightCount[] }) {
  const [activeName, setActiveName] = useState<string | null>(null);
  const total = data.reduce((sum, item) => sum + item.count, 0);
  const platforms = data.filter((item) => item.count > 0).map((item) => ({
    ...item,
    label: PLATFORM_NAMES[item.name.toUpperCase()] ?? item.name,
    color: PLATFORM_COLORS[item.name.toUpperCase()] ?? PLATFORM_COLORS.OTHER,
    percent: total ? (item.count / total * 100).toFixed(1) : '0.0',
  }));
  const active = platforms.find((item) => item.name === activeName);

  return (
    <ChartCard title="平台过题" subtitle="悬停图块或平台名称，查看题数与占比" icon={<PieIcon className="h-4 w-4" />}>
      <div className="relative mt-2 h-[235px]" onMouseLeave={() => setActiveName(null)}>
        {total > 0 ? (
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={platforms} dataKey="count" nameKey="label" cx="50%" cy="50%"
                innerRadius={62} outerRadius={88} startAngle={90} endAngle={-270}
                paddingAngle={platforms.length > 1 ? 3 : 0} stroke="var(--card)" strokeWidth={2}
                isAnimationActive={false}
                onMouseEnter={(_, index) => setActiveName(platforms[index].name)}
                onMouseLeave={() => setActiveName(null)}>
                {platforms.map((item) => (
                  <Cell key={item.name} fill={item.color}
                    fillOpacity={activeName && activeName !== item.name ? 0.4 : 1} />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">暂无平台过题数据</div>
        )}
        {total > 0 && (
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-3xl font-semibold tabular-nums">{active?.count ?? total}</span>
            <span className="mt-1 text-[11px] text-muted-foreground">{active?.label ?? '累计通过'}</span>
            {active && <span className="mt-1 text-[11px] text-muted-foreground">占比 {active.percent}%</span>}
          </div>
        )}
        {active && (
          <div role="tooltip" className="pointer-events-none absolute right-0 top-1 rounded-lg border px-3 py-2 text-xs"
            style={{ backgroundColor: 'var(--popover)' }}>
            <div className="font-medium">{active.label}</div>
            <div className="mt-1 text-muted-foreground">{active.count} 题 · 占比 {active.percent}%</div>
          </div>
        )}
      </div>
      <div className="grid gap-1 border-t pt-2">
        {platforms.map((item) => (
          <button key={item.name} type="button"
            className="flex items-center gap-2 rounded-md px-2 py-1 text-xs hover:bg-muted focus:bg-muted"
            onMouseEnter={() => setActiveName(item.name)} onMouseLeave={() => setActiveName(null)}
            onFocus={() => setActiveName(item.name)} onBlur={() => setActiveName(null)}
            onClick={() => setActiveName(item.name)}
            aria-label={`${item.label}，${item.count} 题，占比 ${item.percent}%`}>
            <span className="h-2 w-2 flex-none rounded-full" style={{ backgroundColor: item.color }} />
            <span className="flex-1 text-left">{item.label}</span>
            <span className="tabular-nums">{item.count} 题</span>
            <span className="w-12 text-right tabular-nums text-muted-foreground">{item.percent}%</span>
          </button>
        ))}
      </div>
    </ChartCard>
  );
}

function DifficultyBars({ data, unknown }: { data: InsightCount[]; unknown: number }) {
  const difficulties = DIFFICULTIES.map((name) => ({
    name, count: data.find((item) => item.name === name)?.count ?? 0,
  }));
  const scale = countScale(Math.max(0, ...difficulties.map((item) => item.count)));
  const known = difficulties.reduce((sum, item) => sum + item.count, 0);

  return (
    <ChartCard title="难度分布" subtitle="各平台难度统一归并为简单、中等、困难"
      icon={<BarChart3 className="h-4 w-4" />} className="md:col-span-2 xl:col-span-1">
      <div className="mt-2 h-[280px]" role="img"
        aria-label={`难度分布，横轴为难度，纵轴为题数，${difficulties.map((d) => `${d.name} ${d.count} 题`).join('，')}`}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={difficulties} margin={{ top: 30, right: 12, bottom: 22, left: 8 }} barSize={44}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="name" axisLine={{ stroke: 'var(--border)' }} tickLine={false}
              tick={{ fill: 'var(--foreground)', fontSize: 12 }} dy={8}
              label={{ value: '难度', position: 'insideBottom', offset: -18, fill: 'var(--muted-foreground)', fontSize: 11 }} />
            <YAxis allowDecimals={false} domain={[0, scale.max]} ticks={scale.ticks} axisLine={false}
              tickLine={false} width={40} tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}
              label={{ value: '题数', angle: -90, position: 'insideLeft', fill: 'var(--muted-foreground)', fontSize: 11 }} />
            <Tooltip contentStyle={TOOLTIP_STYLE} itemStyle={{ color: 'var(--foreground)' }}
              formatter={(value) => [`${value} 题`, '通过题数']} cursor={{ fill: 'var(--muted)', fillOpacity: 0.5 }} />
            <Bar dataKey="count" name="通过题数" radius={[5, 5, 0, 0]} isAnimationActive={false}>
              {['#4ade80', '#60a5fa', '#fb7185'].map((color) => <Cell key={color} fill={color} />)}
              <LabelList dataKey="count" position="top" fill="var(--foreground)" fontSize={12} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-1 border-t pt-3 text-[11px] text-muted-foreground">
        <span>已分类 {known} 题</span>
        <span>{unknown > 0 ? `另有 ${unknown} 题暂无难度` : '按通过题目去重统计'}</span>
      </div>
    </ChartCard>
  );
}

export function HomeInsights({ insights, loading, error }: {
  insights: SubmissionInsights | undefined;
  loading: boolean;
  error: string | null;
}) {
  if (loading) return <Card className="mt-6"><div className="py-6 text-center text-sm text-muted-foreground">刷题画像加载中…</div></Card>;
  if (error) return <Card className="mt-6"><Notice tone="danger">刷题画像加载失败：{error}</Notice></Card>;
  const data = insights ?? {
    totalAc: 0, byPlatform: [], byDifficulty: [], unknownDifficulty: 0,
    byAbility: [], abilityTaggedAc: 0, byTag: [],
  };
  const topTags = data.byTag.slice(0, 10);
  const maxTag = Math.max(1, ...topTags.map((item) => item.count));

  return (
    <Card className="mt-6">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-base font-semibold">我的刷题画像</div>
          <p className="mt-1 text-xs text-muted-foreground">从算法能力、平台和难度查看训练积累</p>
        </div>
        <span className="rounded-full border px-3 py-1 text-xs text-muted-foreground">
          累计通过 <strong className="ml-1 text-foreground">{data.totalAc}</strong> 题
        </span>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <AbilityRadar data={data.byAbility ?? []} tagged={data.abilityTaggedAc ?? 0} total={data.totalAc} />
        <PlatformDonut data={data.byPlatform} />
        <DifficultyBars data={data.byDifficulty} unknown={data.unknownDifficulty ?? 0} />
      </div>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <section className="min-w-0 rounded-[var(--radius)] border p-4" aria-label="标签词云">
          <div className="mb-3 text-sm font-semibold">标签词云</div>
          {topTags.length === 0 ? (
            <p className="py-6 text-center text-xs text-muted-foreground">题目标签回填后，这里会显示你的训练方向。</p>
          ) : (
            <div className="flex min-h-32 flex-wrap items-center justify-center gap-x-4 gap-y-2">
              {topTags.map((item, index) => (
                <span key={item.name} title={`${item.name}：${item.count} 题`}
                  style={{ fontSize: 12 + Math.round(item.count / maxTag * 14), color: TAG_COLORS[index % TAG_COLORS.length],
                    fontWeight: item.count / maxTag > 0.6 ? 700 : 400 }}>
                  {item.name}
                </span>
              ))}
            </div>
          )}
        </section>
        <section className="min-w-0 rounded-[var(--radius)] border p-4" aria-label="标签 AC Top">
          <div className="mb-3 flex items-center justify-between text-sm font-semibold">
            <span>标签 AC Top</span><span className="text-xs font-normal text-muted-foreground">{topTags.length} 项</span>
          </div>
          {topTags.length === 0 && <p className="py-6 text-center text-xs text-muted-foreground">暂无标签数据</p>}
          <div className="space-y-2">
            {topTags.map((item, index) => (
              <div key={item.name} className="flex items-center gap-2 text-xs">
                <span className="w-24 flex-none truncate" title={item.name}>{item.name}</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full" style={{ width: `${item.count / maxTag * 100}%`,
                    backgroundColor: TAG_COLORS[index % TAG_COLORS.length] }} />
                </div>
                <span className="w-12 flex-none text-right tabular-nums text-muted-foreground">{item.count} 题</span>
              </div>
            ))}
          </div>
        </section>
      </div>
    </Card>
  );
}
