import { EditorContent } from '@tiptap/react'
import { Fragment, useState } from 'react'
import { useParams } from 'react-router'
import { useGetDocument } from '@/shared/api/generated/document/document'
import type { DocumentResponse } from '@/shared/api/generated/model'
import { Skeleton } from '@/shared/ui/skeleton'
import { useDocumentEditor } from '../editor/useDocumentEditor'
import { useNodeIndex } from '../hooks/useNodeIndex'
import { useNodeMutations } from '../hooks/useNodeMutations'
import { useWorkspaceAccess } from '../hooks/useWorkspaceAccess'
import { pathTo } from '../tree'
import { SaveStatusBadge } from './SaveStatusBadge'

export function DocumentPage() {
  const { workspaceId, canEdit } = useWorkspaceAccess()
  const nodeId = Number(useParams().nodeId)
  const { data: document, error } = useGetDocument(workspaceId, nodeId, {
    // 编辑过程中不自动重新获取，避免覆盖本地编辑
    query: { select: (result) => result.data, staleTime: Infinity, refetchOnWindowFocus: false },
  })

  if (error) return <p className="text-muted-foreground">{error.message}</p>
  if (!document) {
    return (
      <div className="mx-auto grid max-w-3xl gap-4">
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-4" />
        <Skeleton className="h-4 w-5/6" />
      </div>
    )
  }
  return (
    <DocumentView key={nodeId} workspaceId={workspaceId} document={document} canEdit={canEdit} />
  )
}

function DocumentView({
  workspaceId,
  document,
  canEdit,
}: {
  workspaceId: number
  document: DocumentResponse
  canEdit: boolean
}) {
  const { index } = useNodeIndex(workspaceId)
  const folders = pathTo(index, document.id).slice(0, -1)
  const { editor, status, retry } = useDocumentEditor({
    workspaceId,
    nodeId: document.id,
    content: document.content,
    version: document.version,
    editable: canEdit,
  })

  return (
    <article className="mx-auto max-w-3xl">
      <div className="flex min-h-6 items-center justify-between gap-4">
        <nav className="flex flex-wrap gap-1 text-sm text-muted-foreground" aria-label="路径">
          {folders.map((folder) => (
            <Fragment key={folder.id}>
              <span>{folder.title}</span>
              <span>/</span>
            </Fragment>
          ))}
        </nav>
        {canEdit ? (
          <SaveStatusBadge status={status} onRetry={retry} />
        ) : (
          <span className="text-xs text-muted-foreground">只读</span>
        )}
      </div>
      <TitleInput workspaceId={workspaceId} document={document} editable={canEdit} />
      <EditorContent editor={editor} className="mt-6" />
    </article>
  )
}

function TitleInput({
  workspaceId,
  document,
  editable,
}: {
  workspaceId: number
  document: DocumentResponse
  editable: boolean
}) {
  const [title, setTitle] = useState(document.title)
  const mutations = useNodeMutations(workspaceId)

  if (!editable) return <h1 className="mt-2 text-3xl font-bold">{document.title}</h1>

  const commit = () => {
    const next = title.trim()
    if (!next) {
      setTitle(document.title)
    } else if (next !== document.title) {
      mutations.rename(document.id, next)
    }
  }
  return (
    <input
      value={title}
      aria-label="文档标题"
      maxLength={200}
      placeholder="无标题文档"
      className="mt-2 w-full bg-transparent text-3xl font-bold outline-none placeholder:text-muted-foreground"
      onChange={(event) => {
        setTitle(event.target.value)
      }}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          event.preventDefault()
          event.currentTarget.blur()
        }
      }}
    />
  )
}
