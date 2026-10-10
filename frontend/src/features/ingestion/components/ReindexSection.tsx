import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  getListIndexStatusQueryKey,
  useReindexWorkspace,
} from '@/shared/api/generated/ingestion/ingestion'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { Button } from '@/shared/ui/button'

/** 设置页中的「重新索引」：更换 Embedding 模型或切块参数后使用，仅管理员可见。 */
export function ReindexSection() {
  const { workspaceId, isAdmin } = useWorkspaceAccess()
  const queryClient = useQueryClient()
  const reindex = useReindexWorkspace({
    mutation: {
      onSuccess: (result) => {
        toast.success(`已加入索引队列：${String(result.data.queued)} 篇文档`)
        void queryClient.invalidateQueries({ queryKey: getListIndexStatusQueryKey(workspaceId) })
      },
      onError: (error) => toast.error(error.message),
    },
  })
  if (!isAdmin) return null
  return (
    <section className="flex flex-col gap-3 rounded-[10px] border border-border-subtle bg-surface-subtle p-5">
      <h2 className="text-base font-semibold">AI 索引</h2>
      <p className="text-muted-foreground">
        更换 Embedding
        模型或切块参数后，需要重新索引全部文档，问答才会使用新的结果。索引在后台进行，期间问答仍可使用。
      </p>
      <Button
        variant="outline"
        className="self-start"
        disabled={reindex.isPending}
        onClick={() => {
          reindex.mutate({ workspaceId })
        }}
      >
        重新索引全部文档
      </Button>
    </section>
  )
}
