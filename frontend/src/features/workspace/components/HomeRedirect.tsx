import { Navigate } from 'react-router'
import { useListMyWorkspaces } from '@/shared/api/generated/workspace/workspace'
import { lastWorkspace } from '../lastWorkspace'

/** 首页：进入上次访问的空间；没有空间时引导创建。 */
export function HomeRedirect() {
  const { data: workspaces } = useListMyWorkspaces({ query: { select: (result) => result.data } })

  if (!workspaces) return null
  if (workspaces.length === 0) return <Navigate to="/workspaces/new" replace />

  const last = lastWorkspace.get()
  const target = workspaces.find((workspace) => workspace.id === last) ?? workspaces[0]
  return <Navigate to={`/w/${String(target?.id)}`} replace />
}
