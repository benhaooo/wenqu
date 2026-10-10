import { useListImports } from '@/shared/api/generated/ingestion/ingestion'
import { isUnfinished } from '../jobStatus'

/** 导入列表，有未完成的任务时每 2 秒刷新一次。 */
export function useImports(workspaceId: number) {
  return useListImports(workspaceId, {
    query: {
      select: (result) => result.data,
      refetchInterval: (query) =>
        query.state.data?.data.some((job) => isUnfinished(job.status)) ? 2000 : false,
    },
  })
}
