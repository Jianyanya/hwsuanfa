import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, BookOpen, Bookmark, Download, Eye, FileText, MessageCircle, Paperclip, Pencil, Search, Send, ThumbsUp, Trash2, X } from 'lucide-react';

import { Card } from '@/components/ui/Card';
import { Badge, Pill } from '@/components/ui/Badge';
import { Button, Input, Notice } from '@/components/ui/Button';
import {
  createArticle, createBlogComment, deleteArticle, deleteBlogComment,
  deleteArticleAttachment, downloadArticleAttachment, fetchArticleAttachments,
  fetchBlogComments, fetchBlogDetail, fetchBlogPage, fetchFavoriteArticlesPage,
  fetchMyArticlesPage, setArticleFavorite, setArticleLike, updateArticle,
  uploadArticleAttachment,
} from '@/api/community';
import type { BlogCard, BlogDetail, BlogEngagement } from '@/api/community';
import { useAuth } from '@/stores/auth';
import { BlogMarkdown } from '@/components/blog/BlogMarkdown';

type BlogMode = 'default' | 'editor' | 'mine';

export default function BlogPage({ mode = 'default' }: { mode?: BlogMode }) {
  const { articleId } = useParams();
  const me = useAuth((s) => s.user);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [tag, setTag] = useState('');
  const [search, setSearch] = useState({ keyword: '', tag: '' });
  const publicList = useQuery({
    queryKey: ['blog', 'page', page, search.keyword, search.tag, me?.id ?? null],
    queryFn: () => fetchBlogPage({ page, size: 12, ...search }),
  });
  const cards = publicList.data?.items ?? [];

  return (
    <div className="space-y-5">
      <div
        className="sticky top-14 z-20 flex flex-wrap items-center justify-between gap-3 rounded-[var(--radius)] border p-3 backdrop-blur"
        style={{ backgroundColor: 'color-mix(in oklab, var(--background) 94%, transparent)', borderColor: 'var(--border)' }}
      >
        <Link to="/blog" className="flex items-center gap-2 text-base font-semibold"><BookOpen className="h-5 w-5 text-primary" />博客</Link>
        <div className="flex items-center gap-2">
          <Link to="/blog/mine"><Button size="sm" variant={mode === 'mine' ? 'subtle' : 'ghost'}><FileText className="h-3.5 w-3.5" />我的博客</Button></Link>
          <Link to="/blog/new"><Button size="sm" variant="primary"><Pencil className="h-3.5 w-3.5" />写博客</Button></Link>
        </div>
      </div>
      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_280px]">
        <div className="min-w-0">
          {mode === 'editor' ? <ArticleEditor key={articleId ?? 'new'} articleId={articleId ?? null} /> : mode === 'mine' ? <MyBlog /> : articleId ? <ArticleDetail key={articleId} articleId={articleId} /> : (
            <section className="space-y-4">
              <SectionHeading title="博客广场" description="记录解题思路，分享算法与成长" />
              {publicList.isLoading && <EmptyState>正在加载博客…</EmptyState>}
              {publicList.isError && <Notice tone="danger">{publicList.error.message}</Notice>}
              <BlogSearch keyword={keyword} tag={tag} onKeywordChange={setKeyword} onTagChange={setTag} onSubmit={() => { setPage(1); setSearch({ keyword: keyword.trim(), tag: tag.trim() }); }} onClear={() => { setKeyword(''); setTag(''); setPage(1); setSearch({ keyword: '', tag: '' }); }} />
              {publicList.data && <ArticleList articles={cards} empty="还没有公开博客，写下你的第一篇分享吧。" />}
              {publicList.data && <Pager page={publicList.data.page} pages={publicList.data.pages} onChange={setPage} />}
            </section>
          )}
        </div>
        <aside className="space-y-4 lg:sticky lg:top-36">
          <Card>
            <div className="mb-4 flex items-center justify-between"><h2 className="text-sm font-semibold">最新的博客</h2><Link to="/blog" className="text-xs text-muted-foreground hover:text-primary">全部</Link></div>
            {publicList.isLoading && <p className="text-xs text-muted-foreground">正在加载…</p>}
            {publicList.isError && <p className="text-xs text-muted-foreground">暂时无法加载最新博客</p>}
            {cards.length === 0 && <p className="text-xs text-muted-foreground">还没有公开博客</p>}
            <div className="divide-y divide-[var(--border)]">
              {cards.slice(0, 6).map((article) => (
                <Link key={article.id} to={`/blog/${article.id}`} className="group block py-3 first:pt-0 last:pb-0">
                  <div className="line-clamp-2 break-words text-sm font-medium group-hover:text-primary">{article.title}</div>
                  <div className="mt-1.5 flex flex-wrap gap-x-2 text-[11px] text-muted-foreground"><span>{article.author}</span><time>{formatDate(article.publishedAt)}</time></div>
                </Link>
              ))}
            </div>
          </Card>
          <Card><h2 className="mb-2 text-sm font-semibold">把思考留下来</h2><p className="text-xs leading-6 text-muted-foreground">题解、比赛复盘、学习笔记，都可以在这里分享。收藏喜欢的博客，随时在「我的博客」里重读。</p></Card>
        </aside>
      </div>
    </div>
  );
}

