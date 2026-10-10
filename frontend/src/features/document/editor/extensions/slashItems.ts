import type { Editor, Range } from '@tiptap/core'
import {
  CodeIcon,
  Heading1Icon,
  Heading2Icon,
  Heading3Icon,
  ImageIcon,
  InfoIcon,
  ListChecksIcon,
  ListIcon,
  ListOrderedIcon,
  MinusIcon,
  QuoteIcon,
  TableIcon,
  TriangleAlertIcon,
  type LucideIcon,
} from 'lucide-react'

export interface SlashItem {
  title: string
  /** 用于过滤的关键词（英文、拼音首字母） */
  keywords: string[]
  icon: LucideIcon
  run: (editor: Editor, range: Range) => void
}

const chain = (editor: Editor, range: Range) => editor.chain().focus().deleteRange(range)

export const slashItems: SlashItem[] = [
  {
    title: '一级标题',
    keywords: ['h1', 'heading', 'bt'],
    icon: Heading1Icon,
    run: (e, r) => chain(e, r).setNode('heading', { level: 1 }).run(),
  },
  {
    title: '二级标题',
    keywords: ['h2', 'heading', 'bt'],
    icon: Heading2Icon,
    run: (e, r) => chain(e, r).setNode('heading', { level: 2 }).run(),
  },
  {
    title: '三级标题',
    keywords: ['h3', 'heading', 'bt'],
    icon: Heading3Icon,
    run: (e, r) => chain(e, r).setNode('heading', { level: 3 }).run(),
  },
  {
    title: '无序列表',
    keywords: ['ul', 'bullet', 'list', 'lb'],
    icon: ListIcon,
    run: (e, r) => chain(e, r).toggleBulletList().run(),
  },
  {
    title: '有序列表',
    keywords: ['ol', 'ordered', 'list', 'lb'],
    icon: ListOrderedIcon,
    run: (e, r) => chain(e, r).toggleOrderedList().run(),
  },
  {
    title: '任务列表',
    keywords: ['todo', 'task', 'rw'],
    icon: ListChecksIcon,
    run: (e, r) => chain(e, r).toggleTaskList().run(),
  },
  {
    title: '代码块',
    keywords: ['code', 'dm'],
    icon: CodeIcon,
    run: (e, r) => chain(e, r).toggleCodeBlock().run(),
  },
  {
    title: '引用',
    keywords: ['quote', 'yy'],
    icon: QuoteIcon,
    run: (e, r) => chain(e, r).toggleBlockquote().run(),
  },
  {
    title: '表格',
    keywords: ['table', 'bg'],
    icon: TableIcon,
    run: (e, r) => chain(e, r).insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run(),
  },
  {
    title: '提示框',
    keywords: ['callout', 'info', 'tsk'],
    icon: InfoIcon,
    run: (e, r) => chain(e, r).setCallout('info').run(),
  },
  {
    title: '警告框',
    keywords: ['callout', 'warning', 'jg'],
    icon: TriangleAlertIcon,
    run: (e, r) => chain(e, r).setCallout('warning').run(),
  },
  {
    title: '分割线',
    keywords: ['hr', 'divider', 'fgx'],
    icon: MinusIcon,
    run: (e, r) => chain(e, r).setHorizontalRule().run(),
  },
  {
    title: '图片',
    keywords: ['image', 'img', 'tp'],
    icon: ImageIcon,
    run: (e, r) => chain(e, r).pickImage().run(),
  },
]

export function filterSlashItems(query: string): SlashItem[] {
  const q = query.trim().toLowerCase()
  if (!q) return slashItems
  return slashItems.filter(
    (item) => item.title.includes(q) || item.keywords.some((keyword) => keyword.startsWith(q)),
  )
}
