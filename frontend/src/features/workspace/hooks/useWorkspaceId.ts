import { useParams } from 'react-router'

export function useWorkspaceId(): number {
  const { workspaceId } = useParams()
  return Number(workspaceId)
}
