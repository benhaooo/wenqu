import {
  dragAndDropFeature,
  hotkeysCoreFeature,
  isOrderedDragTarget,
  renamingFeature,
  selectionFeature,
  syncDataLoaderFeature,
  type DragTarget,
  type ItemInstance,
} from '@headless-tree/core'
import { useTree } from '@headless-tree/react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { ChevronRightIcon, MoreHorizontalIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { NodeResponse } from '@/shared/api/generated/model'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { useNodeMutations } from '../hooks/useNodeMutations'
import type { NodeIndex } from '../tree'
import { pathTo, ROOT_ID } from '../tree'

const ROW_HEIGHT = 32
const INDENT = 16

type Item = NodeResponse | undefined

/**
 * 目录树：虚拟滚动（只渲染可见行，500+ 节点不卡顿）、拖拽排序与跨文件夹移动、行内重命名、键盘导航。
 */
export function DocumentTree({
  workspaceId,
  index,
  canEdit,
}: {
  workspaceId: number
  index: NodeIndex
  canEdit: boolean
}) {
  const navigate = useNavigate()
  const activeId = useParams().nodeId
  const mutations = useNodeMutations(workspaceId)
  const scrollRef = useRef<HTMLDivElement>(null)
  const [expandedItems, setExpandedItems] = useState<string[]>([])

  const tree = useTree<Item>({
    rootItemId: ROOT_ID,
    state: { expandedItems },
    setExpandedItems,
    indent: INDENT,
    getItemName: (item) => item.getItemData()?.title ?? '',
    isItemFolder: (item) => item.getItemData()?.type === 'FOLDER' || item.getId() === ROOT_ID,
    dataLoader: {
      getItem: (id) => index.nodes.get(id),
      getChildren: (id) => index.children.get(id) ?? [],
    },
    canReorder: true,
    canDrag: () => canEdit,
    // 只能放进文件夹，且不能放进被拖动文件夹自身或其子孙（后端同样校验）
    canDrop: (items, target) =>
      target.item.isFolder() && !items.some((dragged) => isSelfOrAncestor(dragged, target.item)),
    onDrop: (items, target) => {
      onDrop(items, target)
    },
    onPrimaryAction: (item) => {
      const node = item.getItemData()
      if (node?.type === 'DOCUMENT') {
        void navigate(`/w/${String(workspaceId)}/d/${String(node.id)}`)
      }
    },
    canRename: () => canEdit,
    onRename: (item, value) => {
      const title = value.trim()
      const node = item.getItemData()
      if (node && title && title !== node.title) mutations.rename(node.id, title)
    },
    scrollToItem: (item) => {
      virtualizer.scrollToIndex(item.getItemMeta().index)
    },
    features: [
      syncDataLoaderFeature,
      selectionFeature,
      hotkeysCoreFeature,
      dragAndDropFeature,
      renamingFeature,
    ],
  })

  function onDrop(items: ItemInstance<Item>[], target: DragTarget<Item>) {
    const dragged = items[0]?.getItemData()
    if (!dragged) return
    const parent = target.item.getItemData()
    const parentId = parent?.id ?? null
    const siblings = target.item.getChildren().map((child) => child.getItemData()?.id)
    // 放在文件夹上时追加到最后；放在两项之间时取插入位置前面的同级节点
    const insertAt = isOrderedDragTarget(target) ? target.childIndex : siblings.length
    const before = siblings.slice(0, insertAt).filter((id) => id !== dragged.id)
    mutations.move(index, dragged.id, parentId, before.at(-1) ?? null)
    if (parent) setExpandedItems((current) => [...new Set([...current, String(parent.id)])])
  }

  // 数据变化后重建树；打开文档时展开其所在的文件夹
  useEffect(() => {
    tree.rebuildTree()
  }, [index, tree])
  useEffect(() => {
    if (!activeId) return
    const ancestors = pathTo(index, Number(activeId))
      .slice(0, -1)
      .map((node) => String(node.id))
    if (ancestors.length > 0) {
      setExpandedItems((current) => [...new Set([...current, ...ancestors])])
    }
  }, [activeId, index])

  const items = tree.getItems()
  // TanStack Virtual 返回的函数不能被 React Compiler 记忆化，本组件跳过编译优化
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10,
  })
  const dragLine = tree.getDragLineStyle()

  if (items.length === 0) {
    return <p className="px-2.5 py-2 text-[13px] text-muted-foreground">空间里还没有文档</p>
  }

  return (
    <div ref={scrollRef} className="min-h-0 flex-1 overflow-auto">
      <div
        {...tree.getContainerProps('目录')}
        className="relative"
        style={{ height: virtualizer.getTotalSize() }}
      >
        {virtualizer.getVirtualItems().map((row) => {
          const item = items[row.index]
          if (!item) return null
          return (
            <TreeRow
              key={item.getId()}
              item={item}
              top={row.start}
              active={item.getId() === activeId}
              canEdit={canEdit}
              onCreate={(type) => {
                const node = item.getItemData()
                if (!node) return
                mutations.create(node.id, type)
                item.expand()
              }}
              onDelete={() => {
                const node = item.getItemData()
                if (node) mutations.remove(node.id)
              }}
            />
          )
        })}
        <div style={dragLine} className="absolute h-0.5 bg-primary" />
      </div>
    </div>
  )
}

