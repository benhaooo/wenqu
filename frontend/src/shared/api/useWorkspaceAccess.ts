import { useParams } from 'react-router'
import { useGetWorkspace } from './generated/workspace/workspace'

/** 当前路由中的空间 id 及我的角色。按钮的显隐只是体验优化，权限以后端为准。 */
export function useWorkspaceAccess() {
  const workspaceId = Number(useParams().workspaceId)
  const { data: role } = useGetWorkspace(workspaceId, {
    query: { select: (result) => result.data.myRole },
  })
  return { workspaceId, canEdit: role === 'ADMIN' || role === 'EDITOR', isAdmin: role === 'ADMIN' }
}
