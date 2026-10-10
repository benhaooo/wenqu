import { FilePlusIcon, FolderPlusIcon, Trash2Icon } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import { Skeleton } from '@/shared/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/shared/ui/tooltip'
import { useNodeIndex } from '../hooks/useNodeIndex'
import { useNodeMutations } from '../hooks/useNodeMutations'
import { useWorkspaceAccess } from '../hooks/useWorkspaceAccess'
import { DocumentTree } from './DocumentTree'
import { SearchDialog } from './SearchDialog'

export function DocumentSidebar() {
  const { workspaceId, canEdit } = useWorkspaceAccess()
  const { index, isPending } = useNodeIndex(workspaceId)
  const mutations = useNodeMutations(workspaceId)

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r bg-muted/30">
      <div className="p-2">
        <SearchDialog workspaceId={workspaceId} />
      </div>
      <div className="flex items-center justify-between px-3 pb-1">
        <span className="text-xs font-medium text-muted-foreground">文档</span>
        {canEdit && (
          <div className="flex">
            <IconAction
              label="新建文档"
              onClick={() => {
                mutations.create(null, 'DOCUMENT')
              }}
            >
              <FilePlusIcon />
            </IconAction>
            <IconAction
              label="新建文件夹"
              onClick={() => {
                mutations.create(null, 'FOLDER')
              }}
            >
              <FolderPlusIcon />
            </IconAction>
          </div>
        )}
      </div>
      {isPending ? (
        <div className="grid gap-2 px-3">
          <Skeleton className="h-5" />
          <Skeleton className="h-5" />
          <Skeleton className="h-5 w-2/3" />
        </div>
      ) : (
        <DocumentTree workspaceId={workspaceId} index={index} canEdit={canEdit} />
      )}
      <div className="border-t p-2">
        <NavLink
          to={`/w/${String(workspaceId)}/trash`}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground',
              isActive && 'bg-muted text-foreground',
            )
          }
        >
          <Trash2Icon className="size-4" />
          回收站
        </NavLink>
      </div>
    </aside>
  )
}

function IconAction({
  label,
  onClick,
  children,
}: {
  label: string
  onClick: () => void
  children: ReactNode
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button variant="ghost" size="icon" className="size-7" aria-label={label} onClick={onClick}>
          {children}
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  )
}
