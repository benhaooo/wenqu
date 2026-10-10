import { useQueryClient } from '@tanstack/react-query'
import { logout } from '@/shared/api/generated/auth/auth'
import { clearSession } from '@/shared/api/session'

/** 退出登录后由 RequireAuth 跳转到登录页。 */
export function useSignOut() {
  const queryClient = useQueryClient()

  return async () => {
    try {
      await logout()
    } finally {
      clearSession({ signedOut: true })
      queryClient.clear()
    }
  }
}
