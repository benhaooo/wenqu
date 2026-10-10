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

  if (!isApiResultBody(body)) {
    throw new ApiError(
      NOT_API_RESULT,
      `服务暂时不可用（HTTP ${String(response.status)}）`,
      response.status,
    )
  }
  if (!response.ok || body.code !== 0) {
    throw new ApiError(body.code, body.message, response.status)
  }
  return body as T
}
