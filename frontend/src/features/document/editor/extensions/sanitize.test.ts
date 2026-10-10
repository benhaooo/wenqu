import { Editor } from '@tiptap/core'
import { afterEach, describe, expect, it } from 'vitest'
import { createExtensions } from '.'

declare global {
  interface Window {
    pwned?: boolean
  }
}

/** 编辑器 schema 对粘贴 HTML 的过滤：粘贴内容最终都经过同一个解析流程。 */
describe('pasted HTML', () => {
  let editor: Editor | null = null

  afterEach(() => {
    editor?.destroy()
    window.pwned = undefined
  })

  it('drops scripts, event handlers, dangerous links and external images', () => {
    editor = new Editor({
      element: document.createElement('div'),
      extensions: createExtensions({
        workspaceId: 1,
        upload: () => Promise.resolve(null),
        editable: true,
      }),
    })

    editor.commands.insertContent(`
      <p onclick="window.pwned = true">正文<script>window.pwned = true</script></p>
      <img src="x" onerror="window.pwned = true">
      <p><a href="javascript:window.pwned = true">点我</a> <a href="https://example.com">安全</a></p>
      <iframe src="https://evil.example"></iframe>
      <h1 style="color:red">标题</h1>
    `)

    const html = editor.getHTML()
    const json = JSON.stringify(editor.getJSON())
    expect(html).not.toMatch(/script|onerror|onclick|iframe|javascript:|style=/i)
    expect(json).not.toContain('"image"')
    expect(html).toContain('href="https://example.com"')
    expect(editor.getText()).toContain('正文')
    expect(window.pwned).toBeUndefined()
  })
})
