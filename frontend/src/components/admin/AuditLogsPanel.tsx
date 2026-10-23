import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';

import { fetchAdminLogs } from '@/api/admin';
import { Card } from '@/components/ui/Card';
import { Button, Input, Notice } from '@/components/ui/Button';
import { formatAuditDetail } from './audit-log-format';

const pageSize = 100;

export function AuditLogsPanel() {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const query = useInfiniteQuery({
    queryKey: ['admin-logs', keyword],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => fetchAdminLogs({ limit: pageSize, beforeId: pageParam ?? undefined, keyword: keyword || undefined }),
    getNextPageParam: (lastPage) => lastPage.length === pageSize ? Math.min(...lastPage.map((item) => item.id)) : undefined,
    staleTime: 0,
  });
  const logs = query.data?.pages.flat() ?? [];

  return <section className="space-y-4" aria-label="操作日志">
    <Card><form className="flex flex-wrap items-end gap-3" onSubmit={(event) => { event.preventDefault(); setKeyword(input.trim()); }}>
      <div className="min-w-0 flex-1"><Input id="audit-keyword" label="搜索操作日志" placeholder="操作人、操作、对象或详情" value={input} onChange={(event) => setInput(event.target.value)} maxLength={100} /></div>
      <Button type="submit" size="sm">搜索日志</Button>
      <Button size="sm" disabled={query.isFetching} onClick={() => void query.refetch()}>{query.isFetching && !query.isFetchingNextPage ? '刷新中…' : '刷新日志'}</Button>
    </form><p className="mt-3 text-xs text-muted-foreground">按最新操作在前展示，日志只读，密码和令牌字段已隐藏。</p></Card>
    {query.isLoading && <Card><div className="py-8 text-center text-sm text-muted-foreground">正在加载操作日志…</div></Card>}
    {query.isError && <Notice tone="danger">日志加载失败：{query.error.message}</Notice>}
    {!query.isLoading && !query.isError && logs.length === 0 && <Card><div className="py-8 text-center text-sm text-muted-foreground">{keyword ? '没有符合条件的操作日志' : '暂无操作日志'}</div></Card>}
    {logs.length > 0 && <Card flush><div className="overflow-x-auto"><table className="w-full text-left text-xs"><thead><tr className="border-b border-[var(--border)] text-muted-foreground">{['时间', '操作人', '操作', '对象', '详情'].map((label) => <th key={label} className="px-4 py-3 font-medium">{label}</th>)}</tr></thead>
      <tbody>{logs.map((log) => {
        const detail = formatAuditDetail(log.detail);
        return <tr key={log.id} className="border-b border-[var(--border)] last:border-b-0"><td className="whitespace-nowrap px-4 py-3 tabular-nums"><time>{log.createdAt.replace('T', ' ').slice(0, 19)}</time></td><td className="px-4 py-3">{log.operatorName || '系统'}</td><td className="px-4 py-3">{log.action}</td><td className="whitespace-nowrap px-4 py-3">{log.targetType || '—'}{log.targetId != null ? ` #${log.targetId}` : ''}</td>
          <td className="min-w-48 max-w-md px-4 py-3">{detail === '—' ? '—' : <details><summary className="cursor-pointer text-muted-foreground hover:text-primary">查看详情</summary><pre className="mt-2 max-h-60 overflow-auto whitespace-pre-wrap break-all rounded-[var(--radius)] bg-muted p-3 font-mono text-[11px] leading-5">{detail}</pre></details>}</td>
        </tr>;
      })}</tbody></table></div><div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--border)] p-4"><span className="text-xs text-muted-foreground">已加载 {logs.length} 条日志</span>{query.hasNextPage ? <Button size="sm" disabled={query.isFetching} onClick={() => void query.fetchNextPage()}>{query.isFetchingNextPage ? '加载中…' : '加载更早日志'}</Button> : <span className="text-xs text-muted-foreground">已加载全部符合条件的日志</span>}</div></Card>}
  </section>;
}

