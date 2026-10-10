import type { NodeResponse } from '@/shared/api/generated/model'

export const ROOT_ID = 'root'

/** 由扁平节点列表建立的目录树索引，children 按 position 升序。 */
export interface NodeIndex {
  nodes: Map<string, NodeResponse>
  children: Map<string, string[]>
}

export function toTreeId(parentId: number | null | undefined): string {
  return parentId == null ? ROOT_ID : String(parentId)
}

export function buildIndex(list: NodeResponse[]): NodeIndex {
  const nodes = new Map(list.map((node) => [String(node.id), node]))
  const children = new Map<string, string[]>([[ROOT_ID, []]])
  const sorted = [...list].sort((a, b) =>
    a.position < b.position ? -1 : a.position > b.position ? 1 : a.id - b.id,
  )
  for (const node of sorted) {
    const parent = toTreeId(node.parentId)
    const siblings = children.get(parent) ?? []
    siblings.push(String(node.id))
    children.set(parent, siblings)
  }
  return { nodes, children }
}

/** 从根到 nodeId 的路径（不含根），用于面包屑和展开祖先。 */
export function pathTo(index: NodeIndex, nodeId: number): NodeResponse[] {
  const path: NodeResponse[] = []
  let current = index.nodes.get(String(nodeId))
  while (current) {
    path.unshift(current)
    current = current.parentId == null ? undefined : index.nodes.get(String(current.parentId))
  }
  return path
}
