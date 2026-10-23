import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Loader2, Sparkles } from 'lucide-react';
import { z } from 'zod';

import { useAuth } from '@/stores/auth';
import { safeRedirect } from '@/lib/date';

const loginSchema = z.object({
  username: z.string().min(1, '请输入用户名'),
  password: z.string().min(6, '密码至少 6 位'),
});

/**
 * 登录页。
 *
 * 密码明文经 HTTPS 传输，服务端用 BCrypt 校验。
 * ⚠️ 不要在前端做哈希——参考站点是「客户端 SHA256 后传」，本项目不采用，
 *    因为那会让哈希本身变成等价密码，且无法配合 BCrypt。
 */
export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const navigate = useNavigate();
  const [params] = useSearchParams();

  const login = useAuth((s) => s.login);
  const user = useAuth((s) => s.user);
  const initializing = useAuth((s) => s.initializing);
  const bootstrap = useAuth((s) => s.bootstrap);

  const redirect = safeRedirect(params.get('redirect'));

  // 已登录用户直接放行，避免手动访问 /login 时卡住
  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  useEffect(() => {
    if (!initializing && user) {
      navigate(redirect, { replace: true });
    }
  }, [initializing, user, navigate, redirect]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const parsed = loginSchema.safeParse({ username, password });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? '输入有误');
      return;
    }

    setSubmitting(true);
    try {
      await login(parsed.data.username, parsed.data.password);
      navigate(redirect, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="card w-full max-w-sm p-6">
        <div className="mb-6 flex flex-col items-center gap-2">
          <div
            className="flex h-10 w-10 items-center justify-center rounded-lg"
            style={{ backgroundColor: 'var(--primary)' }}
          >
            <Sparkles className="h-5 w-5" style={{ color: 'var(--primary-foreground)' }} />
          </div>
          <div className="text-lg font-semibold">校队算法训练平台</div>
          <div className="text-xs text-muted-foreground">请使用站内账号登录</div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="text-xs text-muted-foreground" htmlFor="username">
              学号
            </label>
            <input
              id="username"
              className="mt-1 w-full rounded-[var(--radius)] border px-3 py-2 text-sm outline-none focus:border-[var(--primary)]"
              style={{ backgroundColor: 'transparent', borderColor: 'var(--input)' }}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="学号即登录账号"
              autoComplete="username"
              disabled={submitting}
            />
          </div>
          <div>
            <label className="text-xs text-muted-foreground" htmlFor="password">
              密码
            </label>
            <input
              id="password"
              type="password"
              className="mt-1 w-full rounded-[var(--radius)] border px-3 py-2 text-sm outline-none focus:border-[var(--primary)]"
              style={{ backgroundColor: 'transparent', borderColor: 'var(--input)' }}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              disabled={submitting}
            />
          </div>

          {params.get('passwordChanged') === '1' && <p className="text-xs text-muted-foreground">密码已修改，请使用新密码登录。</p>}
          {error && (
            <div
              className="rounded-[var(--radius)] px-3 py-2 text-xs"
              style={{
                color: 'var(--destructive)',
                backgroundColor: 'color-mix(in oklab, var(--destructive) 12%, transparent)',
              }}
              role="alert"
            >
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-[var(--radius)] py-2 text-sm font-medium disabled:opacity-60"
            style={{ backgroundColor: 'var(--primary)', color: 'var(--primary-foreground)' }}
          >
            {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            {submitting ? '登录中…' : '登录'}
          </button>
        </form>

        <div
          className="mt-4 space-y-2 border-t pt-3 text-[11px] text-muted-foreground"
          style={{ borderColor: 'var(--border)' }}
        >
          <Link
            to={`/register${redirect !== '/' ? `?redirect=${encodeURIComponent(redirect)}` : ''}`}
            className="inline-block font-medium hover:underline"
            style={{ color: 'var(--primary)' }}
          >
            没有账号？用邀请码注册 →
          </Link>
        </div>
      </div>
    </div>
  );
}
