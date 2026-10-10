import { PlusIcon, Trash2Icon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { SidebarLink } from '@/shared/ui/sidebar-link'
import { Skeleton } from '@/shared/ui/skeleton'
import { useNodeIndex } from '../hooks/useNodeIndex'
import { useNodeMutations } from '../hooks/useNodeMutations'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { DocumentTree } from './DocumentTree'
import { SearchDialog } from './SearchDialog'

/** 侧栏中的搜索入口。 */
export function SidebarSearch() {
  const { workspaceId } = useWorkspaceAccess()
  return <SearchDialog workspaceId={workspaceId} />
}

/** 侧栏导航中的回收站。 */
export function TrashNavLink() {
  const { workspaceId } = useWorkspaceAccess()
  return (
    <SidebarLink to={`/w/${String(workspaceId)}/trash`} icon={Trash2Icon}>
      回收站
    </SidebarLink>
  )
}

/** 侧栏中的「文档」目录树。renderBadge 由应用层传入，在文档行末显示其他模块的状态（如索引进度）。 */
export function SidebarTree({ renderBadge }: { renderBadge?: (documentId: number) => ReactNode }) {
  const { workspaceId, canEdit } = useWorkspaceAccess()
  const { index, isPending } = useNodeIndex(workspaceId)
  const mutations = useNodeMutations(workspaceId)

  return (
    <>
      <div className="flex min-h-8 items-center justify-between px-2.5">
        <span className="text-xs font-semibold tracking-[0.04em] text-muted-foreground">文档</span>
        {canEdit && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="新建"
                className="text-muted-foreground"
              >
                <PlusIcon />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                onSelect={() => {
                  mutations.create(null, 'DOCUMENT')
                }}
              >
                新建文档
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() => {
                  mutations.create(null, 'FOLDER')
                }}
              >
                新建文件夹
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>
      {isPending ? (
        <div className="grid gap-2 px-2.5 pt-1">
          <Skeleton className="h-5" />
          <Skeleton className="h-5" />
          <Skeleton className="h-5 w-2/3" />
        </div>
      ) : (
        <DocumentTree
          workspaceId={workspaceId}
          index={index}
          canEdit={canEdit}
          renderBadge={renderBadge}
        />
      )}
    </>
  )
}
