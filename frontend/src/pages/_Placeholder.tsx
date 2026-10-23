import { Card, PageHeader } from '@/components/ui/Card';

interface PlaceholderProps {
  title: string;
  description: string;
  /** 该页面后续要实现的内容清单 */
  todo: string[];
}

/** 骨架阶段的页面占位，列出待实现功能，便于对照规格书推进 */
export function Placeholder({ title, description, todo }: PlaceholderProps) {
  return (
    <>
      <PageHeader title={title} description={description} />
      <Card>
        <div className="mb-3 text-sm font-medium">待实现</div>
        <ul className="space-y-1.5">
          {todo.map((t, i) => (
            // ⚠️ 用下标做 key：清单里允许用空字符串当分组间距，
            //    若拿文本本身当 key，重复的空串会触发 React 的 duplicate key 警告
            <li key={i} className="flex items-start gap-2 text-sm text-muted-foreground">
              {t === '' ? (
                <span className="h-1.5" />
              ) : (
                <>
                  <span
                    className="mt-[7px] inline-block h-1.5 w-1.5 flex-none rounded-full"
                    style={{ backgroundColor: 'var(--primary)' }}
                  />
                  <span>{t}</span>
                </>
              )}
            </li>
          ))}
        </ul>
      </Card>
    </>
  );
}
