import { useEffect, type ReactNode } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { UserMenu } from '@/features/auth'
import { cn } from '@/shared/lib/cn'
import { Alert, AlertDescription, AlertTitle } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { useCurrentWorkspace } from '../hooks/useCurrentWorkspace'
import { lastWorkspace } from '../lastWorkspace'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

const navItems = [
  { to: '.', label: '概览', end: true },
  { to: 'members', label: '成员', end: false },
]

/** 空间页面框架；sidebar 由应用层传入（目录树属于 document 模块）。 */
export function WorkspaceLayout({ sidebar }: { sidebar: ReactNode }) {
  const { data: workspace, error } = useCurrentWorkspace()

  useEffect(() => {
    if (workspace) lastWorkspace.set(workspace.id)
  }, [workspace])

  return (
    <div className="flex h-svh flex-col">
      <header className="flex h-14 items-center justify-between border-b px-4">
        <div className="flex items-center gap-4">
          <WorkspaceSwitcher />
          <nav className="flex gap-1">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'rounded-md px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground',
                    isActive && 'bg-muted text-foreground',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <UserMenu />
      </header>
      <div className="flex min-h-0 flex-1">
        {workspace && sidebar}
        <main className="min-w-0 flex-1 overflow-auto px-6 py-8">
          {/* 已加载过空间时，后台刷新失败继续使用已有数据，不打断正在进行的编辑 */}
          {workspace ? (
            <Outlet />
          ) : (
            error && (
              <Alert variant="destructive" className="mx-auto max-w-lg">
                <AlertTitle>无法打开空间</AlertTitle>
                <AlertDescription>
                  <p>{error.message}</p>
                  <Button asChild variant="link" className="h-auto p-0">
                    <Link to="/">返回我的空间</Link>
                  </Button>
                </AlertDescription>
              </Alert>
            )
          )}
        </main>
      </div>
    </div>
  )
}
