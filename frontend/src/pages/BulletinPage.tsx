import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pin, Trash2 } from 'lucide-react';

import { Card, PageHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button, Notice } from '@/components/ui/Button';
import {
  adminListBulletins, createBulletin, deleteBulletin,
  fetchBulletinDetail, fetchBulletins, updateBulletin,
} from '@/api/community';
import type { BulletinRow } from '@/api/community';
import { useAuth } from '@/stores/auth';

/**
 * 公告页：所有人可看已发布公告；站主可发布/编辑/置顶/删除。
 * /bulletin 列表，/bulletin/:id 详情（详情带 ?edit=1 直接进编辑）。
 */
export default function BulletinPage() {
  const { bulletinId } = useParams();
  const me = useAuth((s) => s.user);
  const [params] = useSearchParams();
  const isOwner = me?.siteOwner;

  if (bulletinId && isOwner && (bulletinId === 'new' || params.get('edit') === '1')) {
    return <BulletinEdit key={bulletinId} bulletinId={bulletinId} />;
  }
  if (bulletinId) {
    return <BulletinDetail bulletinId={bulletinId} />;
  }

  return <BulletinList isOwner={!!isOwner} />;
}

function BulletinList({ isOwner }: { isOwner: boolean }) {
  const query = useQuery({ queryKey: ['bulletins'], queryFn: fetchBulletins });

  return (
    <>
      <PageHeader
        title="公告"
        description="训练安排与重要通知"
        actions={isOwner ? <Link to="/bulletin/new"><Button size="sm" variant="primary">发布公告</Button></Link> : undefined}
      />
      {query.isLoading && <Muted>加载中…</Muted>}
      {query.isError && <Notice tone="danger">加载失败</Notice>}
      {query.data && query.data.length === 0 && (
        <Card>
          <div className="py-8 text-center text-sm text-muted-foreground">还没有公告</div>
        </Card>
      )}
      <div className="space-y-3">
        {query.data?.map((b) => (
          <Link to={`/bulletin/${b.id}`} key={b.id} className="block">
            <Card>
              <div className="flex flex-wrap items-center gap-2">
                {b.pinned && (
                  <span className="inline-flex items-center gap-1 text-[11px]" style={{ color: 'var(--warning)' }}>
                    <Pin className="h-3 w-3" />
                    置顶
                  </span>
                )}
                <span className="text-sm font-medium hover:underline">{b.title}</span>
              </div>
              <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{b.content}</p>
              <div className="mt-1.5 text-[11px] text-muted-foreground">{b.createdAt.slice(0, 16)}</div>
            </Card>
          </Link>
        ))}
      </div>
    </>
  );
}

function BulletinDetail({ bulletinId }: { bulletinId: string }) {
  const navigate = useNavigate();
  const isOwner = useAuth((s) => s.user?.siteOwner);
  const query = useQuery({
    queryKey: ['bulletin', bulletinId],
    queryFn: () => fetchBulletinDetail(bulletinId),
  });

  if (query.isLoading) return <Muted>加载中…</Muted>;
  if (query.isError) return <Notice tone="danger">{(query.error as Error).message}</Notice>;

  const b = query.data!;
  return (
    <>
      <div className="mb-4 text-xs text-muted-foreground">
        <Link to="/bulletin" className="hover:text-foreground">公告</Link>
        <span className="mx-1">/</span>
        <span>详情</span>
      </div>
      <PageHeader
        title={b.title}
        description={b.createdAt.slice(0, 16)}
        actions={b.pinned ? <Badge tone="warning"><Pin className="h-3 w-3" />置顶</Badge> : undefined}
      />
      <Card>
        <div className="whitespace-pre-wrap text-sm leading-7">{b.content}</div>
      </Card>
      <div className="mt-4">
        <Button size="sm" onClick={() => navigate('/bulletin')}>返回列表</Button>
        {isOwner && <Button size="sm" className="ml-2" onClick={() => navigate(`/bulletin/${bulletinId}?edit=1`)}>编辑公告</Button>}
      </div>
    </>
  );
}

/** 站主编辑视图：/bulletin/:id?edit=1 或从列表进 */
function BulletinEdit({ bulletinId }: { bulletinId: string }) {
  const isEdit = bulletinId !== 'new';
  const existing = useQuery({
    queryKey: ['bulletin-admin'],
    queryFn: adminListBulletins,
    enabled: isEdit,
  });
  const current = existing.data?.find((b) => String(b.id) === bulletinId);

  if (isEdit && existing.isLoading) return <Muted>加载中…</Muted>;
  if (existing.isError) return <Notice tone="danger">{existing.error.message}</Notice>;
  if (isEdit && !current) return <Notice tone="danger">公告不存在或已被删除</Notice>;
  return <BulletinForm key={bulletinId} bulletinId={bulletinId} current={current} />;
}

