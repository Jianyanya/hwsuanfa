import { useQuery } from '@tanstack/react-query';

import { fetchAdminOverview } from '@/api/admin';
import { Card } from '@/components/ui/Card';
import { Button, Notice } from '@/components/ui/Button';

const formatNumber = (value: number) => Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });

export function OverviewPanel() {
  const query = useQuery({ queryKey: ['admin-overview'], queryFn: fetchAdminOverview, staleTime: 0 });
  const overview = query.data;
  const maxChange = Math.max(1, ...(overview?.recentScores.map((item) => Math.abs(Number(item.score))) ?? []));

  return <section className="space-y-4" aria-label="全站概况">
    <Card><div className="flex items-center justify-between gap-3"><h2 className="text-sm font-semibold">全站概况</h2><Button size="sm" disabled={query.isFetching} onClick={() => void query.refetch()}>{query.isFetching ? '刷新中…' : '刷新概况'}</Button></div></Card>
    {query.isLoading && <Card><div className="py-8 text-center text-sm text-muted-foreground">正在加载全站数据…</div></Card>}
    {query.isError && <Notice tone="danger">概况加载失败：{query.error.message}</Notice>}
    {overview && <>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          ['队伍数', overview.teamCount], ['近 7 日登录', overview.activeUsers],
          ['作业数', overview.assignmentCount], ['每日一题', overview.dailyCount],
          ['待审获奖', overview.pendingAwards], ['待审题单', overview.pendingProblemLists],
          ['全站总积分', overview.totalScore],
        ].map(([label, value]) => <Card key={label}><div className="text-xs text-muted-foreground">{label}</div><div className="mt-2 text-2xl font-semibold tabular-nums">{formatNumber(Number(value))}</div></Card>)}
      </div>
      <Card><h2 className="mb-4 text-sm font-semibold">训练完成率</h2><div className="grid gap-5 sm:grid-cols-2">
        <CompletionRate label="作业完成率" value={overview.assignmentCompletionRate} />
        <CompletionRate label="每日一题完成率" value={overview.dailyCompletionRate} />
      </div><p className="mt-4 text-xs leading-6 text-muted-foreground">完成率按当前目标用户统计；没有目标用户时显示 0%。</p></Card>
      <Card><h2 className="mb-2 text-sm font-semibold">近 7 日积分净变化</h2><p className="mb-4 text-xs text-muted-foreground">按积分事件汇总，负数表示当天净扣分。</p>
        {overview.recentScores.length === 0 ? <div className="py-5 text-center text-sm text-muted-foreground">暂无积分变化记录</div> : <div className="space-y-3" role="img" aria-label={overview.recentScores.map((item) => `${item.date} ${formatNumber(item.score)} 分`).join('，')}>
          {overview.recentScores.map((item) => <div key={item.date} className="grid grid-cols-[88px_minmax(0,1fr)_64px] items-center gap-3 text-xs"><time className="tabular-nums text-muted-foreground">{item.date.slice(5)}</time>
            <div className="relative flex h-4 overflow-hidden rounded-sm bg-muted"><span className="absolute inset-y-0 left-1/2 border-l border-[var(--border)]" /><span className="absolute inset-y-1 rounded-sm" style={{ width: `${Math.abs(Number(item.score)) / maxChange * 50}%`, left: item.score >= 0 ? '50%' : `${50 - Math.abs(Number(item.score)) / maxChange * 50}%`, backgroundColor: item.score < 0 ? 'var(--destructive)' : 'var(--primary)' }} /></div>
            <span className="text-right tabular-nums" style={{ color: item.score < 0 ? 'var(--destructive)' : 'var(--foreground)' }}>{item.score > 0 ? '+' : ''}{formatNumber(item.score)}</span>
          </div>)}
        </div>}
      </Card>
    </>}
  </section>;
}

function CompletionRate({ label, value }: { label: string; value: number }) {
  const percent = Math.max(0, Math.min(100, Number.isFinite(value) ? value : 0));
  return <div><div className="mb-2 flex items-center justify-between gap-3 text-xs"><span>{label}</span><span className="font-medium tabular-nums">{percent.toFixed(1)}%</span></div><div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}><div className="h-full rounded-full bg-primary" style={{ width: `${percent}%` }} /></div></div>;
}
