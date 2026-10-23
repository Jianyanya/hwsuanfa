import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Award, Plus, RotateCcw, Trash2 } from 'lucide-react';

import { Card } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Notice } from '@/components/ui/Button';
import { fetchAllDifficultyTiers, saveDifficultyTiers } from '@/api/assignment';
import type { DifficultyTier } from '@/types/assignment';
import { OJ_PLATFORMS } from '@/config/constants';

/** 平台显示名（复用 OJ_PLATFORMS 的顺序与命名） */
const PLATFORM_LABEL: Record<string, string> = Object.fromEntries(
  OJ_PLATFORMS.map((p) => [p.code, p.label]),
);

const PLATFORM_ORDER = [...OJ_PLATFORMS.map((p) => p.code), 'OTHER'];

/** 可编辑的档位行（多了个临时 key 用于 React 列表） */
interface EditableTier extends DifficultyTier {
  _key: string;
}

let seq = 0;
const nextKey = () => `tier-${++seq}-${Date.now()}`;

/**
 * 难度 → 分值配置。
 *
 * 为什么放在后台可改：分值属于**教学策略**（高一的分值该给 4 分还是 5 分），
 * 不同届、不同训练目标可能不一样，不该写死在代码里改一次就要重新部署。
 *
 * ⚠️ 改分值**只影响之后新产生的积分事件**，不追溯已发放的历史事件。
 *    要修正历史得用积分修正（反向事件），这是刻意的设计。
 */
