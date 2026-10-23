import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';

import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { fetchPaste } from '@/api/tools';

/**
 * 粘贴查看页 —— **公开路由**（分享出去的人未登录也要能看）。
 * 展示内容 / 语言 / 浏览数 / 过期时间，不做任何编辑。
 */
export default function PasteViewPage() {
  const { code } = useParams();
  const query = useQuery({
    queryKey: ['paste', code],
    queryFn: () => fetchPaste(code!),
    retry: false,
  });

  if (query.isLoading) {
    return (
      <Shell>
        <div className='py-8 text-center text-sm text-muted-foreground'>加载中…</div>
      </Shell>
    );
  }

  if (query.isError) {
    return (
      <Shell>
        <Card>
          <div className="py-10 text-center">
            <div className="text-3xl">🔗</div>
            <div className="mt-2 text-sm font-medium">{(query.error as Error).message}</div>
            <div className="mt-1 text-xs text-muted-foreground">
              链接可能已过期，或内容已被创建者删除。
            </div>
            <Link to="/" className="mt-4 inline-block">
              <Button size="sm">回首页</Button>
            </Link>
          </div>
        </Card>
      </Shell>
    );
  }

  const paste = query.data!;
  const isCode = paste.contentType === 'CODE';

  return (
    <Shell>
      <Card>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div>
            <div className="text-sm font-medium">{paste.title ?? '未命名粘贴'}</div>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
              {isCode && paste.language && (
                <span className="badge text-[10px]">{paste.language}</span>
              )}
              <span>浏览 {paste.viewCount ?? 0} 次</span>
              <span>创建于 {paste.createdAt?.slice(0, 16) ?? ''}</span>
              {paste.expiresAt && (
                <span style={{ color: 'var(--warning)' }}>
                  至 {paste.expiresAt.slice(0, 16)} 过期
                </span>
              )}
            </div>
          </div>
          <Button
            size="sm"
            onClick={() => navigator.clipboard.writeText(paste.content)}
          >
            复制内容
          </Button>
        </div>

        <pre
          className="overflow-x-auto rounded-[var(--radius)] px-4 py-3 font-mono text-[13px] leading-6"
          style={{
            backgroundColor: 'color-mix(in oklab, var(--muted) 40%, transparent)',
            color: 'var(--foreground)',
          }}
        >
          {paste.content}
        </pre>

        <div className="mt-2 text-center text-[11px] text-muted-foreground">
          <Link to="/tools" className="hover:text-foreground">
            由校队训练平台粘贴板生成
          </Link>
        </div>
      </Card>
    </Shell>
  );
}

/** 公开页的极简外壳（不套登录侧栏） */
function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="flex min-h-screen items-start justify-center px-4 pt-16"
      style={{ backgroundColor: 'var(--background)' }}
    >
      <div className="w-full max-w-2xl">
        <div className="mb-4 text-center">
          <Link to="/" className="text-sm font-semibold hover:underline">
            校队训练平台
          </Link>
        </div>
        {children}
      </div>
    </div>
  );
}

// 保留 Badge 引用（过期标记可用）
void Badge;
