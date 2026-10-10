import type { SuggestionProps } from '@tiptap/suggestion'
import { useImperativeHandle, useState, type Ref } from 'react'
import { cn } from '@/shared/lib/cn'
import type { SlashItem } from './slashItems'

export interface SlashMenuHandle {
  onKeyDown: (event: KeyboardEvent) => boolean
}

export type SlashMenuProps = SuggestionProps<SlashItem> & { ref?: Ref<SlashMenuHandle> }

/** 输入 / 弹出的插入菜单，上下键选择、回车确认、Esc 关闭。 */
export function SlashMenu({ items, command, ref }: SlashMenuProps) {
  const [selected, setSelected] = useState(0)
  const [lastItems, setLastItems] = useState(items)
  if (items !== lastItems) {
    setLastItems(items)
    setSelected(0)
  }

  useImperativeHandle(ref, () => ({
    onKeyDown: (event) => {
      if (items.length === 0) return false
      if (event.key === 'ArrowDown') {
        setSelected((i) => (i + 1) % items.length)
        return true
      }
      if (event.key === 'ArrowUp') {
        setSelected((i) => (i - 1 + items.length) % items.length)
        return true
      }
      if (event.key === 'Enter') {
        const item = items[selected]
        if (item) command(item)
        return true
      }
      return false
    },
  }))

  if (items.length === 0) {
    return (
      <div className="rounded-md border bg-popover px-3 py-2 text-sm text-muted-foreground shadow-md">
        没有匹配的块
      </div>
    )
  }
  return (
    <div
      className="max-h-80 w-56 overflow-auto rounded-md border bg-popover p-1 shadow-md"
      role="listbox"
      aria-label="插入块"
    >
      {items.map((item, index) => (
        <button
          key={item.title}
          type="button"
          role="option"
          aria-selected={index === selected}
          className={cn(
            'flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm',
            index === selected && 'bg-accent text-accent-foreground',
          )}
          onMouseEnter={() => {
            setSelected(index)
          }}
          onMouseDown={(event) => {
            event.preventDefault()
            command(item)
          }}
        >
          <item.icon className="size-4 text-muted-foreground" />
          {item.title}
        </button>
      ))}
    </div>
  )
}
