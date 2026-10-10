import { create } from 'zustand'

/**
 * 登录态：Access Token 只保存在内存中，Refresh Token 在 HttpOnly Cookie 中由浏览器管理。
 * - status 为 unknown 表示页面刚加载、尚未尝试用 Cookie 恢复登录。
 * - signedOut 区分主动退出与登录失效：失效时登录后回到原页面，主动退出则不记录原页面，
 *   避免下一个登录的用户被带到上一个用户的页面。
 */
interface SessionState {
  accessToken: string | null
  status: 'unknown' | 'authenticated' | 'anonymous'
  signedOut: boolean
}

export const useSession = create<SessionState>(() => ({
  accessToken: null,
  status: 'unknown',
  signedOut: false,
}))

export function setAccessToken(accessToken: string) {
  useSession.setState({ accessToken, status: 'authenticated', signedOut: false })
}

export function clearSession({ signedOut = false }: { signedOut?: boolean } = {}) {
  useSession.setState({ accessToken: null, status: 'anonymous', signedOut })
}

interface RefreshBody {
  code: number
  data: { accessToken: string } | null
}

let inflight: Promise<boolean> | null = null

/**
 * 用 Cookie 中的 Refresh Token 换取新的 Access Token，返回是否成功。
 * 同一页面内并发调用只发一次请求；跨标签页用 Web Locks 串行，避免两个标签页同时使用同一个
 * Refresh Token，被后端当作重用而作废整个登录。
 */
export function refreshSession(): Promise<boolean> {
  inflight ??= withLock(doRefresh).finally(() => {
    inflight = null
  })
  return inflight
}

async function doRefresh(): Promise<boolean> {
  try {
    const response = await fetch('/api/auth/refresh', { method: 'POST' })
    const body = (await response.json()) as RefreshBody
    if (response.ok && body.code === 0 && body.data) {
      setAccessToken(body.data.accessToken)
      return true
    }
  } catch {
    // 网络错误按刷新失败处理
  }
  clearSession()
  return false
}

function withLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && 'locks' in navigator) {
    return navigator.locks.request('wenqu-session-refresh', task)
  }
  return task()
}
