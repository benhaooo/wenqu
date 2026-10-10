import { Fragment } from 'react'
import { useParams } from 'react-router'
import { useNodeIndex } from '../hooks/useNodeIndex'
import { useWorkspaceAccess } from '../hooks/useWorkspaceAccess'
import { pathTo } from '../tree'

export function DocumentPage() {
  const { workspaceId } = useWorkspaceAccess()
  const nodeId = Number(useParams().nodeId)
  const { index, isPending } = useNodeIndex(workspaceId)
  const path = pathTo(index, nodeId)
  const document = path.at(-1)

  if (isPending) return null
  if (!document) {
    return <p className="text-muted-foreground">文档不存在或已被删除</p>
  }
  return (
    <article className="mx-auto max-w-3xl">
      <nav className="flex flex-wrap gap-1 text-sm text-muted-foreground" aria-label="路径">
        {path.slice(0, -1).map((folder) => (
          <Fragment key={folder.id}>
            <span>{folder.title}</span>
            <span>/</span>
          </Fragment>
        ))}
      </nav>
      <h1 className="mt-2 text-3xl font-bold">{document.title}</h1>
    </article>
  )
}
