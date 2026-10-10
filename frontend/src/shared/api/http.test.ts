import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, http } from './http'

function mockFetch(status: number, body: string) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status })))
}

describe('http', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the body when code is 0', async () => {
    mockFetch(200, JSON.stringify({ code: 0, message: '成功', data: { id: 1 } }))

    await expect(http('/api/x')).resolves.toEqual({ code: 0, message: '成功', data: { id: 1 } })
  })

  it('throws ApiError with backend code and message on error response', async () => {
    mockFetch(404, JSON.stringify({ code: 13004, message: '文档不存在', data: null }))

    await expect(http('/api/x')).rejects.toEqual(new ApiError(13004, '文档不存在', 404))
  })

  it('throws ApiError when the body is not the unified format', async () => {
    mockFetch(502, '<html>Bad Gateway</html>')

    await expect(http('/api/x')).rejects.toMatchObject({ code: -1, status: 502 })
  })
})
