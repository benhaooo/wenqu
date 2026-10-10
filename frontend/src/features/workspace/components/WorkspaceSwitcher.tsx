import { CheckIcon, ChevronsUpDownIcon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useListMyWorkspaces } from '@/shared/api/generated/workspace/workspace'
import { BrandMark } from '@/shared/ui/brand-mark'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { useWorkspaceId } from '../hooks/useWorkspaceId'
import { CreateWorkspaceForm } from './CreateWorkspaceForm'

export function WorkspaceSwitcher() {
  const workspaceId = useWorkspaceId()
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  const { data: workspaces = [] } = useListMyWorkspaces({
    query: { select: (result) => result.data },
  })
  const current = workspaces.find((workspace) => workspace.id === workspaceId)

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            className="flex min-h-11 w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-left outline-none hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring aria-expanded:bg-secondary"
          >
            <BrandMark />
            <span className="min-w-0 flex-1 truncate font-semibold">
              {current?.name ?? '选择空间'}
            </span>
            <ChevronsUpDownIcon className="size-4 text-muted-foreground" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-64">
          <DropdownMenuLabel>我的空间</DropdownMenuLabel>
          {workspaces.map((workspace) => (
            <DropdownMenuItem
              key={workspace.id}
              onSelect={() => void navigate(`/w/${String(workspace.id)}`)}
            >
              <span className="truncate">{workspace.name}</span>
              {workspace.id === workspaceId && <CheckIcon className="ml-auto" />}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onSelect={() => {
              setCreating(true)
            }}
          >
            <PlusIcon />
            新建空间
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>新建空间</DialogTitle>
            <DialogDescription>创建后你将成为该空间的管理员</DialogDescription>
          </DialogHeader>
          <CreateWorkspaceForm
            onCreated={() => {
              setCreating(false)
            }}
          />
        </DialogContent>
      </Dialog>
    </>
  )
}
