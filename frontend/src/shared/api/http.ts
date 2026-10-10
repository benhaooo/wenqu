import { refreshSession, useSession } from './session'

/** 后端统一响应格式中的错误，见 docs/conventions/api.md。 */
export class ApiError extends Error {
  readonly code: number
  readonly status: number

  constructor(code: number, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

/** 响应不是统一格式（网关错误、代理连不上后端）或请求本身失败，可以重试。 */
export function isNetworkError(error: unknown): boolean {
  return !(error instanceof ApiError) || error.code === NOT_API_RESULT
}

/** 生成的 TanStack Query hooks 使用的错误类型，泛型参数是 orval 约定的签名。 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export type ErrorType<_Error> = ApiError

/** 响应体不是统一格式时使用的错误码。 */
const NOT_API_RESULT = -1

/** Access Token 无效或过期，见 docs/modules/auth.md。 */
const UNAUTHENTICATED = 11003

interface ApiResultBody {
  code: number
  message: string
}

function isApiResultBody(value: unknown): value is ApiResultBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiResultBody).code === 'number' &&
    typeof (value as ApiResultBody).message === 'string'
  )
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function withAuthorization(init: RequestInit | undefined): RequestInit {
  const { accessToken } = useSession.getState()
  const headers = new Headers(init?.headers)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  return { ...init, headers }
}

function toApiError(response: Response, body: unknown): ApiError {
  if (!isApiResultBody(body)) {
    return new ApiError(
      NOT_API_RESULT,
      `服务暂时不可用（HTTP ${String(response.status)}）`,
      response.status,
    )
  }
  return new ApiError(body.code, body.message, response.status)
}

/**
 * 生成代码使用的请求函数：附带 Access Token，过期时自动刷新并重试一次；
 * 返回响应体，code 非 0 或 HTTP 出错时抛出 ApiError。
 */
export async function http<T>(url: string, init?: RequestInit): Promise<T> {
  let response = await fetch(url, withAuthorization(init))
  let body = parseJson(await response.text())

  if (isApiResultBody(body) && body.code === UNAUTHENTICATED && (await refreshSession())) {
    response = await fetch(url, withAuthorization(init))
    body = parseJson(await response.text())
  }

  if (!isApiResultBody(body) || !response.ok || body.code !== 0) {
    throw toApiError(response, body)
  }
  return body as T
}

function isEventStream(response: Response): boolean {
  return response.ok && (response.headers.get('Content-Type') ?? '').startsWith('text/event-stream')
}

/**
 * 请求流式接口（SSE）并逐个回调事件的 data。开始输出前的错误（校验、鉴权、服务不可用）
 * 以 ApiError 抛出，Access Token 过期时刷新后重试一次。用 fetch 而不是 EventSource，
 * 因为需要携带 Authorization 头；通过 init.signal 中止即停止接收，服务端随之停止生成。
 */
export async function httpStream(
  url: string,
  init: RequestInit,
  onData: (data: string) => void,
): Promise<void> {
  let response = await fetch(url, withAuthorization(init))
  if (!isEventStream(response)) {
    const body = parseJson(await response.text())
    if (!(isApiResultBody(body) && body.code === UNAUTHENTICATED && (await refreshSession()))) {
      throw toApiError(response, body)
    }
    response = await fetch(url, withAuthorization(init))
    if (!isEventStream(response)) throw toApiError(response, parseJson(await response.text()))
  }
  if (!response.body) return
  const parser = new SseParser()
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    for (const data of parser.push(value)) onData(data)
  }
}

/** 按 SSE 格式切分事件，返回每个完整事件的 data（多行 data 以换行连接）。 */
export class SseParser {
  private buffer = ''

  push(chunk: string): string[] {
    this.buffer += chunk.replaceAll('\r\n', '\n')
    const events: string[] = []
    let end = this.buffer.indexOf('\n\n')
    while (end >= 0) {
      const lines = this.buffer.slice(0, end).split('\n')
      this.buffer = this.buffer.slice(end + 2)
      const data = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).replace(/^ /, ''))
      if (data.length > 0) events.push(data.join('\n'))
      end = this.buffer.indexOf('\n\n')
    }
    return events
  }
}
