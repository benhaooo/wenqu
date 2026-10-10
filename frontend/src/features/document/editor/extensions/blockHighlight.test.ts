import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import { describe, expect, it } from 'vitest'
import { BlockHighlight, parseBlockRange } from './BlockHighlight'

function editorWith(paragraphs: string[]) {
  return new Editor({
    extensions: [StarterKit, BlockHighlight],
    content: {
      type: 'doc',
      content: paragraphs.map((text) => ({ type: 'paragraph', content: [{ type: 'text', text }] })),
    },
  })
}

const highlighted = (editor: Editor) =>
  [...editor.view.dom.querySelectorAll('.block-highlight')].map((element) => element.textContent)

describe('BlockHighlight', () => {
  it('highlights the requested top-level blocks', () => {
    const editor = editorWith(['零', '一', '二', '三'])

    editor.commands.highlightBlocks(1, 2)

    expect(highlighted(editor)).toEqual(['一', '二'])
    editor.destroy()
  })

  it('clears the highlight once the document is edited', () => {
    const editor = editorWith(['零', '一'])
    editor.commands.highlightBlocks(0, 1)

    editor.commands.insertContentAt(1, '新')

    expect(highlighted(editor)).toEqual([])
    editor.destroy()
  })

  it('parses block ranges from the link', () => {
    expect(parseBlockRange('3-5')).toEqual({ from: 3, to: 5 })
    expect(parseBlockRange('5-3')).toBeNull()
    expect(parseBlockRange('abc')).toBeNull()
    expect(parseBlockRange(null)).toBeNull()
  })
})
