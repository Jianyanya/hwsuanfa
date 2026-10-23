import { download, get, post, put, del, upload } from '@/lib/http';

/* ==================== 公告 ==================== */

export interface BulletinRow {
  id: number;
  title: string;
  content: string;
  pinned: boolean;
  published: boolean;
  createdAt: string;
}

export function fetchBulletins() {
  return get<BulletinRow[]>('/bulletin');
}

export function fetchBulletinDetail(id: number | string) {
  return get<BulletinRow>(`/bulletin/${id}`);
}

export function adminListBulletins() {
  return get<BulletinRow[]>('/bulletin/admin');
}

export function createBulletin(body: { title: string; content: string; pinned?: boolean; published?: boolean }) {
  return post<BulletinRow>('/bulletin', body);
}

export function updateBulletin(id: number | string, body: { title: string; content: string; pinned?: boolean; published?: boolean }) {
  return put<BulletinRow>(`/bulletin/${id}`, body);
}

export function deleteBulletin(id: number | string) {
  return del<void>(`/bulletin/${id}`);
}

/* ==================== 博客 ==================== */

export interface BlogEngagement {
  likeCount: number;
  favoriteCount: number;
  commentCount: number;
  liked: boolean;
  favorited: boolean;
}

export interface BlogCard extends BlogEngagement {
  id: number;
  authorId: number;
  title: string;
  summary: string | null;
  tags: string | null;
  author: string;
  status: 'DRAFT' | 'PUBLISHED';
  viewCount: number;
  publishedAt: string | null;
}

export interface BlogPageResult {
  items: BlogCard[];
  page: number;
  size: number;
  total: number;
  pages: number;
  hasNext: boolean;
}

export interface BlogDetail extends BlogEngagement {
  article: {
    id: number;
    authorId: number;
    title: string;
    summary: string | null;
    contentMd: string;
    tags: string | null;
    status: 'DRAFT' | 'PUBLISHED';
    viewCount: number;
    publishedAt: string | null;
  };
  author: string;
}

export function fetchBlogList() {
  return get<BlogCard[]>('/blog');
}

export function fetchBlogPage(params: { page?: number; size?: number; keyword?: string; tag?: string }) {
  return get<BlogPageResult>('/blog/page', params);
}

export function fetchBlogDetail(id: number | string) {
  return get<BlogDetail>(`/blog/${id}`);
}

export function fetchMyArticles() {
  return get<BlogCard[]>('/blog/mine');
}

export function fetchMyArticlesPage(params: { page?: number; size?: number; keyword?: string; tag?: string; status?: 'DRAFT' | 'PUBLISHED' }) {
  return get<BlogPageResult>('/blog/mine/page', params);
}

export function fetchFavoriteArticles() {
  return get<BlogCard[]>('/blog/favorites');
}

export function fetchFavoriteArticlesPage(params: { page?: number; size?: number; keyword?: string; tag?: string }) {
  return get<BlogPageResult>('/blog/favorites/page', params);
}

export interface ArticleAttachment {
  id: number;
  articleId: number;
  fileName: string;
  contentType: string;
  fileSize: number;
  createdAt: string | null;
  downloadUrl: string;
}

export function fetchArticleAttachments(articleId: number | string) {
  return get<ArticleAttachment[]>(`/blog/${articleId}/attachments`);
}

export function uploadArticleAttachment(articleId: number | string, file: File) {
  return upload<ArticleAttachment>(`/blog/${articleId}/attachments`, file);
}

export function deleteArticleAttachment(articleId: number | string, attachmentId: number | string) {
  return del<void>(`/blog/${articleId}/attachments/${attachmentId}`);
}

export async function downloadArticleAttachment(attachment: ArticleAttachment) {
  const blob = await download(attachment.downloadUrl);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = attachment.fileName;
  link.click();
  URL.revokeObjectURL(url);
}

export interface ArticlePayload {
  title: string;
  contentMd: string;
  summary?: string;
  tags?: string;
  status: 'DRAFT' | 'PUBLISHED';
}

export function createArticle(body: ArticlePayload) {
  return post<BlogDetail['article']>('/blog', body);
}

export function updateArticle(id: number | string, body: ArticlePayload) {
  return put<BlogDetail['article']>(`/blog/${id}`, body);
}

export function deleteArticle(id: number | string) {
  return del<void>(`/blog/${id}`);
}

export function setArticleLike(id: number | string, liked: boolean) {
  return liked ? put<BlogEngagement>(`/blog/${id}/like`) : del<BlogEngagement>(`/blog/${id}/like`);
}

export function setArticleFavorite(id: number | string, favorited: boolean) {
  return favorited ? put<BlogEngagement>(`/blog/${id}/favorite`) : del<BlogEngagement>(`/blog/${id}/favorite`);
}

export interface BlogComment {
  id: number;
  articleId: number;
  userId: number;
  author: string;
  content: string;
  createdAt: string;
}

export function fetchBlogComments(id: number | string) {
  return get<BlogComment[]>(`/blog/${id}/comments`);
}

export function createBlogComment(id: number | string, content: string) {
  return post<BlogComment>(`/blog/${id}/comments`, { content });
}

export function deleteBlogComment(id: number | string, commentId: number | string) {
  return del<void>(`/blog/${id}/comments/${commentId}`);
}

/* ==================== 站主：成员管理 ==================== */

export interface AdminUserRow {
  id: number;
  username: string;
  nickname: string;
  realName: string;
  className: string;
  role: string;
  status: string;
  cfHandle: string;
  atcoderHandle: string;
  luoguHandle: string;
}

export function adminListUsers() {
  return get<AdminUserRow[]>('/admin/users');
}

export function adminUpdateUser(id: number, body: {
  nickname?: string;
  realName?: string;
  className?: string;
  status?: string;
  cfHandle?: string;
  atcoderHandle?: string;
  luoguHandle?: string;
  newPassword?: string;
}) {
  return put<void>(`/admin/users/${id}`, body);
}

/** 站主：回填题目元数据（难度/标签，CF 官方源 + 洛谷服务端限流抓取） */
export function backfillProblemMetadata() {
  return post<{ updated: number; skipped: number; failed: number; remaining: number }>('/admin/problems/backfill-metadata');
}
