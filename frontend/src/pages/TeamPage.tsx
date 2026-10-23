import { useEffect, useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Crown, Info, Lock, Snowflake, UserPlus, Users } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { fetchMyTeams, fetchTeamDetail } from '@/api/team';
import { fetchRank } from '@/api/rank';
import { fetchMyAssignments } from '@/api/assignment';
import { fetchContests } from '@/api/contest';
import { fetchScoreTrend, fetchTeamCompletion } from '@/api/stats';
import { useAuth } from '@/stores/auth';
import { cn } from '@/lib/utils';

/**
 * 我的队伍。
 *
 * 权限：本队成员可读；队伍结构类操作（增删成员、变更队长）归站主，
 * 所以这里不提供编辑入口，只在站主身份时给一个去后台的链接。
 */
export default function TeamPage() {
  const { teamId: routeTeamId } = useParams();
  const user = useAuth((s) => s.user);
  const currentTeamId = useAuth((s) => s.activeTeamId);
  const setCurrentTeam = useAuth((s) => s.setCurrentTeam);
  const navigate = useNavigate();

  const teamsQuery = useQuery({
    queryKey: ['my-teams'],
    queryFn: fetchMyTeams,
    enabled: !!user,
  });

  const teams = useMemo(() => teamsQuery.data ?? [], [teamsQuery.data]);

  // 支持 /team/:teamId 深链；否则默认第一支队伍
  const routeId = routeTeamId ? Number(routeTeamId) : null;
  const activeId = routeId && teams.some((team) => team.id === routeId) ? routeId
    : teams.some((team) => team.id === currentTeamId) ? currentTeamId : teams[0]?.id ?? null;

  useEffect(() => {
    if (activeId !== null) setCurrentTeam(activeId);
  }, [activeId, setCurrentTeam]);

  const detailQuery = useQuery({
    queryKey: ['team-detail', activeId],
    queryFn: () => fetchTeamDetail(activeId!),
    enabled: activeId !== null,
  });

  const completionQuery = useQuery({
    queryKey: ['team-completion', activeId],
    queryFn: () => fetchTeamCompletion(activeId!),
    enabled: activeId !== null,
  });
  const trendQuery = useQuery({
    queryKey: ['score-trend', activeId],
    queryFn: () => fetchScoreTrend(activeId!, 30),
    enabled: activeId !== null,
  });

  if (teamsQuery.isLoading) {
    return <Hint>加载中…</Hint>;
  }

  if (teamsQuery.isError) {
    return <Hint tone="destructive">加载失败：{(teamsQuery.error as Error).message}</Hint>;
  }

  if (teams.length === 0) {
    return (
      <>
        <PageHeader title="我的队伍" description="查看队伍成员与训练进度" />
        <Card>
          <div className="flex flex-col items-center gap-2 py-10 text-center">
            <Users className="h-8 w-8 text-muted-foreground" />
            <div className="text-sm font-medium">你还未加入任何队伍</div>
            <div className="max-w-md text-xs text-muted-foreground">
              队伍由站主创建并分配成员。加入后这里会显示队友、队伍积分与训练进度。
            </div>
          </div>
        </Card>
      </>
    );
  }

  const detail = detailQuery.data;

  return (
    <>
      <PageHeader
        title="我的队伍"
        description="查看队伍成员与训练进度"
        actions={
          detail?.viewerIsSiteOwner ? (
            <Link to="/admin">
              <Button size="sm">去站主后台管理</Button>
            </Link>
          ) : undefined
        }
      />

      {/* 多队伍时提供切换 */}
      {teams.length > 1 && (
        <div className="mb-4 flex flex-wrap gap-2">
          {teams.map((t) => (
            <Pill key={t.id} active={t.id === activeId} onClick={() => { setCurrentTeam(t.id); navigate(`/team/${t.id}`); }}>
              {t.name}
              <span className="ml-1.5 text-[11px] opacity-70">
                {t.memberCount}/{t.maxMembers}
              </span>
            </Pill>
          ))}
        </div>
      )}

      {detailQuery.isLoading && <Hint>加载中…</Hint>}
      {detailQuery.isError && (
        <Hint tone="destructive">加载失败：{(detailQuery.error as Error).message}</Hint>
      )}

      {detail && (
        <div className="space-y-4">
          {detail.status === 'FROZEN' && (
            <div
              className="flex items-start gap-2 rounded-[var(--radius)] border px-4 py-3 text-sm"
              style={{
                borderColor: 'color-mix(in oklab, var(--warning) 45%, transparent)',
                backgroundColor: 'color-mix(in oklab, var(--warning) 12%, transparent)',
              }}
            >
              <Snowflake className="mt-0.5 h-4 w-4 flex-none" style={{ color: 'var(--warning)' }} />
              <div>
                <div className="font-medium" style={{ color: 'var(--warning)' }}>
                  本队已被冻结
                </div>
                <div className="mt-0.5 text-xs text-muted-foreground">
                  {detail.frozenReason || '暂不能增减成员，请联系站主处理。'}
                </div>
              </div>
            </div>
          )}

          {/* 队伍概况 */}
          <Card>
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-lg font-semibold">{detail.name}</h2>
                  <StatusBadge status={detail.status} />
                  {detail.viewerIsLeader && (
                    <Badge tone="primary">
                      <Crown className="mr-1 inline h-3 w-3" />
                      你是本队队长
                    </Badge>
                  )}
                </div>
                <p className="mt-1.5 max-w-2xl text-sm text-muted-foreground">
                  {detail.description || '暂无队伍简介'}
                </p>
              </div>

              <div className="flex gap-6">
                <Stat label="成员" value={`${detail.memberCount}/${detail.maxMembers}`} />
                <Stat label="队长" value={leaderName(detail)} />
                <Stat label="创建于" value={formatDate(detail.createdAt)} />
              </div>
            </div>
          </Card>

          {/* 成员列表 */}
          <Card>
            <div className="mb-4 flex items-center justify-between">
              <div className="text-sm font-medium">
                队伍成员
                <span className="ml-2 text-xs text-muted-foreground">
                  {detail.memberCount}/{detail.maxMembers} 人
                </span>
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {detail.members.map((m) => (
                <div
                  key={m.userId}
                  className="flex items-center gap-3 rounded-[var(--radius)] border p-3"
                  style={{ borderColor: 'var(--border)' }}
                >
                  <div
                    className="flex h-9 w-9 flex-none items-center justify-center rounded-full text-sm font-medium"
                    style={{
                      backgroundColor: 'var(--muted)',
                      color: 'var(--foreground)',
                    }}
                  >
                    {m.nickname.slice(0, 1)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="truncate text-sm font-medium">{m.nickname}</span>
                      {m.leader && (
                        <Badge tone="primary" className="flex-none">
                          队长
                        </Badge>
                      )}
                    </div>
                    <div className="truncate text-[11px] text-muted-foreground">
                      @{m.username}
                      {m.joinedAt && ` · ${formatDate(m.joinedAt)} 加入`}
                    </div>
                    {/* 真实姓名与学号只有站主拿得到，接口不返回就是 null */}
                    {(m.realName || m.studentNo) && (
                      <div className="mt-0.5 text-[11px] text-muted-foreground">
                        {m.realName}
                        {m.studentNo && ` · ${m.studentNo}`}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {/* 空位：让人一眼看出还能加几个人 */}
              {Array.from({ length: Math.max(0, detail.maxMembers - detail.memberCount) }).map(
                (_, i) => (
                  <div
                    key={`slot-${i}`}
                    className="flex items-center justify-center gap-2 rounded-[var(--radius)] border border-dashed p-3 text-xs text-muted-foreground"
                    style={{ borderColor: 'var(--border)', minHeight: 66 }}
                  >
                    <UserPlus className="h-3.5 w-3.5" />
                    空位
                  </div>
                ),
              )}
            </div>

            <div className="mt-4 flex items-start gap-2 border-t pt-3 text-[11px] text-muted-foreground" style={{ borderColor: 'var(--border)' }}>
              <Info className="mt-0.5 h-3 w-3 flex-none" />
              <span>
                队伍成员由站主分配，每队最多 {detail.maxMembers} 人、且有且仅有一名队长。
                队员的真实姓名与学号仅站主可见。
              </span>
            </div>
          </Card>

          <TeamStats completion={completionQuery.data} trend={trendQuery.data} />

          <TeamTrainingOverview teamId={detail.id} />

          <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
            <Lock className="h-3 w-3" />
            队友的完整提交记录与代码是否可见，取决于各人的隐私设置（判断在后端完成）。
          </div>
        </div>
      )}
    </>
  );
}

function TeamStats({ completion, trend }: { completion?: import('@/api/stats').TeamCompletion; trend?: import('@/api/stats').ScoreTrend }) {
  if (!completion && !trend) return null;
  return <div className="grid gap-4 lg:grid-cols-2">
    {completion && <Card><div className="mb-3 flex items-center justify-between"><h2 className="text-sm font-medium">成员完成度</h2><span className="text-xs text-muted-foreground">{completion.completedCount}/{completion.totalCount} 题</span></div><div className="space-y-3">{completion.members.map((member) => <div key={member.userId}><div className="mb-1 flex justify-between gap-2 text-xs"><span>{member.nickname}</span><span className="text-muted-foreground">{member.completedCount}/{member.totalCount} · {member.completionRate}%</span></div><div className="h-2 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-primary" style={{ width: `${Math.min(100, member.completionRate)}%` }} /></div></div>)}</div></Card>}
    {trend && <Card><div className="mb-3 flex items-center justify-between"><h2 className="text-sm font-medium">近 {trend.days} 天积分趋势</h2><span className="text-xs text-muted-foreground">按日汇总</span></div><div className="flex h-40 items-end gap-1">{trend.points.map((point) => { const max = Math.max(...trend.points.map((item) => Math.abs(item.score)), 1); const height = Math.max(4, Math.round(Math.abs(point.score) / max * 100)); return <div key={point.date} className="group relative flex h-full flex-1 items-end" title={`${point.date}: ${point.score}`}><div className={`w-full rounded-t ${point.score < 0 ? 'bg-[var(--destructive)]' : 'bg-primary'}`} style={{ height: `${height}%` }} /></div>; })}</div><div className="mt-2 flex justify-between text-[11px] text-muted-foreground"><span>{trend.points[0]?.date}</span><span>{trend.points.at(-1)?.date}</span></div></Card>}
  </div>;
}

function TeamTrainingOverview({ teamId }: { teamId: number }) {
  const rank = useQuery({ queryKey: ['rank'], queryFn: fetchRank });
  const assignments = useQuery({ queryKey: ['my-assignments-all'], queryFn: () => fetchMyAssignments('ALL') });
  const contests = useQuery({ queryKey: ['contests'], queryFn: fetchContests });
  const team = rank.data?.teams.find((item) => item.teamId === teamId);
  const relatedAssignments = assignments.data?.filter((item) => item.type === 'TEAM' && item.targets.some((target) => target.id === teamId)) ?? [];
  const relatedContests = contests.data?.filter((item) => item.teams.some((target) => target.id === teamId)) ?? [];
  return <div className="grid gap-4 lg:grid-cols-2">
    <Card><h2 className="mb-3 text-sm font-medium">队伍积分与排名</h2>
      {rank.isLoading ? <Hint>加载中…</Hint> : rank.isError ? <Hint tone="destructive">{rank.error.message}</Hint> : <div className="flex flex-wrap justify-between gap-4"><Stat label="队伍总积分" value={String(team?.totalScore ?? 0)} /><Stat label="全站排名" value={team ? `#${team.rank}` : '—'} /><Stat label="平均积分" value={Number(team?.avgScore ?? 0).toFixed(1)} /></div>}
      <Link to="/rank" className="mt-3 inline-block text-xs text-primary">查看积分排行</Link>
    </Card>
    <Card><h2 className="mb-3 text-sm font-medium">作业与比赛</h2>
      {assignments.isLoading || contests.isLoading ? <Hint>加载中…</Hint> : assignments.isError || contests.isError ? <Hint tone="destructive">训练数据加载失败，请稍后重试。</Hint> : <div className="space-y-3 text-xs">
        <div>本队作业 {relatedAssignments.length} 项 · 我已完成 {relatedAssignments.filter((item) => item.mine?.rawStatus === 'COMPLETED').length} 项</div>
        {relatedAssignments.slice(0, 3).map((item) => <Link key={item.id} className="block truncate hover:text-primary" to={`/assignment/${item.id}`}>{item.title}</Link>)}
        <div>本队已报名比赛 {relatedContests.length} 场</div>
        {relatedContests.slice(0, 3).map((item) => <Link key={item.id} className="block truncate hover:text-primary" to={`/contest/${item.id}`}>{item.name}</Link>)}
      </div>}
      <div className="mt-3 flex gap-3 text-xs text-primary"><Link to="/assignment">全部作业</Link><Link to="/contest">比赛与日志</Link><Link to="/problemset">训练题单</Link></div>
    </Card>
  </div>;
}

/* ---------------- 小组件 ---------------- */

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="text-right">
      <div className="text-[11px] text-muted-foreground">{label}</div>
      <div className="text-sm font-medium">{value}</div>
    </div>
  );
}

function StatusBadge({ status }: { status: 'ACTIVE' | 'FROZEN' }) {
  return status === 'FROZEN' ? (
    <Badge tone="warning">已冻结</Badge>
  ) : (
    <Badge tone="success">正常</Badge>
  );
}

function Hint({ children, tone }: { children: React.ReactNode; tone?: 'destructive' }) {
  return (
    <div
      className={cn('py-8 text-center text-sm')}
      style={tone === 'destructive' ? { color: 'var(--destructive)' } : undefined}
    >
      {children}
    </div>
  );
}

function leaderName(detail: { members: { userId: number; nickname: string }[]; leaderUserId: number | null }) {
  if (detail.leaderUserId === null) return '未指定';
  return detail.members.find((m) => m.userId === detail.leaderUserId)?.nickname ?? '未指定';
}

function formatDate(iso: string | null) {
  if (!iso) return '—';
  // 后端返回 "yyyy-MM-dd HH:mm:ss"
  return iso.slice(0, 10);
}
