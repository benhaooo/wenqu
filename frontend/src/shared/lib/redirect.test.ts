import { describe, expect, it } from 'vitest'
import { safeRedirect } from './redirect'

describe('safeRedirect', () => {
  it('keeps in-site paths', () => {
    expect(safeRedirect('/w/1?x=1')).toBe('/w/1?x=1')
  })

  it.each([null, '', 'https://evil.example', '//evil.example', 'javascript:alert(1)'])(
    'falls back to home for %s',
    (target) => {
      expect(safeRedirect(target)).toBe('/')
    },
  )
})
