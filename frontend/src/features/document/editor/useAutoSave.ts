import type { Editor, JSONContent } from '@tiptap/core'
import { useEffect, useRef, useState } from 'react'
import {
  getSaveDocumentContentUrl,
  saveDocumentContent,
} from '@/shared/api/generated/document/document'
import { ApiError } from '@/shared/api/http'
import { useSession } from '@/shared/api/session'
import { AutoSaver, type SaveStatus } from './autoSaver'

/** 文档版本冲突，见 docs/modules/document.md。 */
const VERSION_CONFLICT = 13006
/** 浏览器对 keepalive 请求体的限制约为 64KB。 */
const KEEPALIVE_LIMIT_BYTES = 60 * 1024

/**
 * 编辑器自动保存。离开页面时：
 * - 有未保存内容且大小允许时，用 keepalive 请求在页面卸载后继续提交，刷新页面不丢内容；
 * - 内容过大无法用 keepalive 提交时，弹出浏览器的离开确认。
 */
export function useAutoSave(
  editor: Editor | null,
  {
    workspaceId,
    nodeId,
    initialVersion,
  }: { workspaceId: number; nodeId: number; initialVersion: number },
) {
  const [status, setStatus] = useState<SaveStatus>('saved')
  const saverRef = useRef<AutoSaver<JSONContent> | null>(null)

  useEffect(() => {
    if (!editor?.isEditable) return
    const saver = new AutoSaver<JSONContent>({
      initialVersion,
      getContent: () => editor.getJSON(),
      save: async (content, baseVersion) =>
        (await saveDocumentContent(workspaceId, nodeId, { content, baseVersion })).data.version,
      isConflict: (error) => error instanceof ApiError && error.code === VERSION_CONFLICT,
      onStatus: setStatus,
    })
    saverRef.current = saver

    const onUpdate = () => {
      saver.markDirty()
    }
    const onOnline = () => {
      saver.flush()
    }
    const keepaliveBody = () =>
      JSON.stringify({ content: editor.getJSON(), baseVersion: saver.baseVersion })
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (saver.hasUnsavedChanges && keepaliveBody().length > KEEPALIVE_LIMIT_BYTES) {
        event.preventDefault()
      }
    }
    const onPageHide = () => {
      if (!saver.hasUnsavedChanges) return
      const body = keepaliveBody()
      if (body.length > KEEPALIVE_LIMIT_BYTES) return
      const { accessToken } = useSession.getState()
      void fetch(getSaveDocumentContentUrl(workspaceId, nodeId), {
        method: 'PUT',
        keepalive: true,
        headers: {
          'Content-Type': 'application/json',
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        },
        body,
      })
    }

    editor.on('update', onUpdate)
    window.addEventListener('online', onOnline)
    window.addEventListener('beforeunload', onBeforeUnload)
    window.addEventListener('pagehide', onPageHide)
    return () => {
      editor.off('update', onUpdate)
      window.removeEventListener('online', onOnline)
      window.removeEventListener('beforeunload', onBeforeUnload)
      window.removeEventListener('pagehide', onPageHide)
      // 离开文档时，未保存的修改以快照形式继续在后台保存（失败会重试）
      saver.detach(editor.getJSON())
      saverRef.current = null
    }
  }, [editor, workspaceId, nodeId, initialVersion])

  return { status, retry: () => saverRef.current?.flush() }
}
