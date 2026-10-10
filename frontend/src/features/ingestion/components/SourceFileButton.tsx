import { DownloadIcon } from 'lucide-react'
import { useGetSourceFile } from '@/shared/api/generated/ingestion/ingestion'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { Button } from '@/shared/ui/button'

/** 文档页顶栏的下载原文件，只有导入的文档显示。下载链接 10 分钟有效，5 分钟后重新获取。 */
export function SourceFileButton({ documentId }: { documentId: number }) {
  const { workspaceId } = useWorkspaceAccess()
  const { data: file } = useGetSourceFile(workspaceId, documentId, {
    query: { select: (result) => result.data, retry: false, staleTime: 5 * 60_000 },
  })
  if (!file) return null
  return (
    <Button
      variant="outline"
      size="icon"
      asChild
      className="text-muted-foreground"
      title={`下载原文件 ${file.fileName}`}
    >
      <a href={file.url} download={file.fileName} aria-label={`下载原文件 ${file.fileName}`}>
        <DownloadIcon />
      </a>
    </Button>
  )
}
