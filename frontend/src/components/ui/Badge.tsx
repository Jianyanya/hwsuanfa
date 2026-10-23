import { cn } from '@/lib/utils';

type PillTone = 'default' | 'success' | 'warning' | 'destructive' | 'primary';

const TONE_STYLE: Record<PillTone, React.CSSProperties> = {
  default: {},
  success: {
    backgroundColor: 'color-mix(in oklab, var(--success) 25%, transparent)',
    color: 'var(--success)',
  },
  warning: {
    backgroundColor: 'color-mix(in oklab, var(--warning) 25%, transparent)',
    color: 'var(--warning)',
  },
  destructive: {
    backgroundColor: 'color-mix(in oklab, var(--destructive) 22%, transparent)',
    color: 'var(--destructive)',
  },
  primary: {
    backgroundColor: 'color-mix(in oklab, var(--primary) 22%, transparent)',
    color: 'var(--primary)',
  },
};

interface BadgeProps {
  children: React.ReactNode;
  /** 实心填充（用于需要强提示的状态，如"已逾期"） */
  solid?: boolean;
  tone?: PillTone;
  className?: string;
}

/** 状态徽章：默认中性色，toned 时半透明底色 */
export function Badge({ children, solid = false, tone = 'default', className }: BadgeProps) {
  const style: React.CSSProperties = solid && tone !== 'default'
    ? {
        backgroundColor:
          tone === 'success'
            ? 'var(--success)'
            : tone === 'warning'
              ? 'var(--warning)'
              : tone === 'destructive'
                ? 'var(--destructive)'
                : 'var(--primary)',
        color: tone === 'warning' ? '#1a1a1a' : 'var(--primary-foreground)',
      }
    : TONE_STYLE[tone];

  return (
    <span className={cn('badge', className)} style={style}>
      {children}
    </span>
  );
}

interface PillProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  active?: boolean;
}

/** Pill 分段按钮（筛选器 / Tab 用） */
export function Pill({ active = false, className, children, ...rest }: PillProps) {
  return (
    <button
      type="button"
      data-on={active}
      className={cn('pill', className)}
      {...rest}
    >
      {children}
    </button>
  );
}
