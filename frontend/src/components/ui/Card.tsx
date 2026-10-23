import type { ReactNode } from 'react';

import { cn } from '@/lib/utils';

interface CardProps {
  children: ReactNode;
  className?: string;
  /** 无内边距（表格类卡片用） */
  flush?: boolean;
}

/** 通用卡片：本项目唯一的容器样式，靠边框分层，不用阴影 */
export function Card({ children, className, flush = false }: CardProps) {
  return (
    <div className={cn('card', !flush && 'p-5', className)}>{children}</div>
  );
}

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

/** 页头：h1 + 描述 + 右侧操作区（规格书 4.10 节） */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        {description && (
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        )}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}