function isSelfOrAncestor(candidate: ItemInstance<Item>, item: ItemInstance<Item>): boolean {
  for (let current: ItemInstance<Item> | undefined = item; current; current = current.getParent()) {
    if (current.getId() === candidate.getId()) return true
  }
  return false
}

function TreeRow({
  item,
  top,
  active,
  canEdit,
  onCreate,
  onDelete,
}: {
  item: ItemInstance<Item>
  top: number
  active: boolean
  canEdit: boolean
  onCreate: (type: 'FOLDER' | 'DOCUMENT') => void
  onDelete: () => void
}) {
  const node = item.getItemData()
  const level = item.getItemMeta().level
  const folder = item.isFolder()

  return (
    <div
      className="group absolute inset-x-0 flex items-center"
      style={{ top, height: ROW_HEIGHT, paddingLeft: level * INDENT }}
    >
      {item.isRenaming() ? (
        <input
          {...item.getRenameInputProps()}
          className="h-8 w-full rounded-sm border border-primary bg-background px-2 outline-none ring-3 ring-primary-soft"
        />
      ) : (
        <button
          {...item.getProps()}
          className={cn(
            'flex h-8 min-w-0 flex-1 items-center gap-1.5 rounded-sm px-2 text-left text-foreground outline-none hover:bg-secondary focus-visible:ring-2 focus-visible:ring-ring',
            active &&
              'bg-sidebar-accent font-medium text-sidebar-accent-foreground hover:bg-sidebar-accent',
            item.isUnorderedDragTarget() && 'bg-accent ring-1 ring-primary',
          )}
        >
          <ChevronRightIcon
            aria-hidden="true"
            className={cn(
              'size-3.5 shrink-0 text-muted-foreground transition-transform',
              !folder && 'invisible',
              item.isExpanded() && 'rotate-90',
            )}
          />
          <span className="truncate">{node?.title}</span>
        </button>
      )}
      {canEdit && !item.isRenaming() && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="size-6 shrink-0 opacity-0 group-hover:opacity-100 data-[state=open]:opacity-100"
              aria-label={`${node?.title ?? ''} 的操作`}
            >
              <MoreHorizontalIcon />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {folder && (
              <>
                <DropdownMenuItem
                  onSelect={() => {
                    onCreate('DOCUMENT')
                  }}
                >
                  新建文档
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() => {
                    onCreate('FOLDER')
                  }}
                >
                  新建文件夹
                </DropdownMenuItem>
                <DropdownMenuSeparator />
              </>
            )}
            <DropdownMenuItem
              onSelect={() => {
                item.startRenaming()
              }}
            >
              重命名
            </DropdownMenuItem>
            <DropdownMenuItem variant="destructive" onSelect={onDelete}>
              删除
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  )
}
