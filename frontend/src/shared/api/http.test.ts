import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, http } from './http'
import { setAccessToken, useSession } from './session'

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status })
}

const ok = (data: unknown) => json(200, { code: 0, message: '成功', data })
const expired = () => json(401, { code: 11003, message: '未登录或登录已过期', data: null })

describe('http', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('token-1')
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('sends the access token and returns the body', async () => {
    fetchMock.mockResolvedValueOnce(ok({ id: 1 }))

    await expect(http('/api/x')).resolves.toEqual({ code: 0, message: '成功', data: { id: 1 } })
    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers)
    expect(headers.get('Authorization')).toBe('Bearer token-1')
  })

  it('throws ApiError with backend code and message on error response', async () => {
    fetchMock.mockResolvedValueOnce(json(404, { code: 13004, message: '文档不存在', data: null }))

    await expect(http('/api/x')).rejects.toEqual(new ApiError(13004, '文档不存在', 404))
  })

  it('throws ApiError when the body is not the unified format', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>Bad Gateway</html>', { status: 502 }))

    await expect(http('/api/x')).rejects.toMatchObject({ code: -1, status: 502 })
  })

  it('refreshes an expired access token and retries once', async () => {
    fetchMock
      .mockResolvedValueOnce(expired())
      .mockResolvedValueOnce(ok({ accessToken: 'token-2' }))
      .mockResolvedValueOnce(ok('retried'))

    await expect(http('/api/x')).resolves.toMatchObject({ data: 'retried' })
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/auth/refresh')
    const retryHeaders = new Headers(fetchMock.mock.calls[2]?.[1]?.headers)
    expect(retryHeaders.get('Authorization')).toBe('Bearer token-2')
  })

  it('refreshes only once for concurrent expired requests', async () => {
    fetchMock.mockImplementation((input) => {
      if (input === '/api/auth/refresh') return Promise.resolve(ok({ accessToken: 'token-2' }))
      const auth = useSession.getState().accessToken
      return Promise.resolve(auth === 'token-2' ? ok('done') : expired())
    })

    await Promise.all([http('/api/a'), http('/api/b')])
    expect(fetchMock.mock.calls.filter(([input]) => input === '/api/auth/refresh')).toHaveLength(1)
  })

  it('clears the session when refresh fails', async () => {
    fetchMock
      .mockResolvedValueOnce(expired())
      .mockResolvedValueOnce(json(401, { code: 11004, message: '登录已失效', data: null }))

    await expect(http('/api/x')).rejects.toMatchObject({ code: 11003 })
    expect(useSession.getState()).toMatchObject({ accessToken: null, status: 'anonymous' })
  })
})
