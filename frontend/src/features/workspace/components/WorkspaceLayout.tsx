import { UsersIcon } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'
import { Link, Outlet } from 'react-router'
import { UserMenu } from '@/features/auth'
import { Alert, AlertDescription, AlertTitle } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { SidebarLink } from '@/shared/ui/sidebar-link'
import { useCurrentWorkspace } from '../hooks/useCurrentWorkspace'
import { lastWorkspace } from '../lastWorkspace'
import { roleLabels } from '../roles'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

/**
 * 空间页面框架：左侧栏 + 主内容。侧栏中的搜索、导航项和目录树由应用层通过插槽传入
 * （它们属于 document 模块，workspace 模块不依赖 document）。
 */
export function WorkspaceLayout({
  search,
  navItems,
  tree,
}: {
  search: ReactNode
  navItems: ReactNode
  tree: ReactNode
}) {
  const { data: workspace, error } = useCurrentWorkspace()

  useEffect(() => {
    if (workspace) lastWorkspace.set(workspace.id)
  }, [workspace])

  return (
    <div className="flex h-svh max-md:h-auto max-md:min-h-svh max-md:flex-col">
      <nav
        aria-label="空间导航"
        className="flex w-[264px] shrink-0 flex-col gap-[18px] border-r border-sidebar-border bg-sidebar px-3 py-4 max-md:w-full max-md:border-r-0 max-md:border-b"
      >
        <WorkspaceSwitcher />
        {workspace && (
          <>
            {search}
            <div className="flex flex-col gap-0.5">
              <SidebarLink to={`/w/${String(workspace.id)}/settings`} icon={UsersIcon}>
                成员与设置
              </SidebarLink>
              {navItems}
            </div>
            <div className="flex min-h-0 flex-1 flex-col max-md:max-h-80">{tree}</div>
          </>
        )}
        <div className="mt-auto border-t border-sidebar-border pt-2">
          <UserMenu subtitle={workspace ? roleLabels[workspace.myRole] : undefined} />
        </div>
      </nav>
      <main className="min-w-0 flex-1 overflow-auto bg-background">
        {/* 已加载过空间时，后台刷新失败继续使用已有数据，不打断正在进行的编辑 */}
        {workspace ? (
          <Outlet />
        ) : (
          error && (
            <Alert variant="destructive" className="mx-auto mt-16 max-w-lg">
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
  )
}
