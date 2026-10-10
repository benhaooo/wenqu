import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import type { JobStatus } from '@/shared/api/generated/model'
import { useImports } from '../hooks/useImports'
import { isUnfinished } from '../jobStatus'

/** 导入任务在本页面打开期间完成或失败时提示，离开导入页也能收到。 */
export function ImportNotifier({ workspaceId }: { workspaceId: number }) {
  const navigate = useNavigate()
  const { data: jobs } = useImports(workspaceId)
  const previous = useRef<Map<number, JobStatus> | null>(null)

  useEffect(() => {
    if (!jobs) return
    const known = previous.current
    previous.current = new Map(jobs.map((job) => [job.id, job.status]))
    // 首次加载时已经结束的任务不提示
    if (!known) return
    for (const job of jobs) {
      const before = known.get(job.id)
      if (before === undefined || !isUnfinished(before) || isUnfinished(job.status)) continue
      if (job.status === 'SUCCEEDED') {
        toast.success(`${job.fileName} 已可用于问答`, {
          action: {
            label: '去提问',
            onClick: () => void navigate(`/w/${String(workspaceId)}/chat`),
          },
        })
      } else {
        toast.error(`${job.fileName} 导入失败`, { description: job.errorMessage })
      }
    }
  }, [jobs, navigate, workspaceId])

  return null
}
