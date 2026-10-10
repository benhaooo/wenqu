import { autoUpdate, computePosition, flip, offset, shift } from '@floating-ui/dom'
import { Extension, type Editor, type Range } from '@tiptap/core'
import { ReactRenderer } from '@tiptap/react'
import Suggestion, { type SuggestionKeyDownProps, type SuggestionProps } from '@tiptap/suggestion'
import { filterSlashItems, type SlashItem } from './slashItems'
import { SlashMenu, type SlashMenuHandle, type SlashMenuProps } from './SlashMenu'

export const SlashCommand = Extension.create({
  name: 'slashCommand',

  addProseMirrorPlugins() {
    return [
      Suggestion<SlashItem, SlashItem>({
        editor: this.editor,
        char: '/',
        items: ({ query }) => filterSlashItems(query),
        command: ({ editor, range, props }: { editor: Editor; range: Range; props: SlashItem }) => {
          props.run(editor, range)
        },
        render: () => {
          let renderer: ReactRenderer<SlashMenuHandle, SlashMenuProps> | null = null
          let cleanup: (() => void) | null = null

          const position = (props: SuggestionProps<SlashItem>) => {
            const element = renderer?.element
            const rect = props.clientRect?.()
            if (!(element instanceof HTMLElement) || !rect) return
            cleanup?.()
            const reference = { getBoundingClientRect: () => props.clientRect?.() ?? rect }
            cleanup = autoUpdate(reference, element, () => {
              void computePosition(reference, element, {
                placement: 'bottom-start',
                strategy: 'fixed',
                middleware: [offset(6), flip(), shift({ padding: 8 })],
              }).then(({ x, y }) => {
                Object.assign(element.style, { left: `${String(x)}px`, top: `${String(y)}px` })
              })
            })
          }

          return {
            onStart: (props) => {
              renderer = new ReactRenderer(SlashMenu, { props, editor: props.editor })
              if (renderer.element instanceof HTMLElement) {
                Object.assign(renderer.element.style, { position: 'fixed', zIndex: '50' })
                document.body.appendChild(renderer.element)
              }
              position(props)
            },
            onUpdate: (props) => {
              renderer?.updateProps(props)
              position(props)
            },
            onKeyDown: ({ event }: SuggestionKeyDownProps) => {
              if (event.key === 'Escape') {
                renderer?.destroy()
                renderer?.element.remove()
                return true
              }
              return renderer?.ref?.onKeyDown(event) ?? false
            },
            onExit: () => {
              cleanup?.()
              renderer?.destroy()
              renderer?.element.remove()
              renderer = null
            },
          }
        },
      }),
    ]
  },
})
