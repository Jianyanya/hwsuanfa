import { useEffect, useMemo, useState } from 'react';
import { calendarToday } from '@/lib/date';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarPlus, Plus, Search, Trash2, X } from 'lucide-react';

import { Card } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Input, Notice, Select } from '@/components/ui/Button';
import {
  adminCreateAssignment,
  adminCreateProblem,
  adminDeleteAssignment,
  adminListAssignments,
  adminSearchProblems,
  fetchDifficultyTiers,
} from '@/api/assignment';
import { importAssignmentProblems } from '@/api/assignment';
import { backfillProblemMetadata } from '@/api/community';
import { adminListTeams } from '@/api/team';
import { fetchMyProblemLists } from '@/api/problemList';
import type { Assignment, AssignmentType, ProblemBrief } from '@/types/assignment';
import { OJ_PLATFORMS } from '@/config/constants';

type Tab = 'ALL' | AssignmentType;

const TABS: { key: Tab; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'TEAM', label: '队伍作业' },
  { key: 'GLOBAL', label: '全体作业' },
  { key: 'DAILY', label: '每日一题' },
];

/**
 * 站主后台 —— 作业管理。
 *
 * ⚠️ 全体作业不要传 teamIds：目标队伍由后端在**发布那一刻**取全部有效队伍做成快照，
 *    传了也会被忽略。这是为了防止「发布后新增的队伍被算进历史作业」。
 */