function MyBlog() {
  const me = useAuth((s) => s.user);
  const [tab, setTab] = useState<'articles' | 'favorites'>('articles');
  const [filter, setFilter] = useState<'ALL' | 'PUBLISHED' | 'DRAFT'>('ALL');
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [tag, setTag] = useState('');
  const [search, setSearch] = useState({ keyword: '', tag: '' });
  const mine = useQuery({
    queryKey: ['blog', 'mine-page', me?.id, page, filter, search.keyword, search.tag],
    queryFn: () => fetchMyArticlesPage({ page, size: 12, ...search, status: filter === 'ALL' ? undefined : filter }),
    enabled: !!me && tab === 'articles',
  });
  const favorites = useQuery({
    queryKey: ['blog', 'favorites-page', me?.id, page, search.keyword, search.tag],
    queryFn: () => fetchFavoriteArticlesPage({ page, size: 12, ...search }),
    enabled: !!me && tab === 'favorites',
  });
  const current = tab === 'articles' ? mine : favorites;
  const articles = current.data?.items ?? [];
  const submitSearch = () => { setPage(1); setSearch({ keyword: keyword.trim(), tag: tag.trim() }); };
  return (
    <section className="space-y-4">
      <SectionHeading title="我的博客" description="管理自己的文章、草稿和收藏" />
      <div className="flex flex-wrap gap-2" aria-label="我的博客分类">
        <Pill active={tab === 'articles'} aria-pressed={tab === 'articles'} onClick={() => { setTab('articles'); setPage(1); }}>我的文章{mine.data ? ` · ${mine.data.total}` : ''}</Pill>
        <Pill active={tab === 'favorites'} aria-pressed={tab === 'favorites'} onClick={() => { setTab('favorites'); setPage(1); }}><Bookmark className="mr-1 inline h-3.5 w-3.5" />我的收藏{favorites.data ? ` · ${favorites.data.total}` : ''}</Pill>
      </div>
      {tab === 'articles' && <div className="flex gap-2 text-xs">{([['ALL', '全部'], ['PUBLISHED', '已发布'], ['DRAFT', '草稿']] as const).map(([value, label]) => <Button key={value} size="sm" variant={filter === value ? 'subtle' : 'ghost'} onClick={() => { setFilter(value); setPage(1); }}>{label}</Button>)}</div>}
      <BlogSearch keyword={keyword} tag={tag} onKeywordChange={setKeyword} onTagChange={setTag} onSubmit={submitSearch} onClear={() => { setKeyword(''); setTag(''); setPage(1); setSearch({ keyword: '', tag: '' }); }} />
      {current.isLoading && <EmptyState>正在加载…</EmptyState>}
      {current.isError && <Notice tone="danger">{current.error.message}</Notice>}
      {articles && <ArticleList articles={articles} manage={tab === 'articles'} empty={tab === 'favorites' ? '还没有收藏，点击博客上的收藏按钮，就能在这里找到它。' : filter === 'DRAFT' ? '还没有草稿。' : '还没有博客，点击上方「写博客」开始记录吧。'} />}
      {current.data && <Pager page={current.data.page} pages={current.data.pages} onChange={setPage} />}
    </section>
  );
}

