import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react'
import { ImageOffIcon } from 'lucide-react'
import { useGetImageUrl } from '@/shared/api/generated/document/document'
import { cn } from '@/shared/lib/cn'
import type { WorkspaceImageOptions } from './WorkspaceImage'

const URL_STALE_MS = 50 * 60 * 1000

export function ImageView({ node, extension, selected }: NodeViewProps) {
  const { workspaceId } = extension.options as WorkspaceImageOptions
  const imageId = node.attrs.imageId as string
  const { data: url, isError } = useGetImageUrl(workspaceId, imageId, {
    // 签名链接 1 小时有效，提前刷新
    query: { staleTime: URL_STALE_MS, refetchInterval: URL_STALE_MS, select: (r) => r.data.url },
  })

  return (
    <NodeViewWrapper className="my-4" data-drag-handle>
      {isError ? (
        <div className="flex h-24 items-center justify-center gap-2 rounded-md border border-dashed text-sm text-muted-foreground">
          <ImageOffIcon className="size-4" />
          图片无法加载
        </div>
      ) : url ? (
        <img
          src={url}
          alt={(node.attrs.alt as string | null) ?? ''}
          className={cn('max-w-full rounded-md', selected && 'ring-2 ring-ring')}
        />
      ) : (
        <div className="h-24 animate-pulse rounded-md bg-muted" />
      )}
    </NodeViewWrapper>
  )
}
