import { useListIndexStatus } from '@/shared/api/generated/ingestion/ingestion'
import type { IndexStatusResponse } from '@/shared/api/generated/model'

const ACTIVE_INTERVAL = 3000
/** 编辑后 30 秒才开始重新索引，空闲时低频刷新以便显示新任务。 */
const IDLE_INTERVAL = 30_000

/** 索引未完成的文档：documentId → 最近一次任务的状态。 */
export function useIndexStatus(workspaceId: number) {
  return useListIndexStatus(workspaceId, {
    query: {
      select: (result) => new Map(result.data.map((item) => [item.documentId, item])),
      refetchInterval: (query) =>
        query.state.data?.data.some((item: IndexStatusResponse) => item.status !== 'FAILED')
          ? ACTIVE_INTERVAL
          : IDLE_INTERVAL,
    },
  })
}
