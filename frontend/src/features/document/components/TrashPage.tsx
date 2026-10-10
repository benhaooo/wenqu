import { useQueryClient } from '@tanstack/react-query'
import { FileTextIcon, FolderIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  getListNodesQueryKey,
  getListTrashQueryKey,
  useListTrash,
  useRestoreNode,
} from '@/shared/api/generated/document/document'
import { Button } from '@/shared/ui/button'
import { useWorkspaceAccess } from '../hooks/useWorkspaceAccess'

const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

export function TrashPage() {
  const { workspaceId, canEdit } = useWorkspaceAccess()
  const queryClient = useQueryClient()
  const { data: items = [] } = useListTrash(workspaceId, {
    query: { select: (result) => result.data },
  })
  const restore = useRestoreNode({
    mutation: {
      onSuccess: () => {
        toast.success('已恢复')
        void queryClient.invalidateQueries({ queryKey: getListTrashQueryKey(workspaceId) })
        void queryClient.invalidateQueries({ queryKey: getListNodesQueryKey(workspaceId) })
      },
      onError: (error) => toast.error(error.message),
    },
  })

  return (
    <div className="mx-auto flex max-w-[960px] flex-col gap-6 px-8 pt-10 pb-20 max-sm:px-4">
      <div>
        <h1 className="mb-1 text-[26px] font-bold">回收站</h1>
        <p className="text-muted-foreground">
          删除的内容保留 30 天，到期后彻底删除。恢复时，如果原文件夹已不存在，会恢复到根目录。
        </p>
      </div>
      {items.length === 0 ? (
        <p className="rounded-xl border border-dashed border-input px-4 py-10 text-center text-muted-foreground">
          回收站是空的
        </p>
      ) : (
        <ul className="divide-y divide-border-subtle rounded-lg border border-border-subtle">
          {items.map((item) => {
            const Icon = item.type === 'FOLDER' ? FolderIcon : FileTextIcon
            return (
              <li key={item.id} className="flex items-center gap-3 px-4 py-3">
                <Icon className="size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium">
                    {item.title}
                    {item.descendantCount > 0 && (
                      <span className="ml-2 font-normal text-muted-foreground">
                        含 {item.descendantCount} 项
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {item.deletedByName} 删除于 {dateFormat.format(new Date(item.deletedAt))}，
                    {dateFormat.format(new Date(item.purgeAt))} 彻底删除
                  </div>
                </div>
                {canEdit && (
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={restore.isPending}
                    onClick={() => {
                      restore.mutate({ workspaceId, nodeId: item.id })
                    }}
                  >
                    恢复
                  </Button>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
