import { EditorContent } from '@tiptap/react'
import { MoreHorizontalIcon, Trash2Icon } from 'lucide-react'
import { Fragment, useEffect, useState, type ReactNode } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { useGetDocument } from '@/shared/api/generated/document/document'
import type { DocumentResponse } from '@/shared/api/generated/model'
import { relativeTime } from '@/shared/lib/relativeTime'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { Skeleton } from '@/shared/ui/skeleton'
import { parseBlockRange } from '../editor/extensions/BlockHighlight'
import { useDocumentEditor } from '../editor/useDocumentEditor'
import { useNodeIndex } from '../hooks/useNodeIndex'
import { useNodeMutations } from '../hooks/useNodeMutations'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { pathTo } from '../tree'
import { SaveStatusBadge } from './SaveStatusBadge'

/**
 * 文档页。顶栏右侧的附加操作（问 AI、下载原文件）属于其他模块，由应用层通过 headerActions 传入。
 * 链接带 blocks=3-5 时高亮对应段落（AI 回答引用跳转）。
 */
export function DocumentPage({
  headerActions,
}: {
  headerActions?: (documentId: number) => ReactNode
}) {
  const { workspaceId, canEdit } = useWorkspaceAccess()
  const nodeId = Number(useParams().nodeId)
  const { data: document, error } = useGetDocument(workspaceId, nodeId, {
    // 编辑过程中不自动重新获取，避免覆盖本地编辑
    query: { select: (result) => result.data, staleTime: Infinity, refetchOnWindowFocus: false },
  })

  if (error) return <p className="px-8 py-12 text-muted-foreground">{error.message}</p>
  if (!document) {
    return (
      <div className="mx-auto grid max-w-[760px] gap-4 px-8 pt-20">
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-4" />
        <Skeleton className="h-4 w-5/6" />
      </div>
    )
  }
  return (
    <DocumentView
      key={nodeId}
      workspaceId={workspaceId}
      document={document}
      canEdit={canEdit}
      headerActions={headerActions?.(nodeId)}
    />
  )
}

function DocumentView({
  workspaceId,
  document,
  canEdit,
  headerActions,
}: {
  workspaceId: number
  document: DocumentResponse
  canEdit: boolean
  headerActions: ReactNode
}) {
  const navigate = useNavigate()
  const { index } = useNodeIndex(workspaceId)
  const mutations = useNodeMutations(workspaceId)
  const path = pathTo(index, document.id)
  const { editor, status, retry } = useDocumentEditor({
    workspaceId,
    nodeId: document.id,
    content: document.content,
    version: document.version,
    editable: canEdit,
  })
  const highlight = parseBlockRange(useSearchParams()[0].get('blocks'))
  const highlightFrom = highlight?.from
  const highlightTo = highlight?.to

  useEffect(() => {
    if (highlightFrom === undefined || highlightTo === undefined) return
    editor.commands.highlightBlocks(highlightFrom, highlightTo)
    const first = editor.view.dom.children.item(highlightFrom)
    first?.scrollIntoView({ block: 'center' })
  }, [editor, highlightFrom, highlightTo])

  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-10 flex min-h-14 flex-wrap items-center gap-3 border-b border-border-subtle bg-background px-6 py-2.5">
        <nav
          aria-label="路径"
          className="flex min-w-0 flex-1 items-center gap-1.5 text-muted-foreground"
        >
          {path.map((node, i) => (
            <Fragment key={node.id}>
              {i > 0 && <span aria-hidden="true">/</span>}
              <span
                className={
                  i === path.length - 1 ? 'truncate font-medium text-foreground' : 'truncate'
                }
              >
                {node.title}
              </span>
            </Fragment>
          ))}
        </nav>
        {headerActions}
        {canEdit ? (
          <SaveStatusBadge status={status} onRetry={retry} />
        ) : (
          <span className="text-[13px] text-muted-foreground">只读</span>
        )}
        {canEdit && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="outline"
                size="icon"
                aria-label="更多操作"
                className="text-muted-foreground"
              >
                <MoreHorizontalIcon />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                variant="destructive"
                onSelect={() => {
                  mutations.remove(document.id)
                  void navigate(`/w/${String(workspaceId)}`)
                }}
              >
                <Trash2Icon />
                移到回收站
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </header>
      <article className="mx-auto w-full max-w-[760px] px-8 pt-12 pb-[120px] max-sm:px-4">
        <TitleInput workspaceId={workspaceId} document={document} editable={canEdit} />
        <p className="mt-2 mb-8 text-[13px] text-muted-foreground">
          {document.updatedByName} · 编辑于 {relativeTime(document.updatedAt)}
        </p>
        <EditorContent editor={editor} />
      </article>
    </div>
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
  const className = 'w-full bg-transparent text-[32px] leading-[1.3] font-bold outline-none'

  if (!editable) return <h1 className={className}>{document.title}</h1>

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
      className={`${className} placeholder:text-muted-foreground`}
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
