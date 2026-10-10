import { FileTextIcon } from 'lucide-react'
import { Link } from 'react-router'
import { useGetWorkspace } from '@/shared/api/generated/workspace/workspace'
import { relativeTime } from '@/shared/lib/relativeTime'
import { useNodeIndex } from '../hooks/useNodeIndex'
import { useWorkspaceAccess } from '../hooks/useWorkspaceAccess'
import { pathTo } from '../tree'

const RECENT_LIMIT = 10

/** 空间首页：最近编辑的文档。 */
export function DocumentHomePage() {
  const { workspaceId } = useWorkspaceAccess()
  const { data: workspace } = useGetWorkspace(workspaceId, {
    query: { select: (result) => result.data },
  })
  const { index, isPending } = useNodeIndex(workspaceId)
  const recent = [...index.nodes.values()]
    .filter((node) => node.type === 'DOCUMENT')
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, RECENT_LIMIT)

  return (
    <div className="mx-auto flex max-w-[760px] flex-col gap-8 px-8 pt-12 pb-20 max-sm:px-4">
      <h1 className="text-[32px] leading-tight font-bold">{workspace?.name}</h1>
      <section aria-labelledby="recent-title" className="flex flex-col gap-3">
        <h2 id="recent-title" className="text-base font-semibold">
          最近编辑
        </h2>
        {!isPending && recent.length === 0 ? (
          <p className="rounded-xl border border-dashed border-input px-4 py-10 text-center text-muted-foreground">
            还没有文档。在左侧「文档」旁点击 + 新建第一篇文档。
          </p>
        ) : (
          <ul className="divide-y divide-border-subtle overflow-hidden rounded-xl border border-border-subtle">
            {recent.map((node) => {
              const folders = pathTo(index, node.id).slice(0, -1)
              return (
                <li key={node.id}>
                  <Link
                    to={`/w/${String(workspaceId)}/d/${String(node.id)}`}
                    className="flex items-center gap-3 px-4 py-3 hover:bg-canvas"
                  >
                    <FileTextIcon
                      className="size-4 shrink-0 text-muted-foreground"
                      aria-hidden="true"
                    />
                    <span className="flex min-w-0 flex-1 flex-col leading-snug">
                      <span className="truncate font-medium">{node.title}</span>
                      {folders.length > 0 && (
                        <span className="truncate text-xs text-muted-foreground">
                          {folders.map((folder) => folder.title).join(' / ')}
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 text-[13px] text-muted-foreground">
                      {relativeTime(node.updatedAt)}
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}
