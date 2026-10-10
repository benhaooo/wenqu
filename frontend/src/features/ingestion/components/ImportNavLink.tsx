import { UploadIcon } from 'lucide-react'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { SidebarLink } from '@/shared/ui/sidebar-link'
import { ImportNotifier } from './ImportNotifier'

/** 侧栏导航中的「导入文件」，同时负责导入完成的全局通知。 */
export function ImportNavLink() {
  const { workspaceId } = useWorkspaceAccess()
  return (
    <>
      <SidebarLink to={`/w/${String(workspaceId)}/import`} icon={UploadIcon}>
        导入文件
      </SidebarLink>
      <ImportNotifier workspaceId={workspaceId} />
    </>
  )
}
