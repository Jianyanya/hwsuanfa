import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardPaste,
  ExternalLink,
  FileText,
  Plus,
  Trash2,
  Users,
  X,
} from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Input, Notice, Select } from '@/components/ui/Button';
import {
  createProblemList,
  deleteProblemList,
  fetchMyProblemLists,
  fetchProblemListDetail,
  importProblems,
  parsePreview,
  removeProblem,
} from '@/api/problemList';
import { fetchMyTeams } from '@/api/team';
import type { ListScope, ProblemList } from '@/types/assignment';
import { OJ_PLATFORMS } from '@/config/constants';
import { cn } from '@/lib/utils';

/**
 * 题单。
 *
 * 核心是「批量粘贴导入」：我们不是 OJ，不抓题面，
 * 所以录题的主要方式就是粘一段题号文本，后端解析成「平台 + 题号」的引用。
 * 标题可以留空（用题号占位），页面上直接给外链。
 */
export default function ProblemsetPage() {
  const { listId } = useParams();
  const id = listId ? Number(listId) : null;

  if (id) {
    return <ListDetail id={id} />;
  }
  return <ListIndex />;
}

/* ==================== 列表 ==================== */

function ListIndex() {
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);

  const query = useQuery({ queryKey: ['problem-lists'], queryFn: fetchMyProblemLists });
  const lists = query.data ?? [];

  const mine = lists.filter((l) => l.scope === 'PERSONAL');
  const teamLists = lists.filter((l) => l.scope === 'TEAM');

  return (
    <>
      <PageHeader
        title="题单"
        description="把一组题整理成训练清单，可直接用于作业"
        actions={
          <Button variant="primary" size="sm" onClick={() => setCreating((v) => !v)}>
            <Plus className="h-3.5 w-3.5" />
            新建题单
          </Button>
        }
      />

      {creating && (
        <div className="mb-4">
          <CreateListForm
            onDone={(created) => {
              setCreating(false);
              void qc.invalidateQueries({ queryKey: ['problem-lists'] });
              void created;
            }}
            onCancel={() => setCreating(false)}
          />
        </div>
      )}

      {query.isLoading && <Muted>加载中…</Muted>}
      {query.isError && <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>}

      {!query.isLoading && lists.length === 0 && (
        <Card>
          <div className="flex flex-col items-center gap-2 py-10 text-center">
            <FileText className="h-7 w-7 text-muted-foreground" />
            <div className="text-sm font-medium">还没有题单</div>
            <div className="max-w-md text-xs text-muted-foreground">
              点右上角「新建题单」，把题号粘进去即可 —— 支持
              <code className="mx-1">CF 1932A</code>、
              <code className="mx-1">洛谷 P1001</code>、
              AtCoder 题号，以及直接粘题目链接。
            </div>
          </div>
        </Card>
      )}

      {teamLists.length > 0 && (
        <Section title="队伍题单" items={teamLists} />
      )}
      {mine.length > 0 && (
        <Section title="我的题单" items={mine} />
      )}
    </>
  );
}

function Section({ title, items }: { title: string; items: ProblemList[] }) {
  return (
    <div className="mb-5">
      <div className="mb-2 text-sm font-medium">
        {title}
        <span className="ml-2 text-xs text-muted-foreground">{items.length} 份</span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((l) => (
          <ListCard key={l.id} list={l} />
        ))}
      </div>
    </div>
  );
}

function ListCard({ list }: { list: ProblemList }) {
  return (
    <Link to={`/problemset/${list.id}`}>
      <Card className="h-full transition-colors hover:border-[var(--primary)]">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="truncate text-sm font-medium">{list.name}</div>
            <div className="mt-0.5 text-[11px] text-muted-foreground">
              {list.problemCount} 道题
              {list.teamName && ` · ${list.teamName}`}
              {list.ownerNickname && ` · ${list.ownerNickname}`}
            </div>
          </div>
          <div className="flex flex-none flex-col items-end gap-1">
            {list.scope === 'TEAM' ? (
              <Badge>
                <Users className="mr-0.5 inline h-3 w-3" />
                队伍
              </Badge>
            ) : (
              <Badge>个人</Badge>
            )}
            {list.auditStatus === 'PENDING' && <Badge tone="warning">待审核</Badge>}
            {list.auditStatus === 'REJECTED' && <Badge tone="destructive">已驳回</Badge>}
          </div>
        </div>
        {list.description && (
          <p className="mt-2 line-clamp-2 text-xs text-muted-foreground">{list.description}</p>
        )}
      </Card>
    </Link>
  );
}

