import { Extension } from '@tiptap/core'
import { Plugin } from '@tiptap/pm/state'

export interface ImageUploadOptions {
  /** 上传图片并返回 imageId；失败时返回 null（由调用方提示错误）。 */
  upload: (file: File) => Promise<string | null>
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    imageUpload: {
      pickImage: () => ReturnType
    }
  }
}

function imageFiles(files: FileList | null | undefined): File[] {
  return Array.from(files ?? []).filter((file) => file.type.startsWith('image/'))
}

/** 粘贴或拖入图片文件时自动上传并插入；也可以通过 pickImage 选择文件。 */
export const ImageUpload = Extension.create<ImageUploadOptions>({
  name: 'imageUpload',

  addOptions() {
    return { upload: () => Promise.resolve(null) }
  },

  addCommands() {
    return {
      pickImage:
        () =>
        ({ editor }) => {
          const input = document.createElement('input')
          input.type = 'file'
          input.accept = 'image/png,image/jpeg,image/gif,image/webp'
          input.onchange = () => {
            for (const file of imageFiles(input.files)) {
              void this.options.upload(file).then((imageId) => {
                if (imageId) editor.commands.insertImage(imageId)
              })
            }
          }
          input.click()
          return true
        },
    }
  },

  addProseMirrorPlugins() {
    const { upload } = this.options
    const editor = this.editor
    const insertAll = (files: File[], position?: number) => {
      for (const file of files) {
        void upload(file).then((imageId) => {
          if (imageId) editor.commands.insertImage(imageId, position)
        })
      }
    }
    return [
      new Plugin({
        props: {
          handlePaste: (_view, event) => {
            const files = imageFiles(event.clipboardData?.files)
            if (files.length === 0) return false
            insertAll(files)
            return true
          },
          handleDrop: (view, event) => {
            const files = imageFiles(event.dataTransfer?.files)
            if (files.length === 0) return false
            const position = view.posAtCoords({ left: event.clientX, top: event.clientY })?.pos
            insertAll(files, position)
            return true
          },
        },
      }),
    ]
  },
})
