import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Clock } from 'lucide-react';

import { Badge } from '@/components/ui/Badge';
import { ASSIGNMENT_STATUS } from '@/config/constants';
import type { DisplayStatus } from '@/types/assignment';

type BadgeTone = 'default' | 'success' | 'warning' | 'destructive' | 'primary';

/** constants 里 muted 是中性态，Badge 对应的档位叫 default */
function toTone(tone: string): BadgeTone {
  return tone === 'muted' ? 'default' : (tone as BadgeTone);
}

/**
 * 作业状态徽章。
 *
 * ⚠️ status 必须是**后端返回**的值。不要在前端用 Date.now() 重算逾期 ——
 *    客户端时钟可以被修改，判定会失准。
 */
export function StatusBadge({ status }: { status: DisplayStatus }) {
  const meta = ASSIGNMENT_STATUS[status] ?? ASSIGNMENT_STATUS.IN_PROGRESS;
  return <Badge tone={toTone(meta.tone)}>{meta.label}</Badge>;
}

/**
 * 倒计时。
 *
 * 用后端给的 secondsUntilDue 作为**起点**，之后每秒本地递减。
 * 这样即使客户端时钟不准，展示也不会跳变；到期后自动置 0 并回调。
 */
export function Countdown({
  seconds,
  onExpire,
  prefix = '剩余',
  expiredText = '已截止',
}: {
  seconds: number | null;
  onExpire?: () => void;
  prefix?: string;
  expiredText?: string;
}) {
  const [left, setLeft] = useState(seconds ?? 0);

  // 只有 seconds 变化时才重建计时器；到 0 自然停下（setLeft 里有下限）
  useEffect(() => {
    setLeft(seconds ?? 0);
    if (seconds === null || seconds <= 0) return;
    const t = setInterval(() => setLeft((v) => Math.max(0, v - 1)), 1000);
    return () => clearInterval(t);
  }, [seconds]);

  // 归零时通知外部刷新（例如重新拉一次作业，让后端重算逾期状态）
  useEffect(() => {
    if (left === 0 && seconds !== null && seconds > 0) {
      onExpire?.();
    }
  }, [left, seconds, onExpire]);

  if (seconds === null) return null;

  if (left <= 0) {
    return (
      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
        <Clock className="h-3 w-3" />
        {expiredText}
      </span>
    );
  }

  const d = Math.floor(left / 86400);
  const h = Math.floor((left % 86400) / 3600);
  const m = Math.floor((left % 3600) / 60);
  const s = left % 60;

  const urgent = left < 86400; // 不到一天
  const text = d > 0 ? `${d} 天 ${h} 小时` : h > 0 ? `${h} 小时 ${m} 分` : `${m} 分 ${s} 秒`;

  return (
    <span
      className="inline-flex items-center gap-1 text-xs tabular-nums"
      style={{ color: urgent ? 'var(--warning)' : 'var(--muted-foreground)' }}
    >
      <Clock className="h-3 w-3" />
      {prefix} {text}
    </span>
  );
}

/** 进度条：已完成 / 总数 */
export function ProgressBar({ done, total }: { done: number; total: number }) {
  const pct = total <= 0 ? 0 : Math.round((done / total) * 100);
  return (
    <div className="flex items-center gap-2">
      <div className="prog flex-1">
        <i style={{ width: `${pct}%`, backgroundColor: pct === 100 ? 'var(--success)' : 'var(--primary)' }} />
      </div>
      <span className="flex-none text-xs tabular-nums text-muted-foreground">
        {done}/{total}
      </span>
    </div>
  );
}

/** 逾期未完成的醒目提示条 */
export function OverdueNotice({ text }: { text: string }) {
  return (
    <div
      className="flex items-start gap-2 rounded-[var(--radius)] border px-3 py-2 text-xs"
      style={{
        borderColor: 'color-mix(in oklab, var(--destructive) 45%, transparent)',
        backgroundColor: 'color-mix(in oklab, var(--destructive) 12%, transparent)',
        color: 'var(--destructive)',
      }}
      role="alert"
    >
      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-none" />
      <span>{text}</span>
    </div>
  );
}

/** 完成标记 */
export function DoneMark({ done }: { done: boolean | null }) {
  if (!done) return null;
  return <CheckCircle2 className="h-4 w-4 flex-none" style={{ color: 'var(--success)' }} />;
}