function BulletinForm({ bulletinId, current }: { bulletinId: string; current?: BulletinRow }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const isEdit = bulletinId !== 'new';

  const [title, setTitle] = useState(current?.title ?? '');
  const [content, setContent] = useState(current?.content ?? '');
  const [pinned, setPinned] = useState(current?.pinned ?? false);
  const [published, setPublished] = useState(current?.published ?? true);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () =>
      isEdit
        ? updateBulletin(bulletinId, { title, content, pinned, published })
        : createBulletin({ title, content, pinned, published }),
    onSuccess: () => {
      setError(null);
      setSaved(true);
      void qc.invalidateQueries({ queryKey: ['bulletins'] });
      void qc.invalidateQueries({ queryKey: ['bulletin-admin'] });
      void qc.invalidateQueries({ queryKey: ['bulletin'] });
      navigate('/bulletin');
    },
    onError: (e: Error) => setError(e.message),
  });

  const del = useMutation({
    mutationFn: () => deleteBulletin(bulletinId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['bulletins'] });
      void qc.invalidateQueries({ queryKey: ['bulletin-admin'] });
      qc.removeQueries({ queryKey: ['bulletin', bulletinId] });
      navigate('/bulletin');
    },
    onError: (failure: Error) => setError(failure.message),
  });

  return (
    <>
      <div className="mb-4 text-xs text-muted-foreground">
        <Link to="/bulletin" className="hover:text-foreground">公告</Link>
        <span className="mx-1">/</span>
        <span>{isEdit ? '编辑' : '新建'}</span>
      </div>
      <PageHeader title={isEdit ? '编辑公告' : '发布公告'} />
      <Card>
        <input
          className="mb-2 w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
          style={{ borderColor: 'var(--input)' }}
          placeholder="标题"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
        />
        <textarea
          className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-2 text-sm outline-none"
          style={{ borderColor: 'var(--input)', minHeight: 180 }}
          placeholder="公告内容"
          value={content}
          onChange={(e) => setContent(e.target.value)}
        />
        <div className="mt-2 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
          <label className="flex items-center gap-1.5">
            <input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} />
            置顶
          </label>
          <label className="flex items-center gap-1.5">
            <input type="checkbox" checked={published} onChange={(e) => setPublished(e.target.checked)} />
            发布（取消勾选存为草稿）
          </label>
        </div>
        {error && <div className="mt-2"><Notice tone="danger">{error}</Notice></div>}
        {saved && <div className="mt-2"><Notice>已保存</Notice></div>}
        <div className="mt-3 flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="primary"
            disabled={!title.trim() || !content.trim() || save.isPending}
            onClick={() => {
              setError(null);
              save.mutate();
            }}
          >
            {save.isPending ? '保存中…' : '保存'}
          </Button>
          {isEdit && (
            <Button
              size="sm"
              variant="danger"
              disabled={del.isPending}
              onClick={() => { if (confirm('确定删除这条公告？')) del.mutate(); }}
            >
              <Trash2 className="h-3.5 w-3.5" />
              删除
            </Button>
          )}
          <Button size="sm" onClick={() => navigate('/bulletin')}>取消</Button>
        </div>
      </Card>
    </>
  );
}

/* ==================== 站主公告管理（嵌入后台） ==================== */

export function BulletinAdminPanel() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const query = useQuery({ queryKey: ['bulletin-admin'], queryFn: adminListBulletins });
  const list = query.data ?? [];

  const del = useMutation({
    mutationFn: (id: number) => deleteBulletin(id),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['bulletin-admin'] }); void qc.invalidateQueries({ queryKey: ['bulletins'] }); void qc.invalidateQueries({ queryKey: ['bulletin'] }); },
  });

  return (
    <div className="space-y-4">
      <Card>
        <div className="flex items-center justify-between">
          <div className="text-sm font-medium">公告管理</div>
          <Button size="sm" variant="primary" onClick={() => navigate('/bulletin/new')}>
            发布新公告
          </Button>
        </div>
      </Card>
      {query.isLoading && <Muted>加载中…</Muted>}
      {query.isError && <Notice tone="danger">{query.error.message}</Notice>}
      {del.isError && <Notice tone="danger">{del.error.message}</Notice>}
      {query.data?.length === 0 && <Muted>暂无公告</Muted>}
      {list.map((b: BulletinRow) => (
        <Card key={b.id}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
              {b.pinned && <Pin className="h-3 w-3" style={{ color: 'var(--warning)' }} />}
              <span className="text-sm font-medium">{b.title}</span>
              {b.published ? <Badge tone="success">已发布</Badge> : <Badge tone="warning">草稿</Badge>}
            </div>
            <div className="flex flex-none items-center gap-2">
              <Button size="sm" onClick={() => navigate(`/bulletin/${b.id}?edit=1`)}>编辑</Button>
              <Button
                size="sm"
                variant="danger"
                disabled={del.isPending}
                aria-label={`删除公告 ${b.title}`}
                onClick={() => { if (confirm('确定删除这条公告？')) del.mutate(b.id); }}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
          <div className="mt-1 text-[11px] text-muted-foreground">{b.createdAt.slice(0, 16)}</div>
        </Card>
      ))}
    </div>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}
