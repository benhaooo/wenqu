import { useMatch } from 'react-router'
import { ChatSidebar } from '@/features/chat'
import { SidebarTree } from '@/features/document'
import { IndexStatusBadge } from '@/features/ingestion'

/** 侧栏下半部分：问答页显示最近会话，其他页面显示带索引状态的目录树。 */
export function SidebarSecondary() {
  const inChat = useMatch('/w/:workspaceId/chat/*')
  if (inChat) return <ChatSidebar />
  return <SidebarTree renderBadge={(documentId) => <IndexStatusBadge documentId={documentId} />} />
}