export function DifficultyTiersPanel() {
  const qc = useQueryClient();
  const [platform, setPlatform] = useState('LUOGU');
  const [draft, setDraft] = useState<EditableTier[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const query = useQuery({
    queryKey: ['difficulty-tiers-all'],
    queryFn: fetchAllDifficultyTiers,
  });

  /**
   * 把某个平台的档位灌进草稿。
   *
   * ⚠️ 只在**切换平台**时灌，不要在 query.data 变化时都灌 ——
   * 保存后会 invalidate 触发重新拉取，如果那时也重置草稿，
   * 刚显示的「已保存」提示会被立刻冲掉（实测踩过）。
   */
  const loadedFor = useRef<string>('');

  useEffect(() => {
    if (!query.data) return;
    if (loadedFor.current === platform) return;
    loadedFor.current = platform;
    setDraft((query.data[platform] ?? []).map((t) => ({ ...t, _key: nextKey() })));
    setError(null);
    setSaved(false);
  }, [query.data, platform]);

  const save = useMutation({
    mutationFn: () =>
      saveDifficultyTiers(
        platform,
        draft.map((t, i) => ({
          platform,
          difficultyKey: t.difficultyKey.trim(),
          label: (t.label ?? '').trim() || t.difficultyKey.trim(),
          score: Number(t.score) || 0,
          sortOrder: i,
          isDefault: !!t.isDefault,
        })),
      ),
    onSuccess: () => {
      setError(null);
      setSaved(true);
      void qc.invalidateQueries({ queryKey: ['difficulty-tiers-all'] });
    },
    onError: (e: Error) => {
      setSaved(false);
      setError(e.message);
    },
  });

  const defaultCount = draft.filter((t) => t.isDefault).length;
  const invalid =
    draft.some((t) => !t.difficultyKey.trim()) ||
    new Set(draft.map((t) => t.difficultyKey.trim())).size !== draft.length;

  function patch(key: string, changes: Partial<EditableTier>) {
    setSaved(false);
    setDraft((v) => v.map((t) => (t._key === key ? { ...t, ...changes } : t)));
  }

  return (
    <div className="space-y-4">
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2 text-sm font-medium">
              <Award className="h-4 w-4" />
              难度 → 首次完成分值
            </div>
            <p className="mt-1 max-w-2xl text-xs text-muted-foreground">
              不同难度的题给不同分。每个平台一套档位 —— 洛谷按档位名、CF 按 rating 分段，
              口径本来就不一样，所以不能共用一个通用下拉。
            </p>
          </div>
        </div>

        {/* 平台切换 */}
        <div className="mt-3 flex flex-wrap gap-2">
          {PLATFORM_ORDER.map((code) => (
            <Pill key={code} active={platform === code} onClick={() => setPlatform(code)}>
              {PLATFORM_LABEL[code] ?? code}
              {(query.data?.[code]?.length ?? 0) > 0 && (
                <span className="ml-1.5 text-[11px] opacity-70">
                  {query.data![code].length}
                </span>
              )}
            </Pill>
          ))}
        </div>
      </Card>

      <Card>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div className="text-sm font-medium">
            {PLATFORM_LABEL[platform] ?? platform} 的档位
            <span className="ml-2 text-xs text-muted-foreground">
              由易到难，共 {draft.length} 档
            </span>
          </div>
          <div className="flex gap-2">
            <Button
              size="sm"
              onClick={() =>
                setDraft((v) => [
                  ...v,
                  {
                    _key: nextKey(),
                    platform,
                    difficultyKey: '',
                    label: '',
                    score: 2,
                    isDefault: false,
                  },
                ])
              }
            >
              <Plus className="h-3.5 w-3.5" />
              添加档位
            </Button>
            <Button
              size="sm"
              onClick={() => {
                // 强制用服务端数据覆盖草稿：清掉标记让 effect 重新灌一次
                loadedFor.current = '';
                setDraft((query.data?.[platform] ?? []).map((t) => ({ ...t, _key: nextKey() })));
                setError(null);
                setSaved(false);
              }}
            >
              <RotateCcw className="h-3.5 w-3.5" />
              还原
            </Button>
            <Button
              size="sm"
              variant="primary"
              disabled={invalid || save.isPending}
              onClick={() => {
                setError(null);
                save.mutate();
              }}
            >
              {save.isPending ? '保存中…' : '保存'}
            </Button>
          </div>
        </div>

        {query.isLoading && <Muted>加载中…</Muted>}
        {query.isError && (
          <Notice tone="danger">加载失败：{(query.error as Error).message}</Notice>
        )}

        {!query.isLoading && (
          <div className="space-y-2">
            {/* 表头 */}
            <div className="hidden gap-2 px-1 text-[11px] text-muted-foreground sm:flex">
              <span className="w-40">档位标识（存在题目上）</span>
              <span className="flex-1">显示名</span>
              <span className="w-20">分值</span>
              <span className="w-20">兜底档</span>
              <span className="w-8" />
            </div>

            {draft.map((t) => (
              <div
                key={t._key}
                className="flex flex-wrap items-center gap-2 rounded-[var(--radius)] border px-2 py-2"
                style={{ borderColor: 'var(--border)' }}
              >
                <div className="w-40">
                  <input
                    className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1 text-xs outline-none"
                    style={{ borderColor: 'var(--input)' }}
                    value={t.difficultyKey}
                    placeholder="如 普及/提高− 或 1300-1699"
                    onChange={(e) => patch(t._key, { difficultyKey: e.target.value })}
                  />
                </div>
                <div className="min-w-[8rem] flex-1">
                  <input
                    className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1 text-xs outline-none"
                    style={{ borderColor: 'var(--input)' }}
                    value={t.label ?? ''}
                    placeholder="界面显示名"
                    onChange={(e) => patch(t._key, { label: e.target.value })}
                  />
                </div>
                <div className="w-20">
                  <input
                    type="number"
                    step="0.5"
                    min="0"
                    className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1 text-xs tabular-nums outline-none"
                    style={{ borderColor: 'var(--input)' }}
                    value={t.score}
                    onChange={(e) => patch(t._key, { score: Number(e.target.value) })}
                  />
                </div>
                <div className="flex w-20 justify-center">
                  <label className="flex items-center gap-1 text-[11px] text-muted-foreground">
                    <input
                      type="radio"
                      name={`default-${platform}`}
                      checked={!!t.isDefault}
                      onChange={() =>
                        setDraft((v) =>
                          v.map((x) => ({ ...x, isDefault: x._key === t._key })),
                        )
                      }
                    />
                    兜底
                  </label>
                </div>
                <button
                  type="button"
                  aria-label="删除该档位"
                  onClick={() => setDraft((v) => v.filter((x) => x._key !== t._key))}
                  className="text-muted-foreground hover:text-[var(--destructive)]"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}

            {draft.length === 0 && <Muted>该平台还没有配置档位，点「添加档位」开始。</Muted>}
          </div>
        )}

        {error && (
          <div className="mt-3">
            <Notice tone="danger">{error}</Notice>
          </div>
        )}
        {saved && (
          <div className="mt-3">
            <Notice>已保存。新产生的积分会按新分值计算；已发放的历史积分不受影响。</Notice>
          </div>
        )}

        <div className="mt-3 space-y-1.5 text-[11px] text-muted-foreground">
          <div>
            取值优先级：<strong className="text-foreground">精确档位</strong> →
            <strong className="text-foreground"> 本平台兜底档</strong> →
            积分规则里 FIRST_AC 的配置 → 代码兜底（2 分）。
          </div>
          {defaultCount === 0 && draft.length > 0 && (
            <div style={{ color: 'var(--warning)' }}>
              ⚠️ 还没指定兜底档：题目难度不在这张表里时会一路回落到积分规则，分值与预期可能不符。
            </div>
          )}
          {defaultCount > 1 && (
            <div style={{ color: 'var(--destructive)' }}>只能有一个兜底档，请只选一个。</div>
          )}
          {invalid && <div style={{ color: 'var(--destructive)' }}>存在空的或重复的档位标识。</div>}
        </div>
      </Card>

      <Card>
        <div className="mb-2 text-sm font-medium">当前生效的分值（所有平台）</div>
        <div className="space-y-2">
          {PLATFORM_ORDER.filter((c) => (query.data?.[c]?.length ?? 0) > 0).map((code) => (
            <div key={code} className="flex flex-wrap items-center gap-2">
              <span className="w-24 flex-none text-xs text-muted-foreground">
                {PLATFORM_LABEL[code] ?? code}
              </span>
              <div className="flex flex-wrap gap-1">
                {(query.data![code] ?? []).map((t) => (
                  <span
                    key={t.difficultyKey}
                    className="badge text-[10px]"
                    style={
                      t.isDefault
                        ? {
                            backgroundColor: 'color-mix(in oklab, var(--primary) 20%, transparent)',
                            color: 'var(--primary)',
                          }
                        : undefined
                    }
                    title={t.isDefault ? '兜底档' : undefined}
                  >
                    {t.label || t.difficultyKey} = {t.score}
                    {t.isDefault ? ' ★' : ''}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
        <div className="mt-2">
          <Badge>★ = 兜底档</Badge>
        </div>
      </Card>
    </div>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-4 text-center text-xs text-muted-foreground">{children}</div>;
}
