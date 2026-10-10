import { useListNodes } from '@/shared/api/generated/document/document'
import type { ScopeOption } from '../components/ScopePicker'

const FROM_DOCUMENT = '从文档页点击「问 AI」进入后可选'

/** 可选的提问范围。contextDocumentId 是进入问答前所在的文档，据此提供「当前文件夹」「当前文档」。 */
export function useScopeOptions(
  workspaceId: number,
  contextDocumentId: number | null,
): ScopeOption[] {
  const { data: nodes } = useListNodes(workspaceId, { query: { select: (result) => result.data } })
  const document = nodes?.find((node) => node.id === contextDocumentId && node.type === 'DOCUMENT')
  const folder =
    document?.parentId === undefined
      ? undefined
      : nodes?.find((node) => node.id === document.parentId)
  return [
    { type: 'WORKSPACE', label: '整个空间' },
    folder
      ? { type: 'FOLDER', label: `文件夹：${folder.title}`, nodeId: folder.id }
      : {
          type: 'FOLDER',
          label: '当前文件夹',
          disabledReason: document ? '当前文档位于根目录' : FROM_DOCUMENT,
        },
    document
      ? { type: 'DOCUMENT', label: `文档：${document.title}`, nodeId: document.id }
      : { type: 'DOCUMENT', label: '当前文档', disabledReason: FROM_DOCUMENT },
  ]
}
