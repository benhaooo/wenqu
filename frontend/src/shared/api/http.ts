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

/** 生成代码使用的请求函数：返回响应体，code 非 0 或 HTTP 出错时抛出 ApiError。 */
export async function http<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  const body = parseJson(await response.text())

  if (!isApiResultBody(body)) {
    throw new ApiError(-1, `接口返回格式错误（HTTP ${String(response.status)}）`, response.status)
  }
  if (!response.ok || body.code !== 0) {
    throw new ApiError(body.code, body.message, response.status)
  }
  return body as T
}
