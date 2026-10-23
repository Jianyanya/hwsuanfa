import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  Crown,
  Plus,
  Search,
  Snowflake,
  Sun,
  Trash2,
  UserMinus,
  UserPlus,
  Users,
} from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Input, Notice, Select } from '@/components/ui/Button';
import { AssignmentsPanel } from '@/components/admin/AssignmentsPanel';
import { DifficultyTiersPanel } from '@/components/admin/DifficultyTiersPanel';
import { ContestsPanel } from '@/components/admin/ContestsPanel';
import { AwardsAuditPanel } from '@/components/admin/AwardsAuditPanel';
import { MembersPanel } from '@/components/admin/MembersPanel';
import { OverviewPanel } from '@/components/admin/OverviewPanel';
import { AuditLogsPanel } from '@/components/admin/AuditLogsPanel';
import {
  adminAddMember,
  adminChangeLeader,
  adminCreateTeam,
  adminDissolveTeam,
  adminFreezeTeam,
  adminListTeams,
  adminRemoveMember,
  adminSearchUsers,
  adminTeamDetail,
  adminUnfreezeTeam,
  adminUpdateTeam,
} from '@/api/team';
import type { TeamDetail } from '@/types/team';
import { cn } from '@/lib/utils';

type Tab = 'teams' | 'members' | 'assignments' | 'contests' | 'awards' | 'scoring' | 'overview' | 'logs';

const TABS: { key: Tab; label: string }[] = [
  { key: 'teams', label: '队伍管理' },
  { key: 'assignments', label: '作业与每日一题' },
  { key: 'contests', label: '比赛' },
  { key: 'awards', label: '获奖审核' },
  { key: 'members', label: '成员管理' },
  { key: 'scoring', label: '难度与积分' },
  { key: 'overview', label: '全站概况' },
  { key: 'logs', label: '操作日志' },
];

export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('teams');

  return (
    <>
      <PageHeader title="站主后台" description="全站概况与队伍管理" />

      <div className="mb-4 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <Pill key={t.key} active={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}
          </Pill>
        ))}
      </div>

      {tab === 'teams' && <TeamsPanel />}
      {tab === 'assignments' && <AssignmentsPanel />}
      {tab === 'contests' && <ContestsPanel />}
      {tab === 'awards' && <AwardsAuditPanel />}
      {tab === 'members' && <MembersPanel />}
      {tab === 'scoring' && <DifficultyTiersPanel />}

      {tab === 'overview' && <OverviewPanel />}
      {tab === 'logs' && <AuditLogsPanel />}
    </>
  );
}

/* ==================== 队伍管理 ==================== */

