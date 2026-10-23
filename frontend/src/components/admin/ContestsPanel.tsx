import { useState } from 'react';
import { calendarToday } from '@/lib/date';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, Trophy } from 'lucide-react';

import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button, Input, Notice, Select } from '@/components/ui/Button';
import { adminCreateContest, adminDeleteContest, adminListContests } from '@/api/contest';
import { OJ_PLATFORMS } from '@/config/constants';
import type { Contest, ContestLevel } from '@/types/assignment';

const LEVELS: { code: ContestLevel; label: string; hint: string }[] = [
  { code: 'S', label: 'S 级', hint: '最高规格（如区域赛级训练）' },
  { code: 'A', label: 'A 级', hint: '高规格' },
  { code: 'B', label: 'B 级', hint: '常规训练赛' },
  { code: 'C', label: 'C 级', hint: '入门练习赛' },
  { code: 'OTHER', label: '其他', hint: '不参与等级分档' },
];

const PLATFORM_LABEL: Record<string, string> = {
  ...Object.fromEntries(OJ_PLATFORMS.map((p) => [p.code, p.label])),
  OTHER: '其他',
};

/**
 * 比赛管理 —— 站主后台。
 *
 * ⚠️ 比赛**手动登记**，故意不提供任何「从平台拉取比赛列表」的入口：
 * 「我们队要参加哪场」是教学决策，抓来的全球比赛列表没有意义，
 * 而且抓取会把训练计划和 OJ 的赛程绑死。
 */
export function ContestsPanel() {
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);

  const list = useQuery({ queryKey: ['admin-contests'], queryFn: adminListContests });

  return (
    <div className="space-y-4">
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2 text-sm font-medium">
              <Trophy className="h-4 w-4" />
              比赛
              <span className="text-xs text-muted-foreground">
                {(list.data ?? []).length} 场
              </span>
            </div>
            <p className="mt-1 max-w-2xl text-xs text-muted-foreground">
              在这里手动登记比赛（名称、平台、等级、时间）。之后队长在比赛页决定哪支队伍参加，
              参赛即按等级给全队发分；赛后由队长填写比赛日志并确认，名次分发给实际参赛成员。
            </p>
          </div>
          <Button size="sm" variant="primary" onClick={() => setCreating((v) => !v)}>
            <Plus className="h-3.5 w-3.5" />
            登记比赛
          </Button>
        </div>
      </Card>

      {creating && (
        <CreateContestForm
          onDone={() => {
            setCreating(false);
            void qc.invalidateQueries({ queryKey: ['admin-contests'] });
            void qc.invalidateQueries({ queryKey: ['contests'] });
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      {list.isLoading && <Muted>加载中…</Muted>}
      {list.isError && <Notice tone="danger">加载失败：{(list.error as Error).message}</Notice>}
      {!list.isLoading && (list.data ?? []).length === 0 && <Muted>还没有登记过比赛</Muted>}

      <div className="space-y-3">
        {(list.data ?? []).map((c) => (
          <ContestRow key={c.id} contest={c} onChanged={() => {
            void qc.invalidateQueries({ queryKey: ['admin-contests'] });
            void qc.invalidateQueries({ queryKey: ['contests'] });
          }} />
        ))}
      </div>
    </div>
  );
}

function ContestRow({ contest: c, onChanged }: { contest: Contest; onChanged: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const del = useMutation({
    mutationFn: () => adminDeleteContest(c.id),
    onSuccess: onChanged,
    onError: (e: Error) => setError(e.message),
  });

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium">{c.name}</span>
            <Badge>{PLATFORM_LABEL[c.platform] ?? c.platform}</Badge>
            <Badge tone={c.level === 'S' ? 'destructive' : c.level === 'A' ? 'primary' : 'default'}>
              {c.level === 'OTHER' ? '其他' : `${c.level} 级`}
            </Badge>
            {c.myTeamSelected && <Badge tone="success">本队参赛</Badge>}
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
            <span>
              {fmt(c.startAt)} → {fmt(c.endAt)}
            </span>
            <span>{c.teamCount} 支队伍参赛</span>
          </div>
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

      {confirming && (
        <div className="mt-3">
          <Notice tone="danger">
            <div>确认删除「{c.name}」？</div>
            <div className="mt-1">已有队伍参赛时系统会拒绝删除，避免参赛积分指向不存在的比赛。</div>
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

function CreateContestForm({
  onDone,
  onCancel,
}: {
  onDone: () => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [platform, setPlatform] = useState('CODEFORCES');
  const [level, setLevel] = useState<ContestLevel>('B');
  const [url, setUrl] = useState('');
  const [startAt, setStartAt] = useState(defaultLocal(0, 12));
  const [endAt, setEndAt] = useState(defaultLocal(0, 15));
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      adminCreateContest({
        name: name.trim(),
        platform,
        level,
        url: url.trim() || undefined,
        startAt: toBackend(startAt),
        endAt: toBackend(endAt),
      }),
    onSuccess: onDone,
    onError: (e: Error) => setError(e.message),
  });

  const levelHint = LEVELS.find((l) => l.code === level)?.hint;

  return (
    <Card>
      <div className="space-y-3">
        <div className="text-sm font-medium">登记比赛</div>

        <Input
          label="比赛名称"
          name="contestName"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="例如：Codeforces Round 1000（Div. 2）"
          maxLength={200}
        />

        <div className="grid gap-3 sm:grid-cols-2">
          <Select
            label="平台"
            name="contestPlatform"
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
          <div>
            <Select
              label="等级（决定参赛积分档位）"
              name="contestLevel"
              value={level}
              onChange={(e) => setLevel(e.target.value as ContestLevel)}
            >
              {LEVELS.map((l) => (
                <option key={l.code} value={l.code}>
                  {l.label}
                </option>
              ))}
            </Select>
            {levelHint && <div className="mt-1 text-[11px] text-muted-foreground">{levelHint}</div>}
          </div>
        </div>

        <Input
          label="比赛链接"
          name="contestUrl"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://..."
          maxLength={500}
        />

        <div className="grid gap-3 sm:grid-cols-2">
          <Input
            label="开始时间"
            name="contestStart"
            type="datetime-local"
            value={startAt}
            onChange={(e) => setStartAt(e.target.value)}
          />
          <Input
            label="结束时间"
            name="contestEnd"
            type="datetime-local"
            value={endAt}
            onChange={(e) => setEndAt(e.target.value)}
          />
        </div>

        {error && <Notice tone="danger">{error}</Notice>}

        <div className="flex gap-2">
          <Button
            size="sm"
            variant="primary"
            disabled={!name.trim() || create.isPending}
            onClick={() => {
              setError(null);
              create.mutate();
            }}
          >
            {create.isPending ? '登记中…' : '登记'}
          </Button>
          <Button size="sm" onClick={onCancel}>
            取消
          </Button>
        </div>
      </div>
    </Card>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-3 text-center text-xs text-muted-foreground">{children}</div>;
}

function pad(n: number) {
  return String(n).padStart(2, '0');
}

function defaultLocal(offsetDays: number, hour: number) {
  const d = calendarToday();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(hour)}:00`;
}

/** datetime-local → 后端约定的 "yyyy-MM-dd HH:mm:ss" */
function toBackend(local: string) {
  if (!local) return local;
  const normalized = local.replace('T', ' ');
  return normalized.length === 16 ? `${normalized}:00` : normalized;
}

function fmt(s: string | null) {
  if (!s) return '—';
  return s.slice(0, 16);
}
