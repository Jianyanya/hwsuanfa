import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Code2, Trophy, UserRound } from 'lucide-react';

import { fetchPublicUser } from '@/api/publicUsers';
import { Badge } from '@/components/ui/Badge';
import { Card, PageHeader } from '@/components/ui/Card';
import { Notice } from '@/components/ui/Button';

export default function PublicUserPage() {
  const { userId } = useParams();
  const query = useQuery({ queryKey: ['public-user', userId], queryFn: () => fetchPublicUser(userId!), enabled: !!userId });
  if (query.isLoading) return <div className="py-10 text-center text-sm text-muted-foreground">正在加载用户资料…</div>;
  if (query.isError) return <Notice tone="danger">{(query.error as Error).message}</Notice>;
  const data = query.data;
  if (!data) return null;
  return <div className="space-y-5">
    <PageHeader title={data.user.nickname} description="公开资料与训练记录" />
    <Card><div className="flex items-center gap-4"><div className="flex h-14 w-14 items-center justify-center overflow-hidden rounded-full bg-muted">{data.user.avatar ? <img src={data.user.avatar} alt="" className="h-full w-full object-cover" /> : <UserRound className="h-7 w-7 text-muted-foreground" />}</div><div><h2 className="text-lg font-semibold">{data.user.nickname}</h2><div className="mt-1 flex flex-wrap gap-2 text-xs text-muted-foreground">{data.user.className && <span>{data.user.className}</span>}<Badge>{data.user.privacyLevel === 'PUBLIC' ? '公开资料' : '部分公开'}</Badge></div></div></div></Card>
    <div className="grid gap-4 md:grid-cols-3"><Stat label="通过题数" value={String(data.stats.solvedCount)} /><Stat label="AC 提交" value={String(data.stats.submissionCount)} /><Stat label="积分" value={String(data.stats.totalScore)} /></div>
    {data.awards.length > 0 && <Card><h2 className="mb-3 flex items-center gap-2 font-semibold"><Trophy className="h-4 w-4 text-primary" />获奖记录</h2><div className="divide-y divide-[var(--border)]">{data.awards.map((award) => <div key={award.id} className="py-3 first:pt-0 last:pb-0"><div className="font-medium">{award.awardName}</div><div className="mt-1 text-xs text-muted-foreground">{award.contestName ?? '—'} · {award.awardLevel ?? '—'} · {award.awardedAt ?? '日期未填写'}</div>{award.certUrl && <a className="mt-1 inline-block text-xs text-primary hover:underline" href={award.certUrl} target="_blank" rel="noreferrer">查看证书</a>}</div>)}</div></Card>}
    {data.submissions.length > 0 && <Card><h2 className="mb-3 flex items-center gap-2 font-semibold"><Code2 className="h-4 w-4 text-primary" />公开代码</h2><div className="space-y-3">{data.submissions.map((submission) => <div key={submission.id} className="rounded-[var(--radius)] border border-[var(--border)] p-3"><div className="flex flex-wrap items-center justify-between gap-2"><span className="font-medium">{submission.platform} · {submission.problemNo}</span>{submission.url && <a className="text-xs text-primary hover:underline" href={submission.url} target="_blank" rel="noreferrer">查看提交</a>}</div>{submission.title && <p className="mt-1 text-xs text-muted-foreground">{submission.title}</p>}{submission.code && <pre className="mt-2 max-h-72 overflow-auto rounded bg-muted p-3 text-xs leading-5"><code>{submission.code}</code></pre>}</div>)}</div></Card>}
    <p className="text-xs text-muted-foreground"><Link to="/" className="text-primary hover:underline">返回首页</Link></p>
  </div>;
}

function Stat({ label, value }: { label: string; value: string }) { return <Card><div className="text-xs text-muted-foreground">{label}</div><div className="mt-1 text-2xl font-semibold">{value}</div></Card>; }
