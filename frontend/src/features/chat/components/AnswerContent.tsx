import { createContext, use, type ReactNode } from 'react'
import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { CitationResponse } from '@/shared/api/generated/model'
import { citationMarker, linkCitations } from '../citations'
import './answer.css'

interface CitationState {
  citations: ReadonlyMap<number, CitationResponse>
  activeMarker: number | null
  onCitationClick: (marker: number) => void
}

const CitationContext = createContext<CitationState>({
  citations: new Map(),
  activeMarker: null,
  onCitationClick: () => undefined,
})

/** 链接渲染器保持稳定的组件身份（状态经 context 传入），避免每次渲染重建角标按钮。 */
function AnswerLink({ href, children }: { href?: string; children?: ReactNode }) {
  const { citations, activeMarker, onCitationClick } = use(CitationContext)
  const marker = citationMarker(href)
  const citation = marker === null ? undefined : citations.get(marker)
  if (marker === null || !citation) {
    return (
      <a href={href} target="_blank" rel="noopener noreferrer">
        {children}
      </a>
    )
  }
  return (
    <button
      type="button"
      aria-label={`查看来源 ${String(marker)}：${citation.documentTitle}`}
      aria-expanded={activeMarker === marker}
      onClick={() => {
        onCitationClick(marker)
      }}
      className="mx-0.5 min-w-[22px] cursor-pointer rounded-[4px] bg-cite px-1.5 align-[2px] font-sans text-xs leading-[1.6] font-semibold text-cite-foreground hover:brightness-95 aria-expanded:ring-2 aria-expanded:ring-cite-foreground/40"
    >
      {marker}
    </button>
  )
}

const components: Components = { a: AnswerLink }

/**
 * 渲染 AI 回答（Markdown）。react-markdown 生成 React 元素、不渲染原始 HTML，模型输出中的 HTML 以文本显示。
 * 引用角标 [n] 显示为按钮，点击展开引用卡片。
 */
export function AnswerContent({
  text,
  citations,
  activeMarker,
  onCitationClick,
  trailing,
}: {
  text: string
  citations: CitationResponse[]
  activeMarker: number | null
  onCitationClick: (marker: number) => void
  trailing?: ReactNode
}) {
  const byMarker = new Map(citations.map((citation) => [citation.marker, citation]))
  return (
    <CitationContext value={{ citations: byMarker, activeMarker, onCitationClick }}>
      <div className="answer-content">
        <Markdown remarkPlugins={[remarkGfm]} components={components}>
          {linkCitations(text, new Set(byMarker.keys()))}
        </Markdown>
        {trailing}
      </div>
    </CitationContext>
  )
}
