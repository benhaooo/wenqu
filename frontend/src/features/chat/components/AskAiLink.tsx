import { MessageCircleIcon } from 'lucide-react'
import { Link } from 'react-router'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { Button } from '@/shared/ui/button'

/** 文档页顶栏的「问 AI」：进入问答，并可选择以当前文档或其所在文件夹为提问范围。 */
export function AskAiLink({ documentId }: { documentId: number }) {
  const { workspaceId } = useWorkspaceAccess()
  return (
    <Button variant="outline" size="sm" asChild>
      <Link to={`/w/${String(workspaceId)}/chat?doc=${String(documentId)}`}>
        <MessageCircleIcon />问 AI
      </Link>
    </Button>
  )
}
