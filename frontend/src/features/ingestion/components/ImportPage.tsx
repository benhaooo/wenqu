import { useQueryClient } from '@tanstack/react-query'
import { CheckIcon, UploadIcon } from 'lucide-react'
import { useState, type DragEvent } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { getListNodesQueryKey, useListNodes } from '@/shared/api/generated/document/document'
import {
  getListImportsQueryKey,
  getListIndexStatusQueryKey,
  useImportFile,
  useRetryIngestJob,
} from '@/shared/api/generated/ingestion/ingestion'
import type { ImportResponse, NodeResponse } from '@/shared/api/generated/model'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import { NativeSelect } from '@/shared/ui/native-select'
import { Skeleton } from '@/shared/ui/skeleton'
import { useImports } from '../hooks/useImports'
import { describeJob, fileKind, formatSize, type JobTone } from '../jobStatus'

const MAX_SIZE = 20 * 1024 * 1024
const ACCEPT = '.pdf,.docx,.doc,.md,.markdown,.txt'

/** 导入文件：选择目标文件夹，拖放或选择文件上传，查看解析与索引进度。 */
export function ImportPage() {
  const { workspaceId, canEdit } = useWorkspaceAccess()
  const queryClient = useQueryClient()
  const { data: jobs } = useImports(workspaceId)
  const [parentId, setParentId] = useState('')
  const [dragging, setDragging] = useState(false)
  const upload = useImportFile()

  async function importFiles(files: File[]) {
    for (const file of files) {
      if (file.size > MAX_SIZE) {
        toast.error(`${file.name} 超过 20MB，未导入`)
        continue
      }
      try {
        await upload.mutateAsync({
          workspaceId,
          data: { file },
          params: parentId ? { parentId: Number(parentId) } : undefined,
        })
      } catch (error) {
        toast.error(`${file.name}：${error instanceof Error ? error.message : '导入失败'}`)
      }
      void queryClient.invalidateQueries({ queryKey: getListImportsQueryKey(workspaceId) })
      void queryClient.invalidateQueries({ queryKey: getListNodesQueryKey(workspaceId) })
      void queryClient.invalidateQueries({ queryKey: getListIndexStatusQueryKey(workspaceId) })
    }
  }

  function onDrop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    if (canEdit) void importFiles([...event.dataTransfer.files])
  }

  return (
    <div className="mx-auto flex max-w-[960px] flex-col gap-6 px-8 pt-10 pb-20 max-sm:px-4">
      <div className="flex flex-wrap items-end gap-4">
        <div className="grow">
          <h1 className="mb-1 text-[26px] font-bold">导入文件</h1>
          <p className="text-muted-foreground">
            导入后自动解析并建立索引，完成后即可在问答中引用。
          </p>
        </div>
        {canEdit && (
          <FolderSelect workspaceId={workspaceId} value={parentId} onChange={setParentId} />
        )}
      </div>

      {canEdit ? (
        <label
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => {
            setDragging(false)
          }}
          onDrop={onDrop}
          className={cn(
            'relative flex cursor-pointer flex-col items-center gap-2.5 rounded-xl border-[1.5px] border-dashed border-dropzone-border bg-dropzone px-6 py-10 text-center focus-within:ring-3 focus-within:ring-primary-soft',
            dragging && 'border-primary bg-accent',
          )}
        >
          <span className="flex size-12 items-center justify-center rounded-xl bg-accent text-primary">
            <UploadIcon className="size-6" aria-hidden="true" />
          </span>
          <span className="text-[15px] font-semibold">
            {upload.isPending ? '正在上传…' : '拖拽文件到这里，或点击选择'}
          </span>
          <span className="text-[13px] text-muted-foreground">
            支持 PDF、Word、Markdown、纯文本 · 单个文件不超过 20MB
          </span>
          <input
            type="file"
            multiple
            accept={ACCEPT}
            className="absolute size-px opacity-0"
            onChange={(event) => {
              const files = [...(event.target.files ?? [])]
              event.target.value = ''
              void importFiles(files)
            }}
          />
        </label>
      ) : (
        <p className="rounded-xl border border-border-subtle bg-surface-subtle px-6 py-5 text-muted-foreground">
          只读成员不能导入文件，可以查看导入进度。
        </p>
      )}

      <section aria-label="导入任务" className="flex flex-col gap-2.5">
        <div className="flex items-center gap-3">
          <h2 className="grow text-base font-semibold">处理进度</h2>
          <span className="text-[13px] text-muted-foreground">
            可以离开此页面，处理完成后会通知你
          </span>
        </div>
        {jobs === undefined ? (
          <Skeleton className="h-24" />
        ) : jobs.length === 0 ? (
          <p className="rounded-[10px] border border-border-subtle px-4 py-6 text-center text-muted-foreground">
            还没有导入过文件
          </p>
        ) : (
          <div className="overflow-x-auto rounded-[10px] border border-border-subtle">
            <table className="w-full min-w-[640px] border-collapse">
              <thead>
                <tr className="bg-canvas text-left text-[13px] text-muted-foreground">
                  <th scope="col" className="px-4 py-2.5 font-medium">
                    文件
                  </th>
                  <th scope="col" className="w-[90px] px-4 py-2.5 font-medium">
                    大小
                  </th>
                  <th scope="col" className="w-[280px] px-4 py-2.5 font-medium">
                    状态
                  </th>
                  <th scope="col" className="w-[100px] px-4 py-2.5 font-medium">
                    <span className="sr-only">操作</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <JobRow key={job.id} workspaceId={workspaceId} job={job} canEdit={canEdit} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

function FolderSelect({
  workspaceId,
  value,
  onChange,
}: {
  workspaceId: number
  value: string
  onChange: (value: string) => void
}) {
  const { data: nodes } = useListNodes(workspaceId, { query: { select: (result) => result.data } })
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor="import-target" className="text-[13px] text-muted-foreground">
        导入到
      </label>
      <NativeSelect
        id="import-target"
        value={value}
        onChange={(event) => {
          onChange(event.target.value)
        }}
        className="h-10 min-w-[200px]"
      >
        <option value="">根目录</option>
        {folderOptions(nodes ?? []).map((folder) => (
          <option key={folder.id} value={folder.id}>
            {folder.label}
          </option>
        ))}
      </NativeSelect>
    </div>
  )
}

/** 文件夹按路径显示，如「项目文档 / 接口」。 */
function folderOptions(nodes: NodeResponse[]): { id: number; label: string }[] {
  const byId = new Map(nodes.map((node) => [node.id, node]))
  const label = (node: NodeResponse): string => {
    const parent = node.parentId == null ? undefined : byId.get(node.parentId)
    return parent ? `${label(parent)} / ${node.title}` : node.title
  }
  return nodes
    .filter((node) => node.type === 'FOLDER')
    .map((node) => ({ id: node.id, label: label(node) }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'))
}

const toneClass: Record<JobTone, string> = {
  muted: 'text-muted-foreground',
  progress: 'font-medium text-primary-strong',
  success: 'font-medium text-primary-strong',
  error: 'font-semibold text-destructive',
}

function JobRow({
  workspaceId,
  job,
  canEdit,
}: {
  workspaceId: number
  job: ImportResponse
  canEdit: boolean
}) {
  const queryClient = useQueryClient()
  const retry = useRetryIngestJob({
    mutation: {
      onSuccess: () =>
        queryClient.invalidateQueries({ queryKey: getListImportsQueryKey(workspaceId) }),
      onError: (error) => toast.error(error.message),
    },
  })
  const description = describeJob(job)
  const kind = fileKind(job.fileName)

  return (
    <tr className="border-t border-border-subtle">
      <td className="px-4 py-3">
        <span className="flex items-center gap-2.5">
          <span
            className={cn(
              'rounded-[4px] px-1.5 py-px text-[11px] font-semibold',
              kind === 'PDF'
                ? 'bg-destructive-soft text-destructive-strong'
                : kind.startsWith('DOC')
                  ? 'bg-file-word text-file-word-foreground'
                  : 'bg-secondary text-text-secondary',
            )}
          >
            {kind}
          </span>
          <span className="break-all">{job.fileName}</span>
        </span>
      </td>
      <td className="px-4 py-3 text-muted-foreground">{formatSize(job.fileSize)}</td>
      <td className="px-4 py-3">
        <span className="flex flex-col gap-1.5">
          <span
            className={cn('flex items-center gap-1.5 text-[13px]', toneClass[description.tone])}
          >
            {description.tone === 'success' && <CheckIcon className="size-4" aria-hidden="true" />}
            {description.label}
          </span>
          {description.progress !== undefined && (
            <span
              role="progressbar"
              aria-valuenow={Math.round(description.progress * 100)}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label={`${job.fileName} 向量化进度`}
              className="h-1.5 overflow-hidden rounded-full bg-border-subtle"
            >
              <span
                className="block h-1.5 bg-primary transition-[width]"
                style={{ width: `${String(Math.round(description.progress * 100))}%` }}
              />
            </span>
          )}
          {description.detail && (
            <span className="text-xs text-muted-foreground">{description.detail}</span>
          )}
        </span>
      </td>
      <td className="px-4 py-3">
        {job.status === 'SUCCEEDED' && (
          <Link
            to={`/w/${String(workspaceId)}/d/${String(job.documentId)}`}
            className="text-[13px] text-primary hover:text-primary-strong"
          >
            打开
          </Link>
        )}
        {job.status === 'FAILED' && canEdit && (
          <Button
            variant="outline"
            size="sm"
            disabled={retry.isPending}
            onClick={() => {
              retry.mutate({ workspaceId, jobId: job.id })
            }}
          >
            重试
          </Button>
        )}
      </td>
    </tr>
  )
}
