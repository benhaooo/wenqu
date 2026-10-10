import { FileTextIcon, FolderIcon, SearchIcon } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { useSearchNodes } from '@/shared/api/generated/document/document'
import { cn } from '@/shared/lib/cn'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

const isMac = typeof navigator !== 'undefined' && navigator.userAgent.includes('Mac')

/** 按标题和正文搜索，快捷键 Ctrl/Cmd + K。 */
export function SearchDialog({ workspaceId }: { workspaceId: number }) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState(0)
  const query = useDebouncedValue(keyword.trim(), 250)
  const { data: hits = [], isFetching } = useSearchNodes(
    workspaceId,
    { q: query },
    { query: { enabled: open && query.length > 0, select: (result) => result.data } },
  )

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setOpen(true)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [])

  const choose = (index: number) => {
    const hit = hits[index]
    if (!hit) return
    setOpen(false)
    setKeyword('')
    if (hit.type === 'DOCUMENT') void navigate(`/w/${String(workspaceId)}/d/${String(hit.id)}`)
  }

  return (
    <>
      <button
        type="button"
        className="flex min-h-10 w-full items-center gap-2 rounded-md border border-border bg-background px-3 text-muted-foreground outline-none hover:border-input focus-visible:ring-2 focus-visible:ring-ring"
        onClick={() => {
          setOpen(true)
        }}
      >
        <SearchIcon className="size-4" />
        <span className="flex-1 text-left">搜索文档</span>
        <kbd className="rounded-[4px] border border-border bg-canvas px-1.5 font-mono text-[11px]">
          {isMac ? '⌘K' : 'Ctrl K'}
        </kbd>
      </button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className="top-24 translate-y-0 gap-0 p-0 sm:max-w-xl"
          showCloseButton={false}
        >
          <DialogTitle className="sr-only">搜索文档</DialogTitle>
          <div className="flex items-center gap-2 border-b px-3">
            <SearchIcon className="size-4 text-muted-foreground" />
            <Input
              autoFocus
              value={keyword}
              placeholder="搜索标题和正文"
              className="border-0 shadow-none focus-visible:ring-0"
              onChange={(event) => {
                setKeyword(event.target.value)
                setSelected(0)
              }}
              onKeyDown={(event) => {
                if (event.key === 'ArrowDown') {
                  event.preventDefault()
                  setSelected((i) => Math.min(i + 1, hits.length - 1))
                } else if (event.key === 'ArrowUp') {
                  event.preventDefault()
                  setSelected((i) => Math.max(i - 1, 0))
                } else if (event.key === 'Enter') {
                  event.preventDefault()
                  choose(selected)
                }
              }}
            />
          </div>
          <ul className="max-h-96 overflow-auto p-1" role="listbox" aria-label="搜索结果">
            {query.length > 0 && !isFetching && hits.length === 0 && (
              <li className="px-3 py-6 text-center text-sm text-muted-foreground">
                没有找到相关内容
              </li>
            )}
            {hits.map((hit, index) => {
              const Icon = hit.type === 'FOLDER' ? FolderIcon : FileTextIcon
              return (
                <li
                  key={hit.id}
                  role="option"
                  aria-selected={index === selected}
                  className={cn(
                    'cursor-pointer rounded-md px-3 py-2',
                    index === selected && 'bg-accent',
                  )}
                  onMouseEnter={() => {
                    setSelected(index)
                  }}
                  onClick={() => {
                    choose(index)
                  }}
                >
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <Icon className="size-4 text-muted-foreground" />
                    {hit.title}
                  </div>
                  {hit.snippet && (
                    <p className="mt-0.5 line-clamp-2 pl-6 text-xs text-muted-foreground">
                      {hit.snippet}
                    </p>
                  )}
                </li>
              )
            })}
          </ul>
        </DialogContent>
      </Dialog>
    </>
  )
}
