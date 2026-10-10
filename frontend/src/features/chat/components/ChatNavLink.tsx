import { MessageCircleIcon } from 'lucide-react'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { SidebarLink } from '@/shared/ui/sidebar-link'

/** 侧栏导航中的「问 AI」。 */
export function ChatNavLink() {
  const { workspaceId } = useWorkspaceAccess()
  return (
    <SidebarLink to={`/w/${String(workspaceId)}/chat`} icon={MessageCircleIcon}>
      问 AI
    </SidebarLink>
  )
}