export function AssignmentsPanel() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>('ALL');
  const [creating, setCreating] = useState(false);
  const [managingProblems, setManagingProblems] = useState(false);

  const list = useQuery({
    queryKey: ['admin-assignments', tab],
    queryFn: () => adminListAssignments(tab === 'ALL' ? undefined : tab, false),
  });

  function invalidate() {
    void qc.invalidateQueries({ queryKey: ['admin-assignments'] });
    void qc.invalidateQueries({ queryKey: ['my-assignments'] });
    void qc.invalidateQueries({ queryKey: ['daily-today'] });
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {TABS.map((t) => (
            <Pill key={t.key} active={tab === t.key} onClick={() => setTab(t.key)}>
              {t.label}
            </Pill>
          ))}
        </div>
        <div className="flex gap-2">
          <Button size="sm" onClick={() => setManagingProblems((v) => !v)}>
            题目库
          </Button>
          <Button size="sm" variant="primary" onClick={() => setCreating((v) => !v)}>
            <Plus className="h-3.5 w-3.5" />
            发布作业
          </Button>
        </div>
      </div>

      {managingProblems && (
        <ProblemsPanel onClose={() => setManagingProblems(false)} />
      )}

      {creating && (
        <CreateAssignmentForm
          defaultType={tab === 'ALL' ? 'GLOBAL' : tab}
          onDone={() => {
            setCreating(false);
            invalidate();
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      {list.isLoading && <Muted>加载中…</Muted>}
      {list.isError && <Notice tone="danger">加载失败：{(list.error as Error).message}</Notice>}

      {list.data && list.data.length === 0 && <Muted>还没有作业</Muted>}

      <div className="space-y-3">
        {(list.data ?? []).map((a) => (
          <AdminAssignmentRow key={a.id} assignment={a} onChanged={invalidate} />
        ))}
      </div>
    </div>
  );
}

/* ---------------- 作业行 ---------------- */

function AdminAssignmentRow({
  assignment: a,
  onChanged,
}: {
  assignment: Assignment;
  onChanged: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importText, setImportText] = useState('');
  const [importMsg, setImportMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const del = useMutation({
    mutationFn: () => adminDeleteAssignment(a.id),
    onSuccess: onChanged,
    onError: (e: Error) => setError(e.message),
  });

  const importProblems = useMutation({
    mutationFn: () => importAssignmentProblems(a.id, importText),
    onSuccess: (updated) => {
      setImportMsg(`导入成功，当前共 ${updated.problems?.length ?? '?'} 题`);
      setImportText('');
      onChanged();
    },
    onError: (e: Error) => {
      setImportMsg(e.message);
    },
  });

  const s = a.stats;

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <TypeTag type={a.type} />
            <span className="text-sm font-medium">{a.title}</span>
            <TimeStatusTag status={a.timeStatus} />
            {!a.allowLateReward && <Badge tone="warning">逾期不给分</Badge>}
          </div>

          <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
            <span>{fmt(a.startAt)} → {fmt(a.dueAt)}</span>
            <span>{a.totalCount} 道题</span>
            {a.scoreReward > 0 && <span>{a.scoreReward} 分</span>}
            {a.publishDate && <span>发布日 {a.publishDate}</span>}
          </div>

          {s && (
            <div className="mt-2 flex flex-wrap items-center gap-3 text-xs">
              {s.targetTeams === 0 ? (
                // 没有目标队伍 = 不限定范围（常见于不限队伍的每日一题），
                // 直接说「面向全体」比显示「0 支队伍」清楚
                <span className="text-muted-foreground">面向全体（未限定队伍）</span>
              ) : (
                <span className="text-muted-foreground">
                  目标 <strong className="text-foreground">{s.targetTeams}</strong> 支队伍 /
                  <strong className="text-foreground"> {s.targetUsers}</strong> 人
                </span>
              )}
              <span style={{ color: 'var(--success)' }}>已完成 {s.completedUsers}</span>
              {s.overdueUsers > 0 && (
                <span style={{ color: 'var(--destructive)' }}>逾期未完成 {s.overdueUsers}</span>
              )}
            </div>
          )}
        </div>

        <Button size="sm" variant="danger" onClick={() => setConfirming(true)}>
          <Trash2 className="h-3.5 w-3.5" />
          删除
        </Button>
      </div>

      {error && (
        <div className="mt-3">
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      <div className="mt-2">
        <button
          type="button"
          className="text-[11px] text-muted-foreground hover:text-foreground"
          onClick={() => {
            setImporting(!importing);
            setImportMsg(null);
          }}
        >
          📋 贴链接导入题目
        </button>
        {importing && (
          <div className="mt-1.5">
            <textarea
              className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1.5 font-mono text-xs outline-none"
              style={{ borderColor: 'var(--input)', minHeight: 60 }}
              placeholder={'每行一个，支持链接或「平台 题号」：\nhttps://www.luogu.com.cn/problem/P1001\nCF 1932A'}
              value={importText}
              onChange={(e) => setImportText(e.target.value)}
            />
            <div className="mt-1.5 flex items-center gap-2">
              <Button
                size="sm"
                disabled={!importText.trim() || importProblems.isPending}
                onClick={() => importProblems.mutate()}
              >
                {importProblems.isPending ? '导入中…' : '导入'}
              </Button>
              {importMsg && (
                <span className="text-[11px] text-muted-foreground">{importMsg}</span>
              )}
            </div>
          </div>
        )}
      </div>

      {confirming && (
        <div className="mt-3">
          <Notice tone="danger">
            <div>确认删除「{a.title}」？</div>
            <div className="mt-1">
              若已有人完成，系统会拒绝删除，避免积分流水指向不存在的作业。
            </div>
            <div className="mt-2 flex gap-2">
              <Button
                size="sm"
                variant="danger"
                disabled={del.isPending}
                onClick={() => {
                  setError(null);
                  del.mutate();
                  setConfirming(false);
                }}
              >
                确认删除
              </Button>
              <Button size="sm" onClick={() => setConfirming(false)}>
                取消
              </Button>
            </div>
          </Notice>
        </div>
      )}
    </Card>
  );
}

/* ---------------- 发布作业 ---------------- */

function CreateAssignmentForm({
  defaultType,
  onDone,
  onCancel,
}: {
  defaultType: AssignmentType;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [type, setType] = useState<AssignmentType>(defaultType);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [problems, setProblems] = useState<ProblemBrief[]>([]);
  const [problemListId, setProblemListId] = useState<number | ''>('');
  const [teamIds, setTeamIds] = useState<number[]>([]);
  const [startAt, setStartAt] = useState(defaultDateTimeLocal(0));
  const [dueAt, setDueAt] = useState(defaultDateTimeLocal(7));
  const [scoreReward, setScoreReward] = useState('10');
  const [allowLateReward, setAllowLateReward] = useState(true);
  const [publishDate, setPublishDate] = useState(todayStr());
  const [error, setError] = useState<string | null>(null);

  const teams = useQuery({ queryKey: ['admin-teams'], queryFn: adminListTeams });
  // 发布作业时可以直接吃一份题单打底，再手工补几道
  const lists = useQuery({ queryKey: ['problem-lists'], queryFn: fetchMyProblemLists });

  const create = useMutation({
    mutationFn: () =>
      adminCreateAssignment({
        type,
        title: title.trim(),
        description: description.trim() || undefined,
        problemIds: problems.map((p) => p.id),
        problemListId: problemListId === '' ? undefined : Number(problemListId),
        // 全体作业不传队伍，由后端快照
        teamIds: type === 'GLOBAL' ? undefined : teamIds,
        startAt: toBackendTime(startAt),
        dueAt: toBackendTime(dueAt),
        scoreReward: Number(scoreReward) || 0,
        allowLateReward,
        publishDate: type === 'DAILY' ? publishDate : undefined,
      }),
    onSuccess: onDone,
    onError: (e: Error) => setError(e.message),
  });

  const needTeams = type === 'TEAM' && teamIds.length === 0;

  return (
    <Card>
      <div className="space-y-3">
        <div className="text-sm font-medium">发布作业</div>

        <div className="flex flex-wrap gap-2">
          {(['GLOBAL', 'TEAM', 'DAILY'] as AssignmentType[]).map((t) => (
            <Pill key={t} active={type === t} onClick={() => setType(t)}>
              {t === 'GLOBAL' ? '全体作业' : t === 'TEAM' ? '队伍作业' : '每日一题'}
            </Pill>
          ))}
        </div>

        <Input
          label="标题"
          name="title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="例如：第一周训练题"
          maxLength={200}
        />

        <Input
          label="说明（可选）"
          name="desc"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="作业要求或提示"
          maxLength={5000}
        />

        {/* 题单打底（可选） */}
        <div className="rounded-[var(--radius)] border p-3" style={{ borderColor: 'var(--border)' }}>
          <div className="mb-1.5 text-xs font-medium">从题单导入（可选）</div>
          <Select
            name="problemListId"
            value={problemListId}
            onChange={(e) => setProblemListId(e.target.value === '' ? '' : Number(e.target.value))}
          >
            <option value="">不导入，只用下面手工选的题</option>
            {(lists.data ?? []).map((l) => (
              <option key={l.id} value={l.id}>
                {l.name}（{l.problemCount} 道
                {l.scope === 'TEAM' ? ` · ${l.teamName ?? '队伍'}` : ' · 个人'}）
              </option>
            ))}
          </Select>
          <div className="mt-1.5 text-[11px] text-muted-foreground">
            题单里的题目会和下面手工选的题<strong className="text-foreground">合并去重</strong>。
            发布后题目会固化到作业上，之后改题单不会影响已发布的作业。
          </div>
        </div>

        {/* 题目选择 */}
        <ProblemPicker value={problems} onChange={setProblems} />

        {/* 目标队伍 */}
        {type === 'TEAM' && (
          <div>
            <div className="mb-1 text-xs text-muted-foreground">
              目标队伍 <span className="text-[10px]">（至少 1 支）</span>
            </div>
            <div className="flex flex-wrap gap-2">
              {(teams.data ?? []).map((t) => {
                const on = teamIds.includes(t.id);
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() =>
                      setTeamIds((v) => (on ? v.filter((x) => x !== t.id) : [...v, t.id]))
                    }
                    className="rounded-[var(--radius)] border px-2.5 py-1 text-xs"
                    style={{
                      borderColor: on ? 'var(--primary)' : 'var(--border)',
                      backgroundColor: on
                        ? 'color-mix(in oklab, var(--primary) 14%, transparent)'
                        : 'transparent',
                    }}
                  >
                    {t.name}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {type === 'GLOBAL' && (
          <Notice>
            全体作业的范围在<strong className="text-foreground">发布这一刻</strong>确定：会把当时所有正常状态的队伍记为快照。
            之后新增的队伍不会被算进这份作业，历史范围也不会变。
          </Notice>
        )}

        {type === 'DAILY' && (
          <Input
            label="发布日（同一天只能有一条；时间自动为当天 0 点 → 次日 0 点）"
            name="publishDate"
            type="date"
            value={publishDate}
            onChange={(e) => setPublishDate(e.target.value)}
          />
        )}

        <div className="grid gap-3 sm:grid-cols-2">
          <Input
            label="开始时间"
            name="startAt"
            type="datetime-local"
            value={type === 'DAILY' ? `${publishDate}T00:00` : startAt}
            onChange={(e) => setStartAt(e.target.value)}
            disabled={type === 'DAILY'}
          />
          <Input
            label={type === 'DAILY' ? '截止时间（次日 0 点，自动）' : '截止时间'}
            name="dueAt"
            type="datetime-local"
            value={type === 'DAILY' ? `${publishDate}T23:59` : dueAt}
            onChange={(e) => setDueAt(e.target.value)}
            disabled={type === 'DAILY'}
          />
        </div>
        {type !== 'DAILY' && startAt >= dueAt && dueAt && (
          <Notice tone="danger">截止时间必须晚于开始时间</Notice>
        )}

        <div className="grid gap-3 sm:grid-cols-2">
          <Input
            label="奖励积分"
            name="score"
            type="number"
            min={0}
            value={scoreReward}
            onChange={(e) => setScoreReward(e.target.value)}
          />
          <div>
            <div className="mb-1 text-xs text-muted-foreground">逾期完成是否给分</div>
            <label className="flex h-[34px] items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={allowLateReward}
                onChange={(e) => setAllowLateReward(e.target.checked)}
              />
              {allowLateReward ? '给分（保留完成记录）' : '不给分（仅记录完成）'}
            </label>
          </div>
        </div>

        {error && <Notice tone="danger">{error}</Notice>}
        {needTeams && <Notice tone="warning">队伍作业至少要选一支队伍。</Notice>}

        <div className="flex gap-2">
          <Button
            size="sm"
            variant="primary"
            disabled={
              create.isPending ||
              !title.trim() ||
              // 题目可以来自「手工选的」或「题单导入的」，两者有其一即可
              (problems.length === 0 && problemListId === '') ||
              needTeams
            }
            onClick={() => {
              setError(null);
              create.mutate();
            }}
          >
            <CalendarPlus className="h-3.5 w-3.5" />
            {create.isPending ? '发布中…' : '发布'}
          </Button>
          <Button size="sm" onClick={onCancel}>
            取消
          </Button>
        </div>
      </div>
    </Card>
  );
}

/* ---------------- 题目选择器 ---------------- */

function ProblemPicker({
  value,
  onChange,
}: {
  value: ProblemBrief[];
  onChange: (v: ProblemBrief[]) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [newProblem, setNewProblem] = useState(false);

  const list = useQuery({
    queryKey: ['admin-problems', keyword],
    queryFn: () => adminSearchProblems(keyword || undefined),
  });

  const selectedIds = useMemo(() => new Set(value.map((p) => p.id)), [value]);
  const candidates = (list.data ?? []).filter((p) => !selectedIds.has(p.id));

  return (
    <div className="rounded-[var(--radius)] border p-3" style={{ borderColor: 'var(--border)' }}>
      <div className="mb-2 flex items-center justify-between gap-2">
        <div className="text-xs text-muted-foreground">
          题目 <span className="text-foreground">{value.length}</span> 道
        </div>
        <Button size="sm" onClick={() => setNewProblem((v) => !v)}>
          <Plus className="h-3 w-3" />
          新建题目
        </Button>
      </div>

      {value.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1.5">
          {value.map((p) => (
            <span
              key={p.id}
              className="inline-flex items-center gap-1 rounded-[var(--radius)] border px-2 py-0.5 text-[11px]"
              style={{ borderColor: 'var(--border)' }}
            >
              {p.title}
              <button
                type="button"
                onClick={() => onChange(value.filter((x) => x.id !== p.id))}
                aria-label={`移除 ${p.title}`}
              >
                <X className="h-3 w-3 text-muted-foreground hover:text-foreground" />
              </button>
            </span>
          ))}
        </div>
      )}

      {newProblem && (
        <div className="mb-2">
          <NewProblemForm
            onCreated={(p) => {
              onChange([...value, p]);
              setNewProblem(false);
            }}
            onCancel={() => setNewProblem(false)}
          />
        </div>
      )}

      <div className="mb-2 flex items-center gap-2 border-b pb-2" style={{ borderColor: 'var(--border)' }}>
        <Search className="h-3.5 w-3.5 text-muted-foreground" />
        <input
          className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          placeholder="搜索题目名 / 题号 / 标签"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>

      <div className="max-h-44 space-y-0.5 overflow-y-auto">
        {list.isLoading && <Muted>搜索中…</Muted>}
        {candidates.map((p) => (
          <button
            key={p.id}
            type="button"
            onClick={() => onChange([...value, p])}
            className="flex w-full items-center justify-between gap-2 rounded-[var(--radius)] px-2 py-1.5 text-left text-sm hover:bg-[var(--muted)]"
          >
            <span className="min-w-0 truncate">
              {p.title}
              <span className="ml-1.5 text-[11px] text-muted-foreground">
                {p.platform} {p.problemNo}
              </span>
            </span>
            <Plus className="h-3.5 w-3.5 flex-none text-muted-foreground" />
          </button>
        ))}
        {!list.isLoading && candidates.length === 0 && (
          <Muted>{keyword ? '没有匹配的题目' : '题目库为空，先「新建题目」'}</Muted>
        )}
      </div>
    </div>
  );
}

function NewProblemForm({
  onCreated,
  onCancel,
}: {
  onCreated: (p: ProblemBrief) => void;
  onCancel: () => void;
}) {
  const [title, setTitle] = useState('');
  const [platform, setPlatform] = useState('CODEFORCES');
  const [problemNo, setProblemNo] = useState('');
  const [url, setUrl] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [error, setError] = useState<string | null>(null);

  // 难度选项按平台从后台配置里取 —— 不同平台档位口径完全不同
  // （洛谷是档位名、CF 是 rating 分段），不能用一个通用下拉
  const tiers = useQuery({
    queryKey: ['difficulty-tiers', platform],
    queryFn: () => fetchDifficultyTiers(platform),
  });

  // 换平台后原来的档位就不再适用，清掉避免提交一个不属于该平台的难度
  useEffect(() => {
    setDifficulty('');
  }, [platform]);

  const create = useMutation({
    mutationFn: () =>
      adminCreateProblem({
        title: title.trim(),
        platform,
        problemNo: problemNo.trim() || undefined,
        url: url.trim() || undefined,
        difficulty: difficulty || undefined,
      }),
    onSuccess: onCreated,
    onError: (e: Error) => setError(e.message),
  });

  const selectedTier = (tiers.data ?? []).find((t) => t.difficultyKey === difficulty);

  return (
    <div className="space-y-2 rounded-[var(--radius)] border p-3" style={{ borderColor: 'var(--border)' }}>
      <Input
        label="题目名称"
        name="pTitle"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="例如：Two Sum"
      />
      <div className="grid gap-2 sm:grid-cols-2">
        <Select
          label="来源平台"
          name="pPlatform"
          value={platform}
          onChange={(e) => setPlatform(e.target.value)}
        >
          {OJ_PLATFORMS.map((p) => (
            <option key={p.code} value={p.code}>
              {p.label}
            </option>
          ))}
          <option value="OTHER">其他</option>
        </Select>
        <Input
          label="题号"
          name="pNo"
          value={problemNo}
          onChange={(e) => setProblemNo(e.target.value)}
          placeholder="例如 1932F"
        />
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <Select
          label="难度档位"
          name="pDifficulty"
          value={difficulty}
          onChange={(e) => setDifficulty(e.target.value)}
        >
          <option value="">未标难度（按平台兜底档计分）</option>
          {(tiers.data ?? []).map((t) => (
            <option key={t.difficultyKey} value={t.difficultyKey}>
              {t.label}（{t.score} 分）
            </option>
          ))}
        </Select>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">首次完成得分</div>
          <div className="flex h-[34px] items-center text-sm">
            {selectedTier ? (
              <span className="font-medium" style={{ color: 'var(--primary)' }}>
                {selectedTier.score} 分
              </span>
            ) : (
              <span className="text-muted-foreground">按平台兜底档</span>
            )}
          </div>
        </div>
      </div>

      <Input
        label="题目链接"
        name="pUrl"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        placeholder="https://..."
      />
      {error && <Notice tone="danger">{error}</Notice>}
      <div className="flex gap-2">
        <Button
          size="sm"
          variant="primary"
          disabled={!title.trim() || create.isPending}
          onClick={() => {
            setError(null);
            create.mutate();
          }}
        >
          {create.isPending ? '创建中…' : '创建并加入作业'}
        </Button>
        <Button size="sm" onClick={onCancel}>
          取消
        </Button>
      </div>
    </div>
  );
}

/* ---------------- 题目库管理 ---------------- */

function ProblemsPanel({ onClose }: { onClose: () => void }) {
  const [bfMsg, setBfMsg] = useState<string | null>(null);
  const backfill = useMutation({
    mutationFn: () => backfillProblemMetadata(),
    onSuccess: (r) => setBfMsg(`已回填 ${r.updated} 道，跳过 ${r.skipped} 道，失败 ${r.failed} 道，剩余 ${r.remaining} 道待回填（可再次点击）`),
    onError: (e: Error) => setBfMsg(e.message),
  });

  const [keyword, setKeyword] = useState('');
  const list = useQuery({
    queryKey: ['admin-problems', keyword],
    queryFn: () => adminSearchProblems(keyword || undefined),
  });

  return (
    <Card>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-col gap-1.5">
          <Button
            size="sm"
            variant="primary"
            disabled={backfill.isPending}
            onClick={() => {
              setBfMsg('回填中，服务端会按洛谷访问限制逐题处理，请稍候…');
              backfill.mutate();
            }}
          >
            {backfill.isPending ? '回填中…' : '🔄 回填题目元数据（难度/标签）'}
          </Button>
          {bfMsg && <span className="text-[11px] text-muted-foreground">{bfMsg}</span>}
        </div>
        <Button size="sm" onClick={onClose}>
          收起
        </Button>
      </div>
      <input
        className="mb-2 w-full rounded-[var(--radius)] border bg-transparent px-3 py-1.5 text-sm outline-none placeholder:text-muted-foreground"
        style={{ borderColor: 'var(--input)' }}
        placeholder="搜索题目"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />
      <div className="max-h-60 space-y-1 overflow-y-auto">
        {list.isLoading && <Muted>加载中…</Muted>}
        {(list.data ?? []).map((p) => (
          <div
            key={p.id}
            className="flex items-center justify-between gap-2 rounded-[var(--radius)] border px-3 py-1.5 text-sm"
            style={{ borderColor: 'var(--border)' }}
          >
            <span className="min-w-0 truncate">
              {p.title}
              <span className="ml-1.5 text-[11px] text-muted-foreground">
                {p.platform} {p.problemNo}
              </span>
            </span>
            {p.url && (
              <a
                href={p.url}
                target="_blank"
                rel="noreferrer"
                className="flex-none text-[11px] text-muted-foreground hover:text-foreground"
              >
                打开
              </a>
            )}
          </div>
        ))}
        {!list.isLoading && (list.data ?? []).length === 0 && <Muted>题目库为空</Muted>}
      </div>
    </Card>
  );
}

/* ---------------- 小组件 ---------------- */

function TypeTag({ type }: { type: AssignmentType }) {
  const label = type === 'GLOBAL' ? '全体作业' : type === 'TEAM' ? '队伍作业' : '每日一题';
  return <span className="badge flex-none">{label}</span>;
}

function TimeStatusTag({ status }: { status: Assignment['timeStatus'] }) {
  if (status === 'OVERDUE') return <Badge tone="destructive">已逾期</Badge>;
  if (status === 'NOT_STARTED') return <Badge>未开始</Badge>;
  return <Badge tone="success">进行中</Badge>;
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-3 text-center text-xs text-muted-foreground">{children}</div>;
}

/* ---------------- 时间工具 ---------------- */

function pad(n: number) {
  return String(n).padStart(2, '0');
}

/** datetime-local 需要 "yyyy-MM-ddTHH:mm" */
function defaultDateTimeLocal(offsetDays: number, hour = 12) {
  const d = calendarToday();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(hour)}:00`;
}

function todayStr() {
  const d = calendarToday();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 转成后端约定的 "yyyy-MM-dd HH:mm:ss" */
function toBackendTime(local: string) {
  if (!local) return local;
  const normalized = local.replace('T', ' ');
  return normalized.length === 16 ? `${normalized}:00` : normalized;
}

function fmt(s: string | null) {
  if (!s) return '—';
  return s.slice(0, 16);
}
