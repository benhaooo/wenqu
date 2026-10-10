import type { SuggestionProps } from '@tiptap/suggestion'
import { Fragment, useImperativeHandle, useState, type Ref } from 'react'
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

  return (
    <div
      role="listbox"
      aria-label="插入内容"
      className="flex max-h-[360px] w-[280px] flex-col gap-0.5 overflow-auto rounded-[10px] border border-border bg-popover p-1.5 text-sm leading-snug shadow-[0_12px_32px_rgba(24,33,30,0.14)]"
    >
      {items.length === 0 && (
        <span className="px-2.5 py-2 text-muted-foreground">没有匹配的内容</span>
      )}
      {items.map((item, index) => (
        <Fragment key={item.title}>
          {item.group !== items[index - 1]?.group && (
            <span
              className={cn(
                'px-2.5 pt-1.5 pb-1 text-xs text-muted-foreground',
                index > 0 && 'mt-1 border-t border-border-subtle',
              )}
            >
              {item.group}
            </span>
          )}
          <button
            type="button"
            role="option"
            aria-selected={index === selected}
            className={cn(
              'flex w-full items-center gap-2.5 rounded-sm px-2.5 py-2 text-left',
              index === selected && 'bg-accent',
            )}
            onMouseEnter={() => {
              setSelected(index)
            }}
            onMouseDown={(event) => {
              event.preventDefault()
              command(item)
            }}
          >
            <span className="flex size-[30px] shrink-0 items-center justify-center rounded-sm border border-border bg-background">
              <item.icon className="size-4" aria-hidden="true" />
            </span>
            <span className="flex flex-col">
              <span className="font-medium">{item.title}</span>
              {item.shortcut && (
                <span className="font-mono text-xs text-muted-foreground">{item.shortcut}</span>
              )}
            </span>
          </button>
        </Fragment>
      ))}
      <span className="mt-1 border-t border-border-subtle px-2.5 pt-1.5 pb-0.5 text-xs text-muted-foreground">
        ↑↓ 选择 · 回车 插入 · Esc 关闭
      </span>
    </div>
  )
}
