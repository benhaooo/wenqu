import { Link } from 'react-router'
import type { CitationResponse } from '@/shared/api/generated/model'
import { citationTarget } from '../citations'

/** 引用卡片：来源文档、所在标题、原文片段，以及跳转到原文并高亮。 */
export function CitationCard({
  workspaceId,
  citation,
}: {
  workspaceId: number
  citation: CitationResponse
}) {
  return (
    <div
      role="note"
      className="flex flex-col gap-2 rounded-[10px] border border-cite bg-surface-subtle px-4 py-3.5 text-sm leading-[1.7]"
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-[4px] bg-cite px-1.5 text-xs font-semibold text-cite-foreground">
          {citation.marker}
        </span>
        <span className="font-semibold">{citation.documentTitle}</span>
        {citation.headingPath && (
          <span className="text-xs text-muted-foreground">
            {withoutTitle(citation.headingPath, citation.documentTitle)}
          </span>
        )}
      </div>
      <p className="line-clamp-6 whitespace-pre-line text-text-secondary">{citation.snippet}</p>
      <Link
        to={citationTarget(workspaceId, citation)}
        className="self-start text-[13px] font-medium text-primary hover:text-primary-strong"
      >
        跳转到原文并高亮 →
      </Link>
    </div>
  )
}

/** 标题路径以文档标题开头时（文档的一级标题与标题相同）省略重复部分。 */
function withoutTitle(headingPath: string, title: string): string {
  return headingPath.startsWith(`${title} > `) ? headingPath.slice(title.length + 3) : headingPath
}
