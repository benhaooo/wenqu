import { useEffect } from 'react'
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

export function WorkspaceLayout() {
  const { data: workspace, error } = useCurrentWorkspace()

  useEffect(() => {
    if (workspace) lastWorkspace.set(workspace.id)
  }, [workspace])

  return (
    <div className="flex min-h-svh flex-col">
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
      <main className="flex-1 px-6 py-8">
        {error ? (
          <Alert variant="destructive" className="mx-auto max-w-lg">
            <AlertTitle>无法打开空间</AlertTitle>
            <AlertDescription>
              <p>{error.message}</p>
              <Button asChild variant="link" className="h-auto p-0">
                <Link to="/">返回我的空间</Link>
              </Button>
            </AlertDescription>
          </Alert>
        ) : (
          workspace && <Outlet />
        )}
      </main>
    </div>
  )
}
