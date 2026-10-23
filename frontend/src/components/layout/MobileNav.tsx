import { NavLink } from 'react-router-dom';

import { MOBILE_NAV } from '@/config/nav';
import { cn } from '@/lib/utils';

/** <768px 时替代侧栏的底部导航（规格书 3.2 节） */
export function MobileNav() {
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t backdrop-blur-md md:hidden"
      style={{
        backgroundColor: 'color-mix(in oklab, var(--background) 95%, transparent)',
        borderColor: 'var(--border)',
        paddingBottom: 'env(safe-area-inset-bottom, 0px)',
      }}
    >
      <div className="mx-auto flex h-14 max-w-lg items-stretch px-1">
        {MOBILE_NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className="flex min-h-[44px] flex-1 flex-col items-center justify-center gap-0.5 rounded-md text-[10px] transition-colors"
          >
            {({ isActive }) => (
              <>
                <item.icon
                  className="h-5 w-5"
                  style={{ color: isActive ? 'var(--primary)' : 'var(--muted-foreground)' }}
                />
                <span
                  className={cn(isActive ? 'font-medium' : '')}
                  style={{ color: isActive ? 'var(--foreground)' : 'var(--muted-foreground)' }}
                >
                  {item.label}
                </span>
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
