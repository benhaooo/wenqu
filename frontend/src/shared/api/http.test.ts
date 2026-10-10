import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, http, httpStream, SseParser } from './http'
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

function eventStream(...chunks: string[]) {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder()
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
}

describe('SseParser', () => {
  it('splits events across chunks and joins multi-line data', () => {
    const parser = new SseParser()

    expect(parser.push('event:token\ndata:{"a"')).toEqual([])
    expect(parser.push(':1}\n\ndata: line1\r\ndata: line2\r\n\n')).toEqual([
      '{"a":1}',
      'line1\nline2',
    ])
  })
})

describe('httpStream', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('token-1')
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('delivers each event data as JSON', async () => {
    fetchMock.mockResolvedValueOnce(
      eventStream('event:token\ndata:{"text":"你"}\n\nevent:to', 'ken\ndata:{"text":"好"}\n\n'),
    )
    const events: string[] = []

    await httpStream('/api/s', { method: 'POST' }, (data) => events.push(data))

    expect(events).toEqual(['{"text":"你"}', '{"text":"好"}'])
  })

  it('throws the API error returned before streaming starts', async () => {
    fetchMock.mockResolvedValueOnce(
      json(503, { code: 16004, message: 'AI 服务暂时不可用', data: null }),
    )

    await expect(httpStream('/api/s', { method: 'POST' }, vi.fn())).rejects.toEqual(
      new ApiError(16004, 'AI 服务暂时不可用', 503),
    )
  })

  it('refreshes an expired token and retries once', async () => {
    fetchMock
      .mockResolvedValueOnce(expired())
      .mockResolvedValueOnce(ok({ accessToken: 'token-2' }))
      .mockResolvedValueOnce(eventStream('data:{"type":"DONE"}\n\n'))
    const onEvent = vi.fn()

    await httpStream('/api/s', { method: 'POST' }, onEvent)

    expect(onEvent).toHaveBeenCalledWith('{"type":"DONE"}')
    expect(new Headers(fetchMock.mock.calls[2]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer token-2',
    )
  })
})