function BlogSearch({ keyword, tag, onKeywordChange, onTagChange, onSubmit, onClear }: {
  keyword: string;
  tag: string;
  onKeywordChange: (value: string) => void;
  onTagChange: (value: string) => void;
  onSubmit: () => void;
  onClear: () => void;
}) {
  return <form className="flex flex-wrap items-end gap-2 rounded-[var(--radius)] border p-3" style={{ borderColor: 'var(--border)' }} onSubmit={(event) => { event.preventDefault(); onSubmit(); }}>
    <div className="min-w-[180px] flex-1"><label htmlFor="blog-keyword" className="mb-1 block text-[11px] text-muted-foreground">关键词</label><input id="blog-keyword" className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none focus:border-[var(--primary)]" style={{ borderColor: 'var(--input)' }} value={keyword} onChange={(event) => onKeywordChange(event.target.value)} placeholder="标题、摘要或正文" maxLength={80} /></div>
    <div className="w-36"><label htmlFor="blog-tag" className="mb-1 block text-[11px] text-muted-foreground">标签</label><input id="blog-tag" className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none focus:border-[var(--primary)]" style={{ borderColor: 'var(--input)' }} value={tag} onChange={(event) => onTagChange(event.target.value)} placeholder="如 DP" maxLength={40} /></div>
    <Button type="submit" size="sm" variant="primary"><Search className="h-3.5 w-3.5" />搜索</Button>
    {(keyword || tag) && <Button type="button" size="sm" variant="ghost" onClick={onClear}><X className="h-3.5 w-3.5" />清除</Button>}
  </form>;
}

function Pager({ page, pages, onChange }: { page: number; pages: number; onChange: (page: number) => void }) {
  if (pages <= 1) return null;
  return <div className="flex items-center justify-center gap-2 pt-2 text-xs"><Button size="sm" variant="ghost" disabled={page <= 1} onClick={() => onChange(page - 1)}>上一页</Button><span className="text-muted-foreground">第 {page} / {pages} 页</span><Button size="sm" variant="ghost" disabled={page >= pages} onClick={() => onChange(page + 1)}>下一页</Button></div>;
}

function ArticleList({ articles, empty, manage = false }: { articles: BlogCard[]; empty: string; manage?: boolean }) {
  const [visibleCount, setVisibleCount] = useState(20);
  if (!articles.length) return <EmptyState>{empty}</EmptyState>;
  return <div className="space-y-3">{articles.slice(0, visibleCount).map((article) => (
    <Card key={article.id}>
      <div className="flex items-start justify-between gap-3"><Link to={`/blog/${article.id}`} className="min-w-0 break-words text-base font-semibold hover:text-primary">{article.title}</Link>{article.status === 'DRAFT' && <Badge tone="warning" className="shrink-0">草稿</Badge>}</div>
      {article.summary && <p className="mt-2 line-clamp-3 break-words text-sm leading-6 text-muted-foreground">{article.summary}</p>}
      <ArticleTags tags={article.tags} />
      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground"><span>{article.author}</span><time>{article.status === 'DRAFT' ? '尚未发布' : formatDate(article.publishedAt)}</time><span className="inline-flex items-center gap-1"><Eye className="h-3 w-3" />{article.viewCount} 次浏览</span></div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-[var(--border)] pt-3">
        {article.status === 'PUBLISHED' ? <ArticleInteractions articleId={article.id} engagement={article} /> : <span className="text-xs text-muted-foreground">继续编辑，准备好后发布</span>}
        {manage && <Link to={`/blog/${article.id}/edit`}><Button size="sm"><Pencil className="h-3 w-3" />编辑</Button></Link>}
      </div>
    </Card>
  ))}{articles.length > visibleCount && <Button className="w-full" onClick={() => setVisibleCount((count) => count + 20)}>加载更多博客（还有 {articles.length - visibleCount} 篇）</Button>}</div>;
}

