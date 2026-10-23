import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Crown, Medal, Users } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Pill } from '@/components/ui/Badge';
import { Notice } from '@/components/ui/Button';
import { fetchRank } from '@/api/rank';
import { useAuth } from '@/stores/auth';
import type { RankTeamRow, RankUserRow } from '@/types/assignment';

/**
 * 积分排行榜。
 *
 * 数据由后端从积分事件实时聚合（不走汇总快照，永远准确）；
 * 完全私密的成员由后端过滤不上榜；同分同名次（1224 排名法）。
 */
export default function RankPage() {
  const [tab, setTab] = useState<'users' | 'teams'>('users');
  const me = useAuth((s) => s.user);
  const query = useQuery({ queryKey: ['rank'], queryFn: fetchRank });

  if (query.isLoading) return <Muted>加载中…</Muted>;
  if (query.isError) return <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>;

  const data = query.data!;

  return (
    <>
      <PageHeader
        title="积分排行榜"
        description="从积分事件实时聚合，做题、比赛、每日一题都算分"
        actions={
          <div className="flex gap-2">
            <Pill active={tab === 'users'} onClick={() => setTab('users')}>
              个人排行
            </Pill>
            <Pill active={tab === 'teams'} onClick={() => setTab('teams')}>
              队伍排行
            </Pill>
          </div>
        }
      />

      {tab === 'users' ? (
        <UserBoard rows={data.users} meId={me?.id} />
      ) : (
        <TeamBoard rows={data.teams} />
      )}
    </>
  );
}

function UserBoard({ rows, meId }: { rows: RankUserRow[]; meId?: number }) {
  return (
    <Card flush>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr
              className="text-left text-[11px] text-muted-foreground"
              style={{ borderBottom: '1px solid var(--border)' }}
            >
              <th className="px-3 py-2 font-normal">名次</th>
              <th className="px-3 py-2 font-normal">成员</th>
              <th className="px-3 py-2 font-normal">班级</th>
              <th className="px-3 py-2 text-right font-normal">总积分</th>
              <th className="px-3 py-2 text-right font-normal">完成题数</th>
              <th className="px-3 py-2 text-right font-normal">首次完成</th>
              <th className="px-3 py-2 text-right font-normal">每日一题</th>
              <th className="px-3 py-2 text-right font-normal">作业</th>
              <th className="px-3 py-2 text-right font-normal">比赛</th>
              <th className="px-3 py-2 font-normal">队伍</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((u) => (
              <tr
                key={u.userId}
                style={{
                  borderBottom: '1px solid var(--border)',
                  backgroundColor:
                    u.userId === meId
                      ? 'color-mix(in oklab, var(--primary) 10%, transparent)'
                      : 'transparent',
                }}
              >
                <td className="px-3 py-2">
                  <RankBadge rank={u.rank} />
                </td>
                <td className="px-3 py-2 font-medium">
                  {u.nickname}
                  {u.userId === meId && (
                    <span className="ml-1.5 text-[10px] text-muted-foreground">（我）</span>
                  )}
                </td>
                <td className="px-3 py-2 text-xs text-muted-foreground">{u.className ?? '—'}</td>
                <td className="px-3 py-2 text-right font-semibold tabular-nums">
                  {Number(u.totalScore).toFixed(1)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">{u.solvedCount}</td>
                <td className="px-3 py-2 text-right tabular-nums">{u.firstAc}</td>
                <td className="px-3 py-2 text-right tabular-nums">{u.dailyCount}</td>
                <td className="px-3 py-2 text-right tabular-nums">{u.assignmentCount}</td>
                <td className="px-3 py-2 text-right tabular-nums">{u.contestCount}</td>
                <td className="px-3 py-2 text-xs text-muted-foreground">{u.teamName ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length === 0 && (
        <div className="py-8 text-center text-sm text-muted-foreground">还没有上榜的成员</div>
      )}
      <div className="px-3 py-2 text-[11px] text-muted-foreground">
        完全私密的成员不会出现在榜上；同分并列名次。
      </div>
    </Card>
  );
}

function TeamBoard({ rows }: { rows: RankTeamRow[] }) {
  return (
    <Card flush>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr
              className="text-left text-[11px] text-muted-foreground"
              style={{ borderBottom: '1px solid var(--border)' }}
            >
              <th className="px-3 py-2 font-normal">名次</th>
              <th className="px-3 py-2 font-normal">队伍</th>
              <th className="px-3 py-2 font-normal">队长</th>
              <th className="px-3 py-2 text-right font-normal">人数</th>
              <th className="px-3 py-2 text-right font-normal">总积分</th>
              <th className="px-3 py-2 text-right font-normal">人均积分</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => (
              <tr key={t.teamId} style={{ borderBottom: '1px solid var(--border)' }}>
                <td className="px-3 py-2">
                  <RankBadge rank={t.rank} />
                </td>
                <td className="px-3 py-2 font-medium">{t.teamName}</td>
                <td className="px-3 py-2 text-xs text-muted-foreground">
                  {t.leaderNickname ?? '—'}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">{t.memberCount}</td>
                <td className="px-3 py-2 text-right font-semibold tabular-nums">
                  {Number(t.totalScore).toFixed(1)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {Number(t.avgScore).toFixed(1)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length === 0 && (
        <div className="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
          <Users className="h-4 w-4" />
          还没有队伍
        </div>
      )}
      <div className="px-3 py-2 text-[11px] text-muted-foreground">
        队伍积分 = 全体队员积分之和（完全私密的成员不计入）。
      </div>
    </Card>
  );
}

function RankBadge({ rank }: { rank: number }) {
  if (rank === 1) {
    return (
      <span className="inline-flex items-center gap-1 font-semibold" style={{ color: '#f5a524' }}>
        <Crown className="h-4 w-4" />
        1
      </span>
    );
  }
  if (rank <= 3) {
    return (
      <span
        className="inline-flex items-center gap-1 font-medium"
        style={{ color: rank === 2 ? '#9ca3af' : '#b87333' }}
      >
        <Medal className="h-4 w-4" />
        {rank}
      </span>
    );
  }
  return <span className="tabular-nums text-muted-foreground">#{rank}</span>;
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}
