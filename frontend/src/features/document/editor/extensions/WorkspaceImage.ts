import { mergeAttributes, Node } from '@tiptap/core'
import { ReactNodeViewRenderer } from '@tiptap/react'
import { ImageView } from './ImageView'

export interface WorkspaceImageOptions {
  workspaceId: number
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    workspaceImage: {
      insertImage: (imageId: string, position?: number) => ReturnType
    }
  }
}

/**
 * 图片节点：文档中只保存 imageId，显示时向后端换取有时效的签名链接（对象存储不公开）。
 * 只解析带 data-image-id 的 img，粘贴的外部图片不会进入文档。
 */
export const WorkspaceImage = Node.create<WorkspaceImageOptions>({
  name: 'image',
  group: 'block',
  atom: true,
  draggable: true,

  addOptions() {
    return { workspaceId: 0 }
  },

  addAttributes() {
    return {
      imageId: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-image-id'),
        renderHTML: (attributes: { imageId: string | null }) => ({
          'data-image-id': attributes.imageId,
        }),
      },
      alt: { default: null },
    }
  },

  parseHTML() {
    return [{ tag: 'img[data-image-id]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['img', mergeAttributes(HTMLAttributes)]
  },

  addNodeView() {
    return ReactNodeViewRenderer(ImageView)
  },

  addCommands() {
    return {
      insertImage:
        (imageId, position) =>
        ({ commands, state }) =>
          commands.insertContentAt(position ?? state.selection.to, {
            type: this.name,
            attrs: { imageId },
          }),
    }
  },
})
