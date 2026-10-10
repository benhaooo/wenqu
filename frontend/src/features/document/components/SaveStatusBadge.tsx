import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/cn'
import type { SaveStatus } from '../editor/autoSaver'

const labels: Record<SaveStatus, string> = {
  saved: '已保存',
  dirty: '保存中',
  saving: '保存中',
  error: '保存失败，正在重试',
  conflict: '文档已被其他人修改，你的修改未保存',
}

/** 顶部保存状态：保存中、已保存、保存失败（自动重试）、内容冲突。 */
export function SaveStatusBadge({ status, onRetry }: { status: SaveStatus; onRetry: () => void }) {
  const failed = status === 'error' || status === 'conflict'
  return (
    <span
      role={failed ? 'alert' : 'status'}
      className={cn(
        'flex items-center gap-1.5 text-[13px] text-muted-foreground',
        failed && 'text-destructive',
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'size-[7px] rounded-full bg-primary',
          (status === 'dirty' || status === 'saving') && 'animate-pulse bg-muted-foreground',
          failed && 'bg-destructive',
        )}
      />
      {labels[status]}
      {status === 'error' && (
        <Button variant="link" className="h-auto p-0 text-[13px]" onClick={onRetry}>
          立即重试
        </Button>
      )}
      {status === 'conflict' && (
        <Button
          variant="link"
          className="h-auto p-0 text-[13px]"
          onClick={() => {
            window.location.reload()
          }}
        >
          刷新获取最新内容
        </Button>
      )}
    </span>
  )
}