function ArticleInteractions({ articleId, engagement }: { articleId: number | string; engagement: BlogEngagement }) {
  const me = useAuth((s) => s.user);
  const location = useLocation();
  const qc = useQueryClient();
  const [loginPrompt, setLoginPrompt] = useState(false);
  const mutation = useMutation({
    mutationFn: ({ kind, value }: { kind: 'like' | 'favorite'; value: boolean }) => kind === 'like' ? setArticleLike(articleId, value) : setArticleFavorite(articleId, value),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['blog'] }),
  });
  const toggle = (kind: 'like' | 'favorite', value: boolean) => {
    if (!me) { setLoginPrompt(true); return; }
    setLoginPrompt(false);
    mutation.mutate({ kind, value });
  };
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" variant={engagement.liked ? 'subtle' : 'ghost'} disabled={mutation.isPending} aria-pressed={engagement.liked} onClick={() => toggle('like', !engagement.liked)}><ThumbsUp className="h-3.5 w-3.5" fill={engagement.liked ? 'currentColor' : 'none'} />{engagement.liked ? '已点赞' : '点赞'} {engagement.likeCount}</Button>
        <Button size="sm" variant={engagement.favorited ? 'subtle' : 'ghost'} disabled={mutation.isPending} aria-pressed={engagement.favorited} onClick={() => toggle('favorite', !engagement.favorited)}><Bookmark className="h-3.5 w-3.5" fill={engagement.favorited ? 'currentColor' : 'none'} />{engagement.favorited ? '已收藏' : '收藏'} {engagement.favoriteCount}</Button>
        <Link to={`/blog/${articleId}#comments`} className="inline-flex items-center gap-1.5 px-1 text-xs text-muted-foreground hover:text-primary"><MessageCircle className="h-3.5 w-3.5" />评论 {engagement.commentCount}</Link>
      </div>
      {loginPrompt && <Notice><Link className="text-primary underline" to={loginUrl(location.pathname + location.search)}>登录</Link>后即可点赞和收藏。</Notice>}
      {mutation.isError && <Notice tone="danger">{mutation.error.message}</Notice>}
    </div>
  );
}

