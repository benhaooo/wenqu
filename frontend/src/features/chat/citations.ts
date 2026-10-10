const CODE = /(```[\s\S]*?(?:```|$)|`[^`\n]*`)/
const MARKER = /\[(\d{1,2})\]/g

export const CITATION_HREF_PREFIX = '#cite-'

/**
 * 把回答中的引用角标 [n] 转换为 Markdown 链接 [n](#cite-n)，由渲染器显示为角标按钮。
 * 只转换确实存在的引用；代码块和行内代码中的方括号保持原样。
 */
export function linkCitations(markdown: string, markers: ReadonlySet<number>): string {
  return markdown
    .split(CODE)
    .map((part, index) =>
      index % 2 === 1
        ? part
        : part.replace(MARKER, (match, number: string) =>
            markers.has(Number(number)) ? `[${number}](${CITATION_HREF_PREFIX}${number})` : match,
          ),
    )
    .join('')
}

/** 角标链接中的引用序号，不是角标链接时为 null。 */
export function citationMarker(href: string | undefined): number | null {
  if (!href?.startsWith(CITATION_HREF_PREFIX)) return null
  const marker = Number(href.slice(CITATION_HREF_PREFIX.length))
  return Number.isInteger(marker) ? marker : null
}

/** 跳转到原文并高亮引用的段落范围。 */
export function citationTarget(
  workspaceId: number,
  citation: { documentId: number; blockFrom: number; blockTo: number },
): string {
  return `/w/${String(workspaceId)}/d/${String(citation.documentId)}?blocks=${String(citation.blockFrom)}-${String(citation.blockTo)}`
}