function TeamsPanel() {
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  const listQuery = useQuery({ queryKey: ['admin-teams'], queryFn: adminListTeams });
  const teams = listQuery.data ?? [];

  const detailQuery = useQuery({
    queryKey: ['admin-team', selectedId],
    queryFn: () => adminTeamDetail(selectedId!),
    enabled: selectedId !== null,
  });

  /** 任何写操作后都要同时刷新列表、详情与侧栏用的「我的队伍」 */
  function invalidate() {
    void qc.invalidateQueries({ queryKey: ['admin-teams'] });
    void qc.invalidateQueries({ queryKey: ['admin-team'] });
    void qc.invalidateQueries({ queryKey: ['my-teams'] });
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[340px_1fr]">
      {/* ---- 左：队伍列表 ---- */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="text-sm font-medium">
            全部队伍
            <span className="ml-2 text-xs text-muted-foreground">{teams.length} 支</span>
          </div>
          <Button size="sm" variant="primary" onClick={() => setCreating((v) => !v)}>
            <Plus className="h-3.5 w-3.5" />
            新建
          </Button>
        </div>

        {creating && (
          <CreateTeamForm
            onDone={(id) => {
              setCreating(false);
              setSelectedId(id);
              invalidate();
            }}
            onCancel={() => setCreating(false)}
          />
        )}

        {listQuery.isLoading && <Muted>加载中…</Muted>}
        {listQuery.isError && (
          <Notice tone="danger">加载失败：{(listQuery.error as Error).message}</Notice>
        )}

        <div className="space-y-2">
          {teams.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setSelectedId(t.id)}
              className={cn(
                'w-full rounded-[var(--radius)] border p-3 text-left transition-colors',
                selectedId === t.id && 'ring-1',
              )}
              style={{
                borderColor: selectedId === t.id ? 'var(--primary)' : 'var(--border)',
                backgroundColor: selectedId === t.id ? 'color-mix(in oklab, var(--primary) 10%, transparent)' : 'transparent',
              }}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium">{t.name}</div>
                  <div className="mt-0.5 truncate text-[11px] text-muted-foreground">
                    队长：{t.leaderNickname ?? '未指定'}
                  </div>
                </div>
                <div className="flex flex-none flex-col items-end gap-1">
                  <span className="text-xs tabular-nums text-muted-foreground">
                    {t.memberCount}/{t.maxMembers}
                  </span>
                  <div className="flex gap-1">
                    {t.status === 'FROZEN' && <Badge tone="warning">冻结</Badge>}
                    {t.full && t.status !== 'FROZEN' && <Badge tone="primary">满员</Badge>}
                  </div>
                </div>
              </div>
            </button>
          ))}
          {!listQuery.isLoading && teams.length === 0 && <Muted>还没有队伍，点「新建」创建。</Muted>}
        </div>
      </div>

      {/* ---- 右：队伍详情 ---- */}
      <div>
        {selectedId === null && (
          <Card>
            <div className="flex flex-col items-center gap-2 py-12 text-center">
              <Users className="h-7 w-7 text-muted-foreground" />
              <div className="text-sm text-muted-foreground">从左侧选择一支队伍查看与编辑</div>
            </div>
          </Card>
        )}

        {detailQuery.isLoading && selectedId !== null && <Muted>加载中…</Muted>}
        {detailQuery.isError && (
          <Notice tone="danger">加载失败：{(detailQuery.error as Error).message}</Notice>
        )}
        {detailQuery.data && (
          <TeamDetailPanel team={detailQuery.data} onChanged={invalidate} />
        )}
      </div>
    </div>
  );
}

/* ---------------- 新建队伍 ---------------- */

function CreateTeamForm({
  onDone,
  onCancel,
}: {
  onDone: (id: number) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () => adminCreateTeam({ name: name.trim(), description: description.trim() || undefined }),
    onSuccess: (team) => onDone(team.id),
    onError: (e: Error) => setError(e.message),
  });

  return (
    <Card>
      <div className="space-y-2.5">
        <div className="text-sm font-medium">新建队伍</div>
        <Input
          label="队伍名称"
          name="teamName"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="例如：第一战队"
          maxLength={64}
        />
        <Input
          label="简介（可选）"
          name="teamDesc"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="队伍方向或备注"
          maxLength={500}
        />
        {error && <Notice tone="danger">{error}</Notice>}
        <Notice>
          新队伍暂无成员。建好后请添加队员并指定队长 —— 每队最多 3 人、有且仅有一名队长。
        </Notice>
        <div className="flex gap-2">
          <Button
            variant="primary"
            size="sm"
            disabled={!name.trim() || create.isPending}
            onClick={() => {
              setError(null);
              create.mutate();
            }}
          >
            {create.isPending ? '创建中…' : '创建'}
          </Button>
          <Button size="sm" onClick={onCancel}>
            取消
          </Button>
        </div>
      </div>
    </Card>
  );
}

/* ---------------- 队伍详情面板 ---------------- */

