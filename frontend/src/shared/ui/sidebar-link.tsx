import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { cn } from '@/shared/lib/cn'

/** 侧栏导航项，当前页高亮。 */
export function SidebarLink({
  to,
  icon: Icon,
  children,
}: {
  to: string
  icon: LucideIcon
  children: ReactNode
}) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          'flex min-h-9 items-center gap-2.5 rounded-sm px-2.5 text-foreground hover:bg-secondary',
          isActive &&
            'bg-sidebar-accent font-medium text-sidebar-accent-foreground hover:bg-sidebar-accent',
        )
      }
    >
      <Icon className="size-4" aria-hidden="true" />
      {children}
    </NavLink>
  )
}
