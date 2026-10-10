import type { ImportResponse, JobStatus } from '@/shared/api/generated/model'

export type JobTone = 'muted' | 'progress' | 'success' | 'error'

export interface JobDescription {
  label: string
  tone: JobTone
  /** 向量化进度 0～1，只有向量化中且已知总数时有值 */
  progress?: number
  detail?: string
}

const UNFINISHED: JobStatus[] = ['QUEUED', 'PARSING', 'EMBEDDING']

export function isUnfinished(status: JobStatus): boolean {
  return UNFINISHED.includes(status)
}

/** 导入任务在列表中的状态文字。 */
export function describeJob(job: ImportResponse): JobDescription {
  switch (job.status) {
    case 'QUEUED':
      if (job.attempts > 0) {
        return {
          label: `等待重试 · 已失败 ${String(job.attempts)} 次`,
          tone: 'muted',
          detail: job.errorMessage,
        }
      }
      return {
        label: job.queuedAhead > 0 ? `排队中 · 前面还有 ${String(job.queuedAhead)} 个` : '排队中',
        tone: 'muted',
      }
    case 'PARSING':
      return { label: '解析中', tone: 'progress' }
    case 'EMBEDDING':
      if (job.chunkCount === undefined || job.chunkCount === 0) {
        return { label: '向量化中', tone: 'progress' }
      }
      return {
        label: `向量化中 · ${String(job.embeddedCount)} / ${String(job.chunkCount)} 块`,
        tone: 'progress',
        progress: job.embeddedCount / job.chunkCount,
      }
    case 'SUCCEEDED':
      return { label: `已完成 · ${String(job.chunkCount ?? 0)} 块`, tone: 'success' }
    case 'FAILED':
      return {
        label: job.attempts > 1 ? `失败（已重试 ${String(job.attempts - 1)} 次）` : '失败',
        tone: 'error',
        detail: job.errorMessage,
      }
  }
}

/** 文件类型角标，取扩展名。 */
export function fileKind(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  const extension = dot >= 0 ? fileName.slice(dot + 1).toUpperCase() : ''
  return extension === 'MARKDOWN' ? 'MD' : extension || 'FILE'
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${String(bytes)} B`
  if (bytes < 1024 * 1024) return `${String(Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
