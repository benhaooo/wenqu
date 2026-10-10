import { useMemo } from 'react'
import { useListNodes } from '@/shared/api/generated/document/document'
import { buildIndex } from '../tree'

export function useNodeIndex(workspaceId: number) {
  const query = useListNodes(workspaceId, { query: { select: (result) => result.data } })
  const index = useMemo(() => buildIndex(query.data ?? []), [query.data])
  return { ...query, index }
}
