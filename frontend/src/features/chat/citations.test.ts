import { describe, expect, it } from 'vitest'
import { citationMarker, citationTarget, linkCitations } from './citations'

describe('linkCitations', () => {
  it('links known markers, including adjacent ones', () => {
    expect(linkCitations('先切流量[1][3]，再回滚[2]。', new Set([1, 3]))).toBe(
      '先切流量[1](#cite-1)[3](#cite-3)，再回滚[2]。',
    )
  })

  it('leaves code untouched', () => {
    const markdown = '执行 `arr[1]`：\n```js\nconst a = b[1]\n```\n结论[1]'

    expect(linkCitations(markdown, new Set([1]))).toBe(
      '执行 `arr[1]`：\n```js\nconst a = b[1]\n```\n结论[1](#cite-1)',
    )
  })

  it('does not link inside an unfinished code block while streaming', () => {
    expect(linkCitations('看这里[1]\n```\nx[1]', new Set([1]))).toBe(
      '看这里[1](#cite-1)\n```\nx[1]',
    )
  })
})

describe('citation helpers', () => {
  it('parses marker links and builds jump targets', () => {
    expect(citationMarker('#cite-12')).toBe(12)
    expect(citationMarker('https://example.com')).toBeNull()
    expect(citationTarget(3, { documentId: 9, blockFrom: 2, blockTo: 4 })).toBe(
      '/w/3/d/9?blocks=2-4',
    )
  })
})
