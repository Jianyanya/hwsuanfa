import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Award } from 'lucide-react';

import { Card } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Notice } from '@/components/ui/Button';
import { adminListAwards, auditAward } from '@/api/awards';
import type { AwardRow } from '@/types/assignment';

/**
 * 获奖审核 —— 站主面板。
 * 队员提交的获奖记录先进 PENDING，这里通过后才会公示到其个人页。
 */
export function AwardsAuditPanel() {
  const qc = useQueryClient();
  const [status, setStatus] = useState('PENDING');
  const [rejecting, setRejecting] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ['admin-awards', status],
    queryFn: () => adminListAwards(status),
  });

  const audit = useMutation({
    mutationFn: ({ id, approved, rejectReason }: { id: number; approved: boolean; rejectReason?: string }) =>
      auditAward(id, approved, rejectReason),
    onSuccess: () => {
      setError(null);
      void qc.invalidateQueries({ queryKey: ['admin-awards'] });
      void qc.invalidateQueries({ queryKey: ['my-awards'] });
    },
    onError: (e: Error) => setError(e.message),
  });

  const list = query.data ?? [];

  return (
    <div className="space-y-4">
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Award className="h-4 w-4" />
            获奖审核
          </div>
          <div className="flex flex-wrap gap-2">
            {['PENDING', 'APPROVED', 'REJECTED', 'ALL'].map((s) => (
              <Pill key={s} active={status === s} onClick={() => setStatus(s)}>
                {s === 'PENDING' ? '待审核' : s === 'APPROVED' ? '已通过' : s === 'REJECTED' ? '已驳回' : '全部'}
              </Pill>
            ))}
          </div>
        </div>
      </Card>

      {error && <Notice tone="danger">{error}</Notice>}
      {query.isLoading && <Muted>加载中…</Muted>}
      {!query.isLoading && list.length === 0 && (
        <Card>
          <div className="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
            <Award className="h-4 w-4" />
            该状态下没有获奖记录
          </div>
        </Card>
      )}

      <div className="space-y-3">
        {list.map((a) => (
          <AwardRowCard
            key={a.id}
            award={a}
            busy={audit.isPending}
            onAudit={(approved, rejectReason) => {
              setError(null);
              audit.mutate({ id: a.id, approved, rejectReason });
            }}
            rejecting={rejecting === a.id}
            onToggleReject={() => {
              setReason('');
              setRejecting(rejecting === a.id ? null : a.id);
            }}
            reason={reason}
            onReasonChange={setReason}
          />
        ))}
      </div>
    </div>
  );
}

function AwardRowCard({
  award, busy, onAudit, rejecting, onToggleReject, reason, onReasonChange,
}: {
  award: AwardRow;
  busy: boolean;
  onAudit: (approved: boolean, rejectReason?: string) => void;
  rejecting: boolean;
  onToggleReject: () => void;
  reason: string;
  onReasonChange: (v: string) => void;
}) {
  const statusBadge =
    award.auditStatus === 'APPROVED' ? (
      <Badge tone="success">已通过</Badge>
    ) : award.auditStatus === 'REJECTED' ? (
      <Badge tone="destructive">已驳回</Badge>
    ) : (
      <Badge tone="warning">待审核</Badge>
    );

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium">{award.awardName}</span>
            {award.awardLevel && <Badge>{award.awardLevel}</Badge>}
            {award.isPublic && <Badge tone="primary">公开</Badge>}
            {statusBadge}
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
            <span>获奖人：{award.userNickname ?? `#${award.userId}`}</span>
            {award.contestName && <span>比赛：{award.contestName}</span>}
            {award.awardedAt && <span>获奖时间：{award.awardedAt}</span>}
          </div>
          {award.note && <p className="mt-1.5 text-xs text-muted-foreground">{award.note}</p>}
          {award.certUrl && (
            <a
              href={award.certUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-1 inline-block text-[11px] hover:underline"
            >
              证书链接
            </a>
          )}
          {award.auditStatus === 'REJECTED' && award.rejectReason && (
            <div className="mt-1.5 text-[11px] text-muted-foreground">
              驳回原因：{award.rejectReason}
            </div>
          )}
        </div>

        {award.auditStatus === 'PENDING' && (
          <div className="flex flex-none gap-2">
            <Button
              size="sm"
              variant="primary"
              disabled={busy}
              onClick={() => onAudit(true)}
            >
              通过
            </Button>
            <Button size="sm" variant="danger" onClick={onToggleReject}>
              驳回
            </Button>
          </div>
        )}
      </div>

      {rejecting && (
        <div className="mt-3">
          <Notice tone="danger">
            <div className="mb-1.5">驳回「{award.awardName}」，请填写原因（会展示给队员）：</div>
            <input
              className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
              style={{ borderColor: 'var(--input)', color: 'var(--foreground)' }}
              value={reason}
              onChange={(e) => onReasonChange(e.target.value)}
              maxLength={255}
              placeholder="例如：证书信息与报名不符"
            />
            <div className="mt-2 flex gap-2">
              <Button
                size="sm"
                variant="danger"
                disabled={!reason.trim() || busy}
                onClick={() => onAudit(false, reason.trim())}
              >
                确认驳回
              </Button>
              <Button size="sm" onClick={onToggleReject}>取消</Button>
            </div>
          </Notice>
        </div>
      )}
    </Card>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}
