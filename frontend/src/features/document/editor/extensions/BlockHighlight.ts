import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    blockHighlight: {
      /** 高亮第 from～to 个顶层块（从 0 开始，含两端），对应后端文本块的 blockFrom、blockTo。 */
      highlightBlocks: (from: number, to: number) => ReturnType
    }
  }
}

interface BlockRange {
  from: number
  to: number
}

const key = new PluginKey<DecorationSet>('blockHighlight')

/** AI 回答引用跳转到原文时高亮对应段落；用户开始编辑后高亮消失。高亮是装饰，不修改文档内容。 */
export const BlockHighlight = Extension.create({
  name: 'blockHighlight',

  addCommands() {
    return {
      highlightBlocks:
        (from, to) =>
        ({ tr, dispatch }) => {
          if (dispatch) tr.setMeta(key, { from, to } satisfies BlockRange)
          return true
        },
    }
  },

  addProseMirrorPlugins() {
    return [
      new Plugin<DecorationSet>({
        key,
        state: {
          init: () => DecorationSet.empty,
          apply(tr, current) {
            const range = tr.getMeta(key) as BlockRange | undefined
            if (!range) return tr.docChanged ? DecorationSet.empty : current
            const decorations: Decoration[] = []
            tr.doc.forEach((node, offset, index) => {
              if (index >= range.from && index <= range.to) {
                decorations.push(
                  Decoration.node(offset, offset + node.nodeSize, { class: 'block-highlight' }),
                )
              }
            })
            return DecorationSet.create(tr.doc, decorations)
          },
        },
        props: {
          decorations: (state) => key.getState(state),
        },
      }),
    ]
  },
})

/** 解析链接参数 blocks=3-5；格式不对时返回 null。 */
export function parseBlockRange(value: string | null): BlockRange | null {
  const match = /^(\d+)-(\d+)$/.exec(value ?? '')
  if (!match) return null
  const from = Number(match[1])
  const to = Number(match[2])
  return from <= to ? { from, to } : null
}
