import { useEffect, useState } from 'react';
import { safeRedirect } from '@/lib/date';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Check, Loader2, Sparkles, X } from 'lucide-react';
import { z } from 'zod';

import { Button, Notice } from '@/components/ui/Button';
import { useAuth } from '@/stores/auth';

/**
 * 注册页。
 *
 * 关键点：
 *  - **登录账号是学号**，所以表单里没有单独的「用户名」，学号即账号。
 *  - 角色由**邀请码**决定（普通码 → 队员，管理员码 → 站主），
 *    邀请码的比对全部在后端完成，前端只是把用户输入原样提交。
 *  - 邀请码正确与否的错误提示要统一成「邀请码无效」，
 *    不能提示「这是管理员码但不是普通码」，否则等于帮人试探。
 */
const schema = z
  .object({
    nickname: z
      .string()
      .trim()
      .min(2, '名称至少 2 个字')
      .max(32, '名称不能超过 32 个字'),
    realName: z
      .string()
      .trim()
      .min(2, '请填写真实姓名')
      .max(32, '真实姓名不能超过 32 个字'),
    studentNo: z
      .string()
      .trim()
      .regex(/^[A-Za-z0-9]{4,32}$/, '学号只能是 4-32 位字母或数字'),
    className: z
      .string()
      .trim()
      .min(2, '请填写班级')
      .max(64, '班级不能超过 64 个字'),
    password: z.string().min(8, '密码至少 8 位').max(64, '密码不能超过 64 位'),
    confirmPassword: z.string(),
    inviteCode: z.string().trim().min(1, '请填写邀请码'),
  })
  .refine((v) => v.password === v.confirmPassword, {
    message: '两次输入的密码不一致',
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const [values, setValues] = useState<FormValues>({
    nickname: '',
    realName: '',
    studentNo: '',
    className: '',
    password: '',
    confirmPassword: '',
    inviteCode: '',
  });
  const [errors, setErrors] = useState<Partial<Record<keyof FormValues, string>>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const navigate = useNavigate();
  const [params] = useSearchParams();
  const register = useAuth((s) => s.register);
  const user = useAuth((s) => s.user);
  const initializing = useAuth((s) => s.initializing);
  const bootstrap = useAuth((s) => s.bootstrap);

  const redirect = safeRedirect(params.get('redirect'));

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  // 已登录就别停在注册页
  useEffect(() => {
    if (!initializing && user) {
      navigate(redirect, { replace: true });
    }
  }, [initializing, user, navigate, redirect]);

  function setField<K extends keyof FormValues>(key: K, value: string) {
    setValues((v) => ({ ...v, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
    setServerError(null);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setServerError(null);

    const parsed = schema.safeParse(values);
    if (!parsed.success) {
      const fieldErrors: Partial<Record<keyof FormValues, string>> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof FormValues;
        if (!fieldErrors[key]) fieldErrors[key] = issue.message;
      }
      setErrors(fieldErrors);
      return;
    }

    setSubmitting(true);
    try {
      // 只提交后端需要的字段，confirmPassword 留在前端
      await register({
        nickname: parsed.data.nickname,
        realName: parsed.data.realName,
        studentNo: parsed.data.studentNo,
        className: parsed.data.className,
        password: parsed.data.password,
        inviteCode: parsed.data.inviteCode,
      });
      navigate(redirect, { replace: true });
    } catch (err) {
      setServerError(err instanceof Error ? err.message : '注册失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="card w-full max-w-md p-6">
        <div className="mb-5 flex flex-col items-center gap-2">
          <div
            className="flex h-10 w-10 items-center justify-center rounded-lg"
            style={{ backgroundColor: 'var(--primary)' }}
          >
            <Sparkles className="h-5 w-5" style={{ color: 'var(--primary-foreground)' }} />
          </div>
          <div className="text-lg font-semibold">注册校队算法训练平台</div>
          <div className="text-xs text-muted-foreground">
            需要邀请码才能注册，请向站主索取
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3" noValidate>
          <Field
            id="nickname"
            label="名称"
            hint="站内显示名，需唯一"
            value={values.nickname}
            error={errors.nickname}
            disabled={submitting}
            onChange={(v) => setField('nickname', v)}
            placeholder="例如：小王"
          />

          <Field
            id="realName"
            label="真实姓名"
            hint="仅站主可见"
            value={values.realName}
            error={errors.realName}
            disabled={submitting}
            onChange={(v) => setField('realName', v)}
            placeholder="例如：王小明"
          />

          <Field
            id="studentNo"
            label="学号"
            hint="学号即登录账号"
            value={values.studentNo}
            error={errors.studentNo}
            disabled={submitting}
            onChange={(v) => setField('studentNo', v)}
            placeholder="例如：2021001"
            autoComplete="username"
          />

          <Field
            id="className"
            label="班级"
            value={values.className}
            error={errors.className}
            disabled={submitting}
            onChange={(v) => setField('className', v)}
            placeholder="例如：计算机 2101"
          />

          <Field
            id="password"
            label="密码"
            type="password"
            value={values.password}
            error={errors.password}
            disabled={submitting}
            onChange={(v) => setField('password', v)}
            autoComplete="new-password"
          />

          <Field
            id="confirmPassword"
            label="确认密码"
            type="password"
            value={values.confirmPassword}
            error={errors.confirmPassword}
            disabled={submitting}
            onChange={(v) => setField('confirmPassword', v)}
            autoComplete="new-password"
          />

          <Field
            id="inviteCode"
            label="邀请码"
            hint="普通邀请码注册为队员，管理员邀请码注册为管理员"
            value={values.inviteCode}
            error={errors.inviteCode}
            disabled={submitting}
            onChange={(v) => setField('inviteCode', v)}
            placeholder="请输入站主发放的邀请码"
            // 邀请码是否正确只有服务端知道，前端打勾会误导（填错也显示 ✓）
            showCheck={false}
          />

          {serverError && <Notice tone="danger">{serverError}</Notice>}

          <Button type="submit" variant="primary" className="w-full" disabled={submitting}>
            {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            {submitting ? '注册中…' : '注册并登录'}
          </Button>
        </form>

        <div className="mt-4 flex items-center justify-center">
          <Link
            to={`/login${redirect !== '/' ? `?redirect=${encodeURIComponent(redirect)}` : ''}`}
            className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-3 w-3" />
            已有账号，去登录
          </Link>
        </div>
      </div>
    </div>
  );
}

/* ---------------- 表单项 ---------------- */

function Field({
  id,
  label,
  hint,
  value,
  error,
  disabled,
  onChange,
  type = 'text',
  placeholder,
  autoComplete,
  showCheck = true,
}: {
  id: string;
  label: string;
  hint?: string;
  value: string;
  error?: string;
  disabled?: boolean;
  onChange: (v: string) => void;
  type?: string;
  placeholder?: string;
  autoComplete?: string;
  /** 是否在填写后显示 ✓。服务端才能判定对错的字段（如邀请码）应传 false */
  showCheck?: boolean;
}) {
  const valid = showCheck && !error && value.trim().length > 0;
  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between gap-2">
        <label className="text-xs text-muted-foreground" htmlFor={id}>
          {label}
        </label>
        {hint && <span className="text-[10px] text-muted-foreground opacity-80">{hint}</span>}
      </div>
      <div className="relative">
        <input
          id={id}
          type={type}
          className="w-full rounded-[var(--radius)] border bg-transparent px-3 py-2 pr-8 text-sm outline-none placeholder:text-muted-foreground"
          style={{
            borderColor: error ? 'var(--destructive)' : 'var(--input)',
          }}
          value={value}
          placeholder={placeholder}
          autoComplete={autoComplete}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        />
        {valid && (
          <Check
            className="pointer-events-none absolute right-2.5 top-2.5 h-3.5 w-3.5"
            style={{ color: 'var(--success)' }}
          />
        )}
        {error && (
          <X
            className="pointer-events-none absolute right-2.5 top-2.5 h-3.5 w-3.5"
            style={{ color: 'var(--destructive)' }}
          />
        )}
      </div>
      {error && (
        <div className="mt-1 text-[11px]" style={{ color: 'var(--destructive)' }}>
          {error}
        </div>
      )}
    </div>
  );
}
