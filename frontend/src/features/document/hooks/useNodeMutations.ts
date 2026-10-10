import { useQueryClient } from '@tanstack/react-query'
import { generateKeyBetween } from 'fractional-indexing'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import {
  getListNodesQueryKey,
  getListTrashQueryKey,
  useCreateNode,
  useDeleteNode,
  useMoveNode,
  useRenameNode,
} from '@/shared/api/generated/document/document'
import type { ApiResultListNodeResponse, NodeType } from '@/shared/api/generated/model'
import type { NodeIndex } from '../tree'
import { toTreeId } from '../tree'

/**
 * 目录树的增删改与移动。移动采用乐观更新：先按分数索引算出新位置立即显示，失败时回滚。
 * 前端使用的 fractional-indexing 与后端是同一算法，最终位置以服务端返回为准。
 */
export function useNodeMutations(workspaceId: number) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const queryKey = getListNodesQueryKey(workspaceId)
  const refresh = () => queryClient.invalidateQueries({ queryKey })
  const onError = (error: Error) => {
    toast.error(error.message)
    void refresh()
  }

  const createNode = useCreateNode({ mutation: { onError } })
  const renameNode = useRenameNode({ mutation: { onError, onSettled: refresh } })
  const deleteNode = useDeleteNode({
    mutation: {
      onError,
      onSuccess: () => {
        toast.success('已移入回收站')
        void refresh()
        void queryClient.invalidateQueries({ queryKey: getListTrashQueryKey(workspaceId) })
      },
    },
  })
  const moveNode = useMoveNode({ mutation: { onError, onSettled: refresh } })

  return {
    create: (parentId: number | null, type: NodeType) => {
      const title = type === 'FOLDER' ? '新建文件夹' : '无标题文档'
      createNode.mutate(
        { workspaceId, data: { parentId, type, title } },
        {
          onSuccess: (result) => {
            void refresh()
            if (type === 'DOCUMENT') {
              void navigate(`/w/${String(workspaceId)}/d/${String(result.data.id)}`)
            }
          },
        },
      )
    },
    rename: (nodeId: number, title: string) => {
      renameNode.mutate({ workspaceId, nodeId, data: { title } })
    },
    remove: (nodeId: number) => {
      deleteNode.mutate({ workspaceId, nodeId })
    },
    /** 把 nodeId 移到 parentId 中、afterNodeId 之后（null 表示最前）。 */
    move: (
      index: NodeIndex,
      nodeId: number,
      parentId: number | null,
      afterNodeId: number | null,
    ) => {
      const siblings = (index.children.get(toTreeId(parentId)) ?? []).filter(
        (id) => id !== String(nodeId),
      )
      const afterPosition =
        afterNodeId == null ? null : (index.nodes.get(String(afterNodeId))?.position ?? null)
      const nextId = siblings[afterNodeId == null ? 0 : siblings.indexOf(String(afterNodeId)) + 1]
      const nextPosition = nextId ? (index.nodes.get(nextId)?.position ?? null) : null
      try {
        const position = generateKeyBetween(afterPosition, nextPosition)
        queryClient.setQueryData<ApiResultListNodeResponse>(queryKey, (previous) =>
          previous
            ? {
                ...previous,
                data: previous.data.map((node) =>
                  node.id === nodeId ? { ...node, parentId, position } : node,
                ),
              }
            : previous,
        )
      } catch {
        // 本地无法计算位置时（如排序键相同）跳过乐观更新，等待服务端结果
      }
      moveNode.mutate({ workspaceId, nodeId, data: { parentId, afterNodeId } })
    },
  }
}
