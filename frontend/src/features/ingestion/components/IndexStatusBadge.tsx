import { CircleAlertIcon, LoaderCircleIcon } from 'lucide-react'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { useIndexStatus } from '../hooks/useIndexStatus'

/** 目录树中文档的索引状态：处理中显示转圈，失败显示警示图标；已完成不显示。 */
export function IndexStatusBadge({ documentId }: { documentId: number }) {
  const { workspaceId } = useWorkspaceAccess()
  const { data: statuses } = useIndexStatus(workspaceId)
  const status = statuses?.get(documentId)
  if (!status) return null
  if (status.status === 'FAILED') {
    const label = `索引失败：${status.errorMessage ?? '未知原因'}`
    return (
      <span title={label} className="shrink-0 text-destructive">
        <CircleAlertIcon className="size-3.5" aria-hidden="true" />
        <span className="sr-only">{label}</span>
      </span>
    )
  }
  return (
    <span title="正在建立索引" className="shrink-0 text-muted-foreground">
      <LoaderCircleIcon className="size-3.5 animate-spin" aria-hidden="true" />
      <span className="sr-only">正在建立索引</span>
    </span>
  )
}
