import { CheckIcon, CloudOffIcon, LoaderIcon, TriangleAlertIcon } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import type { SaveStatus } from '../editor/autoSaver'

/** 顶部保存状态：保存中、已保存、保存失败（自动重试）、内容冲突。 */
export function SaveStatusBadge({ status, onRetry }: { status: SaveStatus; onRetry: () => void }) {
  switch (status) {
    case 'saved':
      return (
        <span className="flex items-center gap-1 text-xs text-muted-foreground">
          <CheckIcon className="size-3.5" />
          已保存
        </span>
      )
    case 'dirty':
    case 'saving':
      return (
        <span className="flex items-center gap-1 text-xs text-muted-foreground">
          <LoaderIcon className="size-3.5 animate-spin" />
          保存中
        </span>
      )
    case 'error':
      return (
        <span className="flex items-center gap-1 text-xs text-destructive" role="status">
          <CloudOffIcon className="size-3.5" />
          保存失败，正在重试
          <Button variant="link" size="sm" className="h-auto p-0 text-xs" onClick={onRetry}>
            立即重试
          </Button>
        </span>
      )
    case 'conflict':
      return (
        <span className="flex items-center gap-1 text-xs text-destructive" role="alert">
          <TriangleAlertIcon className="size-3.5" />
          文档已被其他人修改，你的修改未保存
          <Button
            variant="link"
            size="sm"
            className="h-auto p-0 text-xs"
            onClick={() => {
              window.location.reload()
            }}
          >
            刷新获取最新内容
          </Button>
        </span>
      )
  }
}
