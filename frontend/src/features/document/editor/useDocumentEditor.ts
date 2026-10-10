import type { JSONContent } from '@tiptap/core'
import { useEditor } from '@tiptap/react'
import { toast } from 'sonner'
import { uploadImage } from '@/shared/api/generated/document/document'
import { ApiError } from '@/shared/api/http'
import { createExtensions } from './extensions'
import { useAutoSave } from './useAutoSave'
import './editor.css'

/** 文档编辑器实例及其自动保存状态；只读成员得到不可编辑的编辑器，不会触发保存。 */
export function useDocumentEditor({
  workspaceId,
  nodeId,
  content,
  version,
  editable,
}: {
  workspaceId: number
  nodeId: number
  content: JSONContent
  version: number
  editable: boolean
}) {
  const editor = useEditor(
    {
      extensions: createExtensions({
        workspaceId,
        editable,
        upload: async (file) => {
          const toastId = toast.loading('图片上传中…')
          try {
            const result = await uploadImage(workspaceId, nodeId, { file })
            toast.dismiss(toastId)
            return result.data.imageId
          } catch (error) {
            toast.error(error instanceof ApiError ? error.message : '图片上传失败', { id: toastId })
            return null
          }
        },
      }),
      content,
      editable,
      immediatelyRender: true,
      shouldRerenderOnTransaction: false,
      editorProps: {
        attributes: {
          class: 'focus:outline-none min-h-[50vh]',
          'aria-label': '文档正文',
        },
      },
    },
    [workspaceId, nodeId, editable],
  )
  const autoSave = useAutoSave(editable ? editor : null, {
    workspaceId,
    nodeId,
    initialVersion: version,
  })
  return { editor, ...autoSave }
}
