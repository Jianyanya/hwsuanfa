import type { ButtonHTMLAttributes, ReactNode } from 'react';

import { cn } from '@/lib/utils';

type Variant = 'primary' | 'ghost' | 'danger' | 'subtle';
type Size = 'sm' | 'md';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  children: ReactNode;
}

const VARIANT_STYLE: Record<Variant, React.CSSProperties> = {
  primary: {
    backgroundColor: 'var(--primary)',
    color: 'var(--primary-foreground)',
    borderColor: 'transparent',
  },
  ghost: {
    backgroundColor: 'transparent',
    color: 'var(--foreground)',
    borderColor: 'var(--border)',
  },
  subtle: {
    backgroundColor: 'var(--muted)',
    color: 'var(--foreground)',
    borderColor: 'transparent',
  },
  danger: {
    backgroundColor: 'transparent',
    color: 'var(--destructive)',
    borderColor: 'color-mix(in oklab, var(--destructive) 45%, transparent)',
  },
};

/**
 * 通用按钮。
 * 品牌色只用在 primary 上；次级操作用 ghost / subtle 保持中性（规格书 2.4）。
 */
export function Button({
  variant = 'ghost',
  size = 'md',
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type="button"
      className={cn(
        'inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-[var(--radius)] border font-medium transition-colors',
        size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3 py-1.5 text-sm',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      style={VARIANT_STYLE[variant]}
      {...rest}
    >
      {children}
    </button>
  );
}

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
}

export function Input({ label, className, id, ...rest }: InputProps) {
  const inputId = id ?? rest.name;
  return (
    <div className="w-full">
      {label && (
        <label className="mb-1 block text-xs text-muted-foreground" htmlFor={inputId}>
          {label}
        </label>
      )}
      <input
        id={inputId}
        className={cn(
          'w-full rounded-[var(--radius)] border bg-transparent px-3 py-1.5 text-sm outline-none',
          'placeholder:text-muted-foreground focus:border-[var(--primary)]',
          className,
        )}
        style={{ borderColor: 'var(--input)' }}
        {...rest}
      />
    </div>
  );
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
}

export function Select({ label, className, id, children, ...rest }: SelectProps) {
  const selectId = id ?? rest.name;
  return (
    <div className="w-full">
      {label && (
        <label className="mb-1 block text-xs text-muted-foreground" htmlFor={selectId}>
          {label}
        </label>
      )}
      <select
        id={selectId}
        className={cn(
          'w-full rounded-[var(--radius)] border px-3 py-1.5 text-sm outline-none',
          className,
        )}
        style={{
          borderColor: 'var(--input)',
          backgroundColor: 'var(--card)',
          color: 'var(--foreground)',
        }}
        {...rest}
      >
        {children}
      </select>
    </div>
  );
}

/** 内联提示条，用于表单错误 / 业务规则说明 */
export function Notice({
  children,
  tone = 'info',
}: {
  children: ReactNode;
  tone?: 'info' | 'danger' | 'warning';
}) {
  const color =
    tone === 'danger' ? 'var(--destructive)' : tone === 'warning' ? 'var(--warning)' : 'var(--muted-foreground)';
  return (
    <div
      className="rounded-[var(--radius)] border px-3 py-2 text-xs leading-relaxed"
      style={{
        borderColor: `color-mix(in oklab, ${color} 40%, transparent)`,
        backgroundColor: `color-mix(in oklab, ${color} 10%, transparent)`,
        color: tone === 'info' ? 'var(--muted-foreground)' : color,
      }}
      role={tone === 'danger' ? 'alert' : undefined}
    >
      {children}
    </div>
  );
}