function TeamDetailPanel({ team, onChanged }: { team: TeamDetail; onChanged: () => void }) {
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(false);
  const [pendingRemove, setPendingRemove] = useState<number | null>(null);
  const [newLeaderId, setNewLeaderId] = useState<number | ''>('');
  const [confirmDissolve, setConfirmDissolve] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** 统一包装：把后端错误信息显示出来，成功后刷新 */
  function useAction<T>(fn: (arg: T) => Promise<unknown>) {
    return useMutation({
      mutationFn: fn,
      onSuccess: () => {
        setError(null);
        onChanged();
      },
      onError: (e: Error) => setError(e.message),
    });
  }

  const changeLeader = useAction<number>((uid) => adminChangeLeader(team.id, uid));
  const removeMember = useAction<{ uid: number; newLeader?: number }>((v) =>
    adminRemoveMember(team.id, v.uid, v.newLeader),
  );
  const freeze = useAction<string | undefined>((reason) => adminFreezeTeam(team.id, reason));
  const unfreeze = useAction<void>(() => adminUnfreezeTeam(team.id));
  const dissolve = useAction<void>(() => adminDissolveTeam(team.id));

  const leader = team.members.find((m) => m.userId === team.leaderUserId);
  const others = team.members.filter((m) => m.userId !== team.leaderUserId);
  const isFull = team.memberCount >= team.maxMembers;
  const frozen = team.status === 'FROZEN';
  // 有成员却没有队长是非法状态（正常流程不会出现，留给人工修数据后自查）
  const missingLeader = team.members.length > 0 && !leader;

  /** 移除队长时后端要求先指定新队长，这里先把交互拦住，避免白跑一次请求 */
  const removingLeader = pendingRemove !== null && pendingRemove === team.leaderUserId;

  return (
    <Card>
      {/* 头部 */}
      <div className="flex flex-wrap items-start justify-between gap-3 border-b pb-4" style={{ borderColor: 'var(--border)' }}>
        <div className="min-w-0">
          {editing ? (
            <EditTeamForm
              team={team}
              onDone={() => {
                setEditing(false);
                onChanged();
              }}
              onCancel={() => setEditing(false)}
            />
          ) : (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-lg font-semibold">{team.name}</h2>
                {frozen ? <Badge tone="warning">已冻结</Badge> : <Badge tone="success">正常</Badge>}
                {isFull && <Badge tone="primary">已满员</Badge>}
              </div>
              <p className="mt-1 text-sm text-muted-foreground">
                {team.description || '暂无简介'}
              </p>
            </>
          )}
        </div>

        {!editing && (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" onClick={() => setEditing(true)}>
              编辑信息
            </Button>
            {frozen ? (
              <Button size="sm" onClick={() => unfreeze.mutate()} disabled={unfreeze.isPending}>
                <Sun className="h-3.5 w-3.5" />
                解冻
              </Button>
            ) : (
              <Button
                size="sm"
                onClick={() => freeze.mutate('由站主冻结')}
                disabled={freeze.isPending}
              >
                <Snowflake className="h-3.5 w-3.5" />
                冻结
              </Button>
            )}
            <Button size="sm" variant="danger" onClick={() => setConfirmDissolve(true)}>
              <Trash2 className="h-3.5 w-3.5" />
              解散
            </Button>
          </div>
        )}
      </div>

      {error && (
        <div className="mt-3">
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      {confirmDissolve && (
        <div className="mt-3">
          <Notice tone="danger">
            <div className="font-medium">确认解散「{team.name}」？</div>
            <div className="mt-1">
              若该队已有作业、比赛、题单或公告记录，系统会拒绝删除并建议改为冻结 ——
              历史任务与积分记录不会被抹掉。
            </div>
            <div className="mt-2 flex gap-2">
              <Button
                size="sm"
                variant="danger"
                disabled={dissolve.isPending}
                onClick={() => dissolve.mutate()}
              >
                {dissolve.isPending ? '处理中…' : '确认解散'}
              </Button>
              <Button size="sm" onClick={() => setConfirmDissolve(false)}>
                取消
              </Button>
            </div>
          </Notice>
        </div>
      )}

      {/* 成员 */}
      <div className="mt-4">
        <div className="mb-3 flex items-center justify-between">
          <div className="text-sm font-medium">
            成员
            <span className="ml-2 text-xs text-muted-foreground">
              {team.memberCount}/{team.maxMembers} 人
            </span>
          </div>
          <Button
            size="sm"
            variant="primary"
            disabled={isFull || frozen}
            title={frozen ? '队伍已冻结' : isFull ? `已达上限 ${team.maxMembers} 人` : ''}
            onClick={() => setAdding((v) => !v)}
          >
            <UserPlus className="h-3.5 w-3.5" />
            {isFull ? '已满员' : '添加成员'}
          </Button>
        </div>

        {frozen && (
          <div className="mb-3">
            <Notice tone="warning">
              队伍已冻结，不能增减成员。{team.frozenReason && `原因：${team.frozenReason}`}
            </Notice>
          </div>
        )}

        {missingLeader && (
          <div className="mb-3">
            <Notice tone="danger">
              <div className="flex items-start gap-2">
                <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-none" />
                <span>
                  该队有成员但没有队长，属于异常状态。请点某个成员的「设为队长」修复 ——
                  每支队伍必须有且仅有一名队长。
                </span>
              </div>
            </Notice>
          </div>
        )}

        {adding && !isFull && !frozen && (
          <div className="mb-3">
            <AddMemberPicker
              existingIds={team.members.map((m) => m.userId)}
              teamId={team.id}
              noLeaderYet={team.leaderUserId === null}
              onDone={() => {
                setAdding(false);
                onChanged();
              }}
              onCancel={() => setAdding(false)}
            />
          </div>
        )}

        <div className="space-y-2">
          {team.members.map((m) => (
            <div
              key={m.userId}
              className="flex flex-wrap items-center gap-3 rounded-[var(--radius)] border p-3"
              style={{ borderColor: 'var(--border)' }}
            >
              <div
                className="flex h-8 w-8 flex-none items-center justify-center rounded-full text-xs font-medium"
                style={{ backgroundColor: 'var(--muted)' }}
              >
                {m.nickname.slice(0, 1)}
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-sm font-medium">{m.nickname}</span>
                  {m.leader && (
                    <Badge tone="primary">
                      <Crown className="mr-0.5 inline h-3 w-3" />
                      队长
                    </Badge>
                  )}
                </div>
                <div className="text-[11px] text-muted-foreground">
                  @{m.username}
                  {m.realName && ` · ${m.realName}`}
                  {m.studentNo && ` · ${m.studentNo}`}
                </div>
              </div>

              <div className="flex flex-none gap-2">
                {!m.leader && (
                  <Button
                    size="sm"
                    disabled={changeLeader.isPending}
                    onClick={() => changeLeader.mutate(m.userId)}
                  >
                    设为队长
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="danger"
                  disabled={removeMember.isPending}
                  onClick={() => {
                    setNewLeaderId('');
                    setPendingRemove(m.userId);
                  }}
                >
                  <UserMinus className="h-3.5 w-3.5" />
                  移除
                </Button>
              </div>
            </div>
          ))}
          {team.members.length === 0 && <Muted>该队暂无成员，请先添加队员并指定队长。</Muted>}
        </div>

        {/* 移除队长的二次确认：必须先指定新队长 */}
        {pendingRemove !== null && (
          <div className="mt-3">
            {removingLeader ? (
              others.length === 0 ? (
                <Notice tone="warning">
                  <div className="flex items-start gap-2">
                    <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-none" />
                    <span>
                      队伍仅剩队长一人，不能直接移除。请先添加新成员并指定为队长，或直接解散队伍。
                    </span>
                  </div>
                  <div className="mt-2">
                    <Button size="sm" onClick={() => setPendingRemove(null)}>
                      知道了
                    </Button>
                  </div>
                </Notice>
              ) : (
                <Notice tone="warning">
                  <div className="font-medium">被移除的是队长，请先指定新队长</div>
                  <div className="mt-2 max-w-xs">
                    <Select
                      name="newLeader"
                      value={newLeaderId}
                      onChange={(e) =>
                        setNewLeaderId(e.target.value === '' ? '' : Number(e.target.value))
                      }
                    >
                      <option value="">选择新队长…</option>
                      {others.map((m) => (
                        <option key={m.userId} value={m.userId}>
                          {m.nickname}（@{m.username}）
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div className="mt-2 flex gap-2">
                    <Button
                      size="sm"
                      variant="danger"
                      disabled={newLeaderId === '' || removeMember.isPending}
                      onClick={() => {
                        removeMember.mutate({ uid: pendingRemove, newLeader: Number(newLeaderId) });
                        setPendingRemove(null);
                      }}
                    >
                      确认移除
                    </Button>
                    <Button size="sm" onClick={() => setPendingRemove(null)}>
                      取消
                    </Button>
                  </div>
                </Notice>
              )
            ) : (
              <Notice tone="danger">
                <div>确认从「{team.name}」移除该成员？</div>
                <div className="mt-2 flex gap-2">
                  <Button
                    size="sm"
                    variant="danger"
                    disabled={removeMember.isPending}
                    onClick={() => {
                      removeMember.mutate({ uid: pendingRemove });
                      setPendingRemove(null);
                    }}
                  >
                    确认移除
                  </Button>
                  <Button size="sm" onClick={() => setPendingRemove(null)}>
                    取消
                  </Button>
                </div>
              </Notice>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}

/* ---------------- 编辑队伍信息 ---------------- */

function EditTeamForm({
  team,
  onDone,
  onCancel,
}: {
  team: TeamDetail;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(team.name);
  const [description, setDescription] = useState(team.description ?? '');
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      adminUpdateTeam(team.id, { name: name.trim(), description: description.trim() }),
    onSuccess: onDone,
    onError: (e: Error) => setError(e.message),
  });

  return (
    <div className="w-full max-w-md space-y-2.5">
      <Input label="队伍名称" name="editName" value={name} onChange={(e) => setName(e.target.value)} maxLength={64} />
      <Input label="简介" name="editDesc" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />
      {error && <Notice tone="danger">{error}</Notice>}
      <div className="flex gap-2">
        <Button
          size="sm"
          variant="primary"
          disabled={!name.trim() || save.isPending}
          onClick={() => {
            setError(null);
            save.mutate();
          }}
        >
          {save.isPending ? '保存中…' : '保存'}
        </Button>
        <Button size="sm" onClick={onCancel}>
          取消
        </Button>
      </div>
    </div>
  );
}

/* ---------------- 添加成员选择器 ---------------- */

function AddMemberPicker({
  teamId,
  existingIds,
  noLeaderYet,
  onDone,
  onCancel,
}: {
  teamId: number;
  existingIds: number[];
  noLeaderYet: boolean;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [asLeader, setAsLeader] = useState(noLeaderYet);
  const [error, setError] = useState<string | null>(null);

  const usersQuery = useQuery({
    queryKey: ['admin-users', keyword],
    queryFn: () => adminSearchUsers(keyword || undefined),
  });

  const add = useMutation({
    mutationFn: (userId: number) => adminAddMember(teamId, userId, asLeader),
    onSuccess: onDone,
    onError: (e: Error) => setError(e.message),
  });

  const candidates = (usersQuery.data ?? []).filter((u) => !existingIds.includes(u.id));

  return (
    <div className="rounded-[var(--radius)] border p-3" style={{ borderColor: 'var(--border)' }}>
      <div className="mb-2 flex items-center gap-2">
        <Search className="h-3.5 w-3.5 text-muted-foreground" />
        <input
          className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          placeholder="搜索用户名 / 昵称 / 真实姓名 / 学号"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>

      {noLeaderYet && (
        <div className="mb-2">
          <Notice tone="warning">本队尚未指定队长，首位成员将自动成为队长。</Notice>
        </div>
      )}

      {!noLeaderYet && (
        <label className="mb-2 flex items-center gap-2 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={asLeader}
            onChange={(e) => setAsLeader(e.target.checked)}
          />
          同时指定为队长（会替换当前队长）
        </label>
      )}

      {error && (
        <div className="mb-2">
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <div className="max-h-56 space-y-1 overflow-y-auto">
        {usersQuery.isLoading && <Muted>搜索中…</Muted>}
        {candidates.map((u) => (
          <button
            key={u.id}
            type="button"
            disabled={add.isPending}
            onClick={() => {
              setError(null);
              add.mutate(u.id);
            }}
            className="flex w-full items-center justify-between gap-2 rounded-[var(--radius)] px-2 py-1.5 text-left text-sm hover:bg-[var(--muted)] disabled:opacity-50"
          >
            <span className="min-w-0 truncate">
              {u.nickname}
              <span className="ml-1.5 text-[11px] text-muted-foreground">@{u.username}</span>
              {u.studentNo && (
                <span className="ml-1.5 text-[11px] text-muted-foreground">{u.studentNo}</span>
              )}
            </span>
            <Plus className="h-3.5 w-3.5 flex-none text-muted-foreground" />
          </button>
        ))}
        {!usersQuery.isLoading && candidates.length === 0 && (
          <Muted>{keyword ? '没有匹配的用户' : '没有可添加的用户'}</Muted>
        )}
      </div>

      <div className="mt-2 flex justify-end">
        <Button size="sm" onClick={onCancel}>
          关闭
        </Button>
      </div>
    </div>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-3 text-center text-xs text-muted-foreground">{children}</div>;
}