function ArticleDetail({ articleId }: { articleId: string }) {
  const me = useAuth((s) => s.user);
  const navigate = useNavigate();
  const qc = useQueryClient();
  const location = useLocation();
  const commentsRef = useRef<HTMLDivElement>(null);
  const query = useQuery({ queryKey: ['blog', 'detail', articleId, me?.id ?? null], queryFn: () => fetchBlogDetail(articleId) });
  const remove = useMutation({
    mutationFn: () => deleteArticle(articleId),
    onSuccess: () => {
      qc.removeQueries({ queryKey: ['blog', 'detail', articleId] });
      void qc.invalidateQueries({ queryKey: ['blog'] });
      navigate('/blog/mine');
    },
  });
  useEffect(() => {
    if (location.hash === '#comments' && query.data?.article.status === 'PUBLISHED') commentsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [location.hash, query.data?.article.status]);
  if (query.isLoading) return <EmptyState>正在加载博客…</EmptyState>;
  if (query.isError) return <Notice tone="danger">{query.error.message}</Notice>;
  if (!query.data) return null;
  const { article, author } = query.data;
  const canManage = !!me && (me.id === article.authorId || me.siteOwner);
  return (
    <div className="space-y-4">
      <Link to="/blog" className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-primary"><ArrowLeft className="h-3.5 w-3.5" />返回博客广场</Link>
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-3"><h1 className="min-w-0 flex-1 break-words text-2xl font-semibold leading-snug">{article.title}</h1>{article.status === 'DRAFT' && <Badge tone="warning">草稿</Badge>}</div>
        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-xs text-muted-foreground">
          <span>{author}</span><time>{article.status === 'DRAFT' ? '尚未发布' : formatDate(article.publishedAt, true)}</time><span>{article.viewCount} 次浏览</span>
          {canManage && <><Link to={`/blog/${articleId}/edit`}><Button size="sm"><Pencil className="h-3 w-3" />编辑</Button></Link><Button size="sm" variant="danger" disabled={remove.isPending} onClick={() => { if (confirm('确定删除这篇博客？删除后无法恢复。')) remove.mutate(); }}><Trash2 className="h-3 w-3" />{remove.isPending ? '删除中…' : '删除'}</Button></>}
        </div>
        <ArticleTags tags={article.tags} />
        {remove.isError && <div className="mt-3"><Notice tone="danger">{remove.error.message}</Notice></div>}
        <div className="my-5 border-t border-[var(--border)]" />
        <BlogMarkdown content={article.contentMd} />
        <ArticleAttachments articleId={articleId} canManage={canManage} />
        {article.status === 'PUBLISHED' && <div className="mt-6 border-t border-[var(--border)] pt-4"><ArticleInteractions articleId={articleId} engagement={query.data} /></div>}
      </Card>
      {article.status === 'PUBLISHED' && <div ref={commentsRef} id="comments" className="scroll-mt-36"><ArticleComments articleId={articleId} /></div>}
    </div>
  );
}

function ArticleAttachments({ articleId, canManage }: { articleId: string | number; canManage: boolean }) {
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const list = useQuery({ queryKey: ['blog', 'attachments', articleId], queryFn: () => fetchArticleAttachments(articleId) });
  const upload = useMutation({ mutationFn: (file: File) => uploadArticleAttachment(articleId, file), onSuccess: () => { void qc.invalidateQueries({ queryKey: ['blog', 'attachments', articleId] }); } });
  const remove = useMutation({ mutationFn: (id: number) => deleteArticleAttachment(articleId, id), onSuccess: () => { void qc.invalidateQueries({ queryKey: ['blog', 'attachments', articleId] }); } });
  const download = useMutation({ mutationFn: downloadArticleAttachment });
  if (list.isLoading) return <div className="mt-6 border-t border-[var(--border)] pt-4 text-xs text-muted-foreground">正在加载附件…</div>;
  if (list.isError) return <div className="mt-6 border-t border-[var(--border)] pt-4"><Notice tone="danger">附件加载失败：{list.error.message}</Notice></div>;
  if (!canManage && !list.data?.length) return null;
  return <div className="mt-6 border-t border-[var(--border)] pt-4">
    <div className="mb-2 flex flex-wrap items-center justify-between gap-2"><h2 className="flex items-center gap-1.5 text-sm font-semibold"><Paperclip className="h-4 w-4" />附件{list.data?.length ? ` · ${list.data.length}` : ''}</h2>{canManage && <><Button size="sm" variant="ghost" disabled={upload.isPending} onClick={() => fileRef.current?.click()}><Paperclip className="h-3.5 w-3.5" />{upload.isPending ? '上传中…' : '上传附件'}</Button><input ref={fileRef} type="file" className="hidden" onChange={(event) => { const file = event.target.files?.[0]; event.target.value = ''; if (file) upload.mutate(file); }} /></>}</div>
    {upload.isError && <Notice tone="danger">{upload.error.message}</Notice>}
    {remove.isError && <Notice tone="danger">{remove.error.message}</Notice>}
    {list.data?.length ? <div className="space-y-1.5">{list.data.map((attachment) => <div key={attachment.id} className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--radius)] border px-3 py-2 text-xs" style={{ borderColor: 'var(--border)' }}><span className="min-w-0 flex-1 truncate">{attachment.fileName}<span className="ml-2 text-muted-foreground">{formatBytes(attachment.fileSize)}</span></span><span className="flex items-center gap-1"><Button size="sm" variant="ghost" disabled={download.isPending} onClick={() => download.mutate(attachment)}><Download className="h-3.5 w-3.5" />下载</Button>{canManage && <Button size="sm" variant="ghost" disabled={remove.isPending} onClick={() => { if (confirm('确定删除这个附件？')) remove.mutate(attachment.id); }}><Trash2 className="h-3.5 w-3.5" />删除</Button>}</span></div>)}</div> : <p className="text-xs text-muted-foreground">还没有附件。</p>}
  </div>;
}

function ArticleComments({ articleId }: { articleId: string }) {
  const me = useAuth((s) => s.user);
  const qc = useQueryClient();
  const [content, setContent] = useState('');
  const query = useQuery({ queryKey: ['blog', 'comments', articleId, me?.id ?? null], queryFn: () => fetchBlogComments(articleId) });
  const send = useMutation({ mutationFn: () => createBlogComment(articleId, content.trim()), onSuccess: () => { setContent(''); void qc.invalidateQueries({ queryKey: ['blog'] }); } });
  const remove = useMutation({ mutationFn: (commentId: number) => deleteBlogComment(articleId, commentId), onSuccess: () => { void qc.invalidateQueries({ queryKey: ['blog'] }); } });
  return (
    <Card>
      <h2 className="mb-4 text-base font-semibold">评论{query.data ? ` · ${query.data.length}` : ''}</h2>
      {me ? <form className="mb-5 space-y-2" onSubmit={(event) => { event.preventDefault(); if (content.trim() && !send.isPending) send.mutate(); }}>
        <label htmlFor="blog-comment" className="block text-xs text-muted-foreground">分享你的想法</label>
        <textarea id="blog-comment" className="min-h-24 w-full resize-y rounded-[var(--radius)] border border-[var(--input)] bg-transparent px-3 py-2 text-sm outline-none focus:border-[var(--primary)]" placeholder="友好讨论，一起进步…" value={content} onChange={(event) => setContent(event.target.value)} maxLength={2000} disabled={send.isPending} />
        <div className="flex items-center justify-between gap-3"><span className="text-xs text-muted-foreground">{content.length}/2000</span><Button type="submit" size="sm" variant="primary" disabled={!content.trim() || send.isPending}><Send className="h-3.5 w-3.5" />{send.isPending ? '发表中…' : '发表评论'}</Button></div>
        {send.isError && <Notice tone="danger">{send.error.message}</Notice>}
      </form> : <div className="mb-5"><Notice><Link className="text-primary underline" to={loginUrl(`/blog/${articleId}#comments`)}>登录</Link>后参与评论。</Notice></div>}
      {query.isLoading && <p className="text-xs text-muted-foreground">正在加载评论…</p>}
      {query.isError && <Notice tone="danger">{query.error.message}</Notice>}
      {remove.isError && <Notice tone="danger">{remove.error.message}</Notice>}
      {query.data?.length === 0 && <p className="py-3 text-sm text-muted-foreground">还没有评论，来交流一下吧。</p>}
      <div className="divide-y divide-[var(--border)]">{query.data?.map((comment) => <div key={comment.id} className="py-4 first:pt-0 last:pb-0">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2"><div className="flex flex-wrap items-center gap-2 text-xs"><span className="font-medium">{comment.author}</span><time className="text-muted-foreground">{formatDate(comment.createdAt, true)}</time></div>{me && (me.id === comment.userId || me.siteOwner) && <Button size="sm" variant="ghost" aria-label={`删除评论（${comment.id}）`} disabled={remove.isPending} onClick={() => { if (confirm('确定删除这条评论？')) remove.mutate(comment.id); }}>删除</Button>}</div>
        <p className="whitespace-pre-wrap break-words text-sm leading-7">{comment.content}</p>
      </div>)}</div>
    </Card>
  );
}

function ArticleEditor({ articleId }: { articleId: string | null }) {
  const me = useAuth((s) => s.user);
  const existing = useQuery({ queryKey: ['blog', 'detail', articleId, me?.id ?? null], queryFn: () => fetchBlogDetail(articleId!), enabled: !!articleId });
  if (articleId && existing.isLoading) return <EmptyState>正在加载文章…</EmptyState>;
  if (articleId && existing.isError) return <Notice tone="danger">{existing.error.message}</Notice>;
  if (articleId && !existing.data) return null;
  if (existing.data && me?.id !== existing.data.article.authorId && !me?.siteOwner) return <Notice tone="danger">你只能编辑自己的博客。</Notice>;
  return <ArticleEditorForm key={`${me?.id}:${articleId ?? 'new'}`} articleId={articleId} existing={existing.data?.article} />;
}

interface EditorContent {
  title: string;
  tags: string;
  summary: string;
  contentMd: string;
}

function ArticleEditorForm({ articleId, existing }: { articleId: string | null; existing?: BlogDetail['article'] }) {
  const me = useAuth((s) => s.user);
  const navigate = useNavigate();
  const qc = useQueryClient();
  const storageKey = `hwsuanfa.blog.editor:${me?.id}:${articleId ?? 'new'}`;
  const saved = useRef(false);
  const [form, setForm] = useState<EditorContent>(() => {
    const initial = { title: existing?.title ?? '', tags: existing?.tags ?? '', summary: existing?.summary ?? '', contentMd: existing?.contentMd ?? '' };
    try {
      const stored: unknown = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null');
      if (stored && typeof stored === 'object' && Object.keys(initial).every((key) => typeof (stored as Record<string, unknown>)[key] === 'string')) return stored as EditorContent;
    } catch { return initial; }
    return initial;
  });
  const [preview, setPreview] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (saved.current) return;
    try { sessionStorage.setItem(storageKey, JSON.stringify(form)); } catch { return; }
  }, [form, storageKey]);
  const save = useMutation({
    mutationFn: (status: 'DRAFT' | 'PUBLISHED') => {
      const body = { ...form, title: form.title.trim(), tags: form.tags.trim(), summary: form.summary.trim(), status };
      return articleId ? updateArticle(articleId, body) : createArticle(body);
    },
    onSuccess: async (article) => {
      saved.current = true;
      try { sessionStorage.removeItem(storageKey); } catch { saved.current = true; }
      await qc.invalidateQueries({ queryKey: ['blog'] });
      navigate(article.status === 'DRAFT' ? '/blog/mine' : `/blog/${article.id}`);
    },
    onError: (failure: Error) => setError(failure.message),
  });
  const valid = !!form.title.trim() && !!form.contentMd.trim();
  const setField = (name: keyof EditorContent, value: string) => setForm((previous) => ({ ...previous, [name]: value }));
  const submit = (status: 'DRAFT' | 'PUBLISHED') => { setError(null); save.mutate(status); };
  return (
    <section className="space-y-4">
      <SectionHeading title={articleId ? '编辑博客' : '写博客'} description="用文字记录思路，用代码分享解法" />
      <Card><form className="space-y-4" onSubmit={(event) => { event.preventDefault(); if (valid && !save.isPending) submit('PUBLISHED'); }}>
        <Input id="blog-title" label="标题" placeholder="给你的博客起个标题" value={form.title} onChange={(event) => setField('title', event.target.value)} maxLength={200} required disabled={save.isPending} />
        <Input id="blog-tags" label="标签（选填）" placeholder="题解,动态规划,比赛复盘" value={form.tags} onChange={(event) => setField('tags', event.target.value)} maxLength={255} disabled={save.isPending} />
        <Input id="blog-summary" label="摘要（选填）" placeholder="用一两句话介绍这篇博客，不填写时会自动生成" value={form.summary} onChange={(event) => setField('summary', event.target.value)} maxLength={500} disabled={save.isPending} />
        <div>
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2"><label htmlFor="blog-content" className="text-xs text-muted-foreground">正文</label><div className="flex gap-1"><Button size="sm" variant={preview ? 'ghost' : 'subtle'} onClick={() => setPreview(false)}>编辑</Button><Button size="sm" variant={preview ? 'subtle' : 'ghost'} onClick={() => setPreview(true)}><Eye className="h-3 w-3" />预览</Button></div></div>
          {preview ? <div className="min-h-[380px] rounded-[var(--radius)] border border-[var(--border)] p-4">{form.contentMd.trim() ? <BlogMarkdown content={form.contentMd} /> : <p className="text-sm text-muted-foreground">写点内容，再来看看预览。</p>}</div> : <textarea id="blog-content" className="min-h-[380px] w-full resize-y rounded-[var(--radius)] border border-[var(--input)] bg-transparent px-3 py-3 font-mono text-[13px] leading-6 outline-none focus:border-[var(--primary)]" placeholder={'## 我的解题思路\n\n正文支持 Markdown，可以使用标题、列表和代码块。\n\n```cpp\n#include <bits/stdc++.h>\n```'} value={form.contentMd} onChange={(event) => setField('contentMd', event.target.value)} spellCheck={false} disabled={save.isPending} />}
          <p className="mt-2 text-xs text-muted-foreground">支持 Markdown 标题、列表、引用、链接和代码块。输入会暂存在当前浏览器标签页中。</p>
        </div>
        {error && <Notice tone="danger">{error}</Notice>}
        <div className="flex flex-wrap gap-2 border-t border-[var(--border)] pt-4">
          <Button type="submit" variant="primary" disabled={!valid || save.isPending}><Send className="h-3.5 w-3.5" />{save.isPending && save.variables === 'PUBLISHED' ? '发布中…' : '发布博客'}</Button>
          <Button disabled={!valid || save.isPending} onClick={() => submit('DRAFT')}>{save.isPending && save.variables === 'DRAFT' ? '保存中…' : '保存草稿'}</Button>
          <Button variant="ghost" disabled={save.isPending} onClick={() => navigate('/blog/mine')}>返回我的博客</Button>
        </div>
      </form></Card>
      {articleId && <ArticleAttachments articleId={articleId} canManage />}
    </section>
  );
}

function ArticleTags({ tags }: { tags: string | null }) {
  if (!tags) return null;
  return <div className="mt-3 flex flex-wrap gap-1.5">{Array.from(new Set(tags.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean))).map((tag) => <Badge key={tag} className="max-w-full truncate">{tag}</Badge>)}</div>;
}

function SectionHeading({ title, description }: { title: string; description: string }) {
  return <div><h1 className="text-xl font-semibold">{title}</h1><p className="mt-1 text-sm text-muted-foreground">{description}</p></div>;
}

function EmptyState({ children }: { children: ReactNode }) {
  return <Card><div className="py-10 text-center text-sm leading-7 text-muted-foreground">{children}</div></Card>;
}

function formatDate(value: string | null, withTime = false) {
  return value ? value.replace('T', ' ').slice(0, withTime ? 16 : 10) : '';
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function loginUrl(redirect: string) {
  return `/login?redirect=${encodeURIComponent(redirect)}`;
}
