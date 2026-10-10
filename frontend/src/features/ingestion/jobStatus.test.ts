import { describe, expect, it } from 'vitest'
import type { ImportResponse } from '@/shared/api/generated/model'
import { describeJob, fileKind, formatSize } from './jobStatus'

const job = (overrides: Partial<ImportResponse>): ImportResponse => ({
  id: 1,
  documentId: 2,
  fileName: 'a.pdf',
  fileSize: 1,
  status: 'QUEUED',
  embeddedCount: 0,
  attempts: 0,
  queuedAhead: 0,
  createdAt: '2026-10-10T00:00:00Z',
  ...overrides,
})

describe('describeJob', () => {
  it('shows the queue position', () => {
    expect(describeJob(job({ queuedAhead: 2 })).label).toBe('排队中 · 前面还有 2 个')
    expect(describeJob(job({})).label).toBe('排队中')
  })

  it('distinguishes waiting for a retry from waiting in the queue', () => {
    expect(describeJob(job({ attempts: 1, errorMessage: '超时' }))).toEqual({
      label: '等待重试 · 已失败 1 次',
      tone: 'muted',
      detail: '超时',
    })
  })

  it('reports embedding progress', () => {
    expect(describeJob(job({ status: 'EMBEDDING', chunkCount: 180, embeddedCount: 126 }))).toEqual({
      label: '向量化中 · 126 / 180 块',
      tone: 'progress',
      progress: 0.7,
    })
  })

  it('counts retries after the first attempt', () => {
    expect(
      describeJob(job({ status: 'FAILED', attempts: 4, errorMessage: '模型不可用' })),
    ).toMatchObject({ label: '失败（已重试 3 次）', tone: 'error', detail: '模型不可用' })
    expect(describeJob(job({ status: 'FAILED', attempts: 1 })).label).toBe('失败')
  })
})

describe('file helpers', () => {
  it('labels file kinds and sizes', () => {
    expect(fileKind('规范.markdown')).toBe('MD')
    expect(fileKind('计划.docx')).toBe('DOCX')
    expect(formatSize(18 * 1024)).toBe('18 KB')
    expect(formatSize(8.4 * 1024 * 1024)).toBe('8.4 MB')
  })
})