/* ==================== 新建 ==================== */

function CreateListForm({
  onDone,
  onCancel,
}: {
  onDone: (l: ProblemList) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [scope, setScope] = useState<ListScope>('PERSONAL');
  const [teamId, setTeamId] = useState<number | ''>('');
  const [pasteText, setPasteText] = useState('');
  const [defaultPlatform, setDefaultPlatform] = useState('LUOGU');
  const [error, setError] = useState<string | null>(null);

  const teams = useQuery({ queryKey: ['my-teams'], queryFn: fetchMyTeams });

  const create = useMutation({
    mutationFn: () =>
      createProblemList({
        name: name.trim(),
        description: description.trim() || undefined,
        scope,
        teamId: scope === 'TEAM' ? Number(teamId) : undefined,
        pasteText: pasteText.trim() || undefined,
        defaultPlatform: pasteText.trim() ? defaultPlatform : undefined,
      }),
    onSuccess: onDone,
    onError: (e: Error) => setError(e.message),
  });

  const needTeam = scope === 'TEAM' && teamId === '';

  return (
    <Card>
      <div className="space-y-3">
        <div className="text-sm font-medium">新建题单</div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Input
            label="名称"
            name="listName"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="例如：第一周基础题"
            maxLength={128}
          />
          <Input
            label="简介（可选）"
            name="listDesc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <div className="mb-1 text-xs text-muted-foreground">归属</div>
            <div className="flex gap-2">
              <Pill active={scope === 'PERSONAL'} onClick={() => setScope('PERSONAL')}>
                个人题单
              </Pill>
              <Pill active={scope === 'TEAM'} onClick={() => setScope('TEAM')}>
                队伍题单
              </Pill>
            </div>
          </div>
          {scope === 'TEAM' && (
            <Select
              label="队伍"
              name="teamId"
              value={teamId}
              onChange={(e) => setTeamId(e.target.value === '' ? '' : Number(e.target.value))}
            >
              <option value="">选择队伍…</option>
              {(teams.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>
          )}
        </div>

        {scope === 'TEAM' && (
          <Notice>
            队伍题单：队长和站主可直接维护；普通队员提交的题单会先进「待审核」，
            由队长或站主确认后才算正式内容。
          </Notice>
        )}

        <PasteBox
          text={pasteText}
          onTextChange={setPasteText}
          defaultPlatform={defaultPlatform}
          onPlatformChange={setDefaultPlatform}
        />

        {error && <Notice tone="danger">{error}</Notice>}
        {needTeam && <Notice tone="warning">请选择队伍。</Notice>}

        <div className="flex gap-2">
          <Button
            variant="primary"
            size="sm"
            disabled={!name.trim() || needTeam || create.isPending}
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

/* ==================== 粘贴框（核心） ==================== */

/**
 * 粘贴 + 实时预览。
 *
 * 为什么要预览：粘贴一大段文本时，如果只有几行被识别，
 * 用户很难发现。这里把「识别出什么」和「哪几行没认出来」都摆出来。
 */
function PasteBox({
  text,
  onTextChange,
  defaultPlatform,
  onPlatformChange,
  compact = false,
}: {
  text: string;
  onTextChange: (v: string) => void;
  defaultPlatform: string;
  onPlatformChange: (v: string) => void;
  compact?: boolean;
}) {
  const [debounced, setDebounced] = useState(text);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(text), 400);
    return () => clearTimeout(t);
  }, [text]);

  const preview = useQuery({
    queryKey: ['parse-preview', debounced, defaultPlatform],
    queryFn: () => parsePreview(debounced, defaultPlatform),
    enabled: debounced.trim().length > 0,
  });

  const d = preview.data;

  return (
    <div className="rounded-[var(--radius)] border p-3" style={{ borderColor: 'var(--border)' }}>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 text-xs font-medium">
          <ClipboardPaste className="h-3.5 w-3.5" />
          批量粘贴题号
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[11px] text-muted-foreground">未写平台时按</span>
          <select
            className="rounded-[var(--radius)] border px-1.5 py-0.5 text-[11px] outline-none"
            style={{
              borderColor: 'var(--input)',
              backgroundColor: 'var(--card)',
              color: 'var(--foreground)',
            }}
            value={defaultPlatform}
            onChange={(e) => onPlatformChange(e.target.value)}
          >
            {OJ_PLATFORMS.map((p) => (
              <option key={p.code} value={p.code}>
                {p.label}
              </option>
            ))}
            <option value="OTHER">其他</option>
          </select>
        </div>
      </div>

      <textarea
        className="w-full resize-y rounded-[var(--radius)] border bg-transparent px-3 py-2 font-mono text-xs outline-none placeholder:text-muted-foreground"
        style={{ borderColor: 'var(--input)', minHeight: compact ? 72 : 104 }}
        placeholder={'每行一种写法都可以，混着写也行：\nCF 1932A 1932B\n洛谷 P1001，P1046\nhttps://atcoder.jp/contests/abc300/tasks/abc300_a'}
        value={text}
        onChange={(e) => onTextChange(e.target.value)}
      />

      {d && (
        <div className="mt-2 space-y-2">
          <div className="flex flex-wrap items-center gap-3 text-[11px]">
            <span className="inline-flex items-center gap-1" style={{ color: 'var(--success)' }}>
              <CheckCircle2 className="h-3 w-3" />
              识别 {d.problems.length} 题
            </span>
            {d.duplicateCount > 0 && (
              <span className="text-muted-foreground">去重 {d.duplicateCount} 条</span>
            )}
            {d.unparsed.length > 0 && (
              <span className="inline-flex items-center gap-1" style={{ color: 'var(--warning)' }}>
                <AlertTriangle className="h-3 w-3" />
                {d.unparsed.length} 行没认出来
              </span>
            )}
          </div>

          {d.problems.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {d.problems.slice(0, 40).map((p, i) => (
                <span
                  key={`${p.platform}-${p.problemNo}-${i}`}
                  className="badge text-[10px]"
                  title={p.url ?? ''}
                >
                  {PLATFORM_SHORT[p.platform] ?? p.platform} {p.problemNo}
                </span>
              ))}
              {d.problems.length > 40 && (
                <span className="text-[10px] text-muted-foreground">
                  …等 {d.problems.length} 题
                </span>
              )}
            </div>
          )}

          {d.unparsed.length > 0 && (
            <div
              className="rounded-[var(--radius)] px-2 py-1.5 text-[11px]"
              style={{
                backgroundColor: 'color-mix(in oklab, var(--warning) 12%, transparent)',
                color: 'var(--warning)',
              }}
            >
              <div>以下内容无法识别，将被跳过：</div>
              <ul className="mt-1 space-y-0.5 font-mono">
                {d.unparsed.slice(0, 5).map((line, i) => (
                  <li key={i} className="truncate">
                    {line}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

const PLATFORM_SHORT: Record<string, string> = {
  CODEFORCES: 'CF',
  ATCODER: 'AtCoder',
  NOWCODER: '牛客',
  LEETCODE: 'LC',
  LUOGU: '洛谷',
  OTHER: '其他',
};

/* ==================== 详情 ==================== */

function ListDetail({ id }: { id: number }) {
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [pasteText, setPasteText] = useState('');
  const [defaultPlatform, setDefaultPlatform] = useState('LUOGU');
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const query = useQuery({
    queryKey: ['problem-list', id],
    queryFn: () => fetchProblemListDetail(id),
  });

  function invalidate() {
    void qc.invalidateQueries({ queryKey: ['problem-list', id] });
    void qc.invalidateQueries({ queryKey: ['problem-lists'] });
  }

  const add = useMutation({
    mutationFn: () => importProblems(id, { pasteText, defaultPlatform }),
    onSuccess: () => {
      setPasteText('');
      setAdding(false);
      setError(null);
      invalidate();
    },
    onError: (e: Error) => setError(e.message),
  });

  const remove = useMutation({
    mutationFn: (problemId: number) => removeProblem(id, problemId),
    onSuccess: invalidate,
    onError: (e: Error) => setError(e.message),
  });

  const del = useMutation({
    mutationFn: () => deleteProblemList(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['problem-lists'] });
      window.location.href = '/problemset';
    },
    onError: (e: Error) => setError(e.message),
  });

  const totalScore = useMemo(
    () => (query.data?.problems ?? []).reduce((s, p) => s + (p.score ?? 0), 0),
    [query.data],
  );

  if (query.isLoading) return <Muted>加载中…</Muted>;
  if (query.isError) return <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>;

  const list = query.data!;
  const problems = list.problems ?? [];

  return (
    <>
      <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
        <Link to="/problemset" className="hover:text-foreground">
          题单
        </Link>
        <span>/</span>
        <span className="truncate">{list.name}</span>
      </div>

      <PageHeader
        title={list.name}
        description={list.description || undefined}
        actions={
          <div className="flex gap-2">
            {list.editable && (
              <>
                <Button size="sm" variant="primary" onClick={() => setAdding((v) => !v)}>
                  <Plus className="h-3.5 w-3.5" />
                  加题
                </Button>
                <Button size="sm" variant="danger" onClick={() => setConfirmDelete(true)}>
                  <Trash2 className="h-3.5 w-3.5" />
                  删除
                </Button>
              </>
            )}
          </div>
        }
      />

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <Badge>{list.scope === 'TEAM' ? '队伍题单' : '个人题单'}</Badge>
        {list.teamName && <Badge>{list.teamName}</Badge>}
        {list.auditStatus === 'PENDING' && <Badge tone="warning">待审核</Badge>}
        {list.auditStatus === 'REJECTED' && <Badge tone="destructive">已驳回</Badge>}
        <span className="text-xs text-muted-foreground">
          {list.problemCount} 道题 · 做完约 {totalScore} 分
        </span>
      </div>

      {list.auditStatus === 'PENDING' && (
        <div className="mb-3">
          <Notice tone="warning">
            这份队伍题单还在等队长或站主确认。确认后本队其他成员就能看到。
          </Notice>
        </div>
      )}

      {!list.editable && (
        <div className="mb-3">
          <Notice>队伍题单只有本队队长或站主可以修改，你可以查看和用于练习。</Notice>
        </div>
      )}

      {error && (
        <div className="mb-3">
          <Notice tone="danger">{error}</Notice>
        </div>
      )}

      {confirmDelete && (
        <div className="mb-3">
          <Notice tone="danger">
            <div>确认删除「{list.name}」？题单里的题目引用会一并移除（题目本身保留）。</div>
            <div className="mt-2 flex gap-2">
              <Button size="sm" variant="danger" disabled={del.isPending} onClick={() => del.mutate()}>
                确认删除
              </Button>
              <Button size="sm" onClick={() => setConfirmDelete(false)}>
                取消
              </Button>
            </div>
          </Notice>
        </div>
      )}

      {adding && list.editable && (
        <div className="mb-3">
          <Card>
            <PasteBox
              text={pasteText}
              onTextChange={setPasteText}
              defaultPlatform={defaultPlatform}
              onPlatformChange={setDefaultPlatform}
              compact
            />
            <div className="mt-3 flex gap-2">
              <Button
                size="sm"
                variant="primary"
                disabled={!pasteText.trim() || add.isPending}
                onClick={() => {
                  setError(null);
                  add.mutate();
                }}
              >
                {add.isPending ? '导入中…' : '导入这些题'}
              </Button>
              <Button size="sm" onClick={() => setAdding(false)}>
                取消
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card flush>
        {problems.length === 0 && (
          <div className="py-10 text-center text-sm text-muted-foreground">
            题单里还没有题目{list.editable ? '，点「加题」粘贴题号' : ''}
          </div>
        )}
        {problems.map((p, i) => (
          <div
            key={p.id}
            className={cn('flex flex-wrap items-center gap-3 px-4 py-2.5')}
            style={{ borderTop: i === 0 ? 'none' : '1px solid var(--border)' }}
          >
            <span className="w-6 flex-none text-xs tabular-nums text-muted-foreground">
              {i + 1}
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm">{p.title}</span>
                <span className="badge text-[10px]">
                  {PLATFORM_SHORT[p.platform] ?? p.platform}
                  {p.problemNo ? ` ${p.problemNo}` : ''}
                </span>
                {p.difficulty && (
                  <span className="text-[11px] text-muted-foreground">{p.difficulty}</span>
                )}
              </div>
            </div>
            <div className="flex flex-none items-center gap-2">
              {p.score != null && (
                <span className="text-[11px] text-muted-foreground">{p.score} 分</span>
              )}
              {p.url && (
                <a
                  href={p.url}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
                >
                  打开
                  <ExternalLink className="h-3 w-3" />
                </a>
              )}
              {list.editable && (
                <button
                  type="button"
                  aria-label="移除"
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(p.id)}
                  className="text-muted-foreground hover:text-[var(--destructive)]"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          </div>
        ))}
      </Card>
    </>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}
