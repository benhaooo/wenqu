import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight'
import { TaskItem, TaskList } from '@tiptap/extension-list'
import { TableKit } from '@tiptap/extension-table'
import { Placeholder } from '@tiptap/extensions'
import StarterKit from '@tiptap/starter-kit'
import { common, createLowlight } from 'lowlight'
import { Callout } from './Callout'
import { ImageUpload, type ImageUploadOptions } from './ImageUpload'
import { SlashCommand } from './SlashCommand'
import { WorkspaceImage } from './WorkspaceImage'

const lowlight = createLowlight(common)

/** 与后端 ProseMirrorContent 允许的链接协议一致。 */
export function isSafeHref(url: string): boolean {
  return /^(https?:|mailto:|\/|#)/i.test(url.trim())
}

/**
 * 编辑器扩展。节点与 mark 的名称需与后端 ProseMirrorContent 的白名单一致。
 * 粘贴的 HTML 由 schema 解析，不在 schema 中的标签（如 script、iframe）和属性（如 onerror）会被丢弃。
 */
export function createExtensions(options: {
  workspaceId: number
  upload: ImageUploadOptions['upload']
  editable: boolean
}) {
  return [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      codeBlock: false,
      link: {
        openOnClick: !options.editable,
        autolink: true,
        isAllowedUri: (url) => isSafeHref(url),
      },
    }),
    TaskList,
    TaskItem.configure({ nested: true }),
    TableKit.configure({ table: { resizable: false } }),
    CodeBlockLowlight.configure({ lowlight, defaultLanguage: null }),
    Callout,
    WorkspaceImage.configure({ workspaceId: options.workspaceId }),
    ImageUpload.configure({ upload: options.upload }),
    SlashCommand,
    Placeholder.configure({ placeholder: '输入正文，或输入 / 插入标题、列表、代码块等' }),
  ]
}
