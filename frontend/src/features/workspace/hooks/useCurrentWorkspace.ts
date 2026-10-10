import { useGetWorkspace } from '@/shared/api/generated/workspace/workspace'
import { useWorkspaceId } from './useWorkspaceId'

/** 当前路由中的空间及我在其中的角色。 */
export function useCurrentWorkspace() {
  const workspaceId = useWorkspaceId()
  return useGetWorkspace(workspaceId, { query: { select: (result) => result.data } })
}
