import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from '@/shared/api/session'

/** 需要登录的路由：未登录时跳转登录页，并在登录后回到原页面。 */
export function RequireAuth() {
  const status = useSession((state) => state.status)
  const signedOut = useSession((state) => state.signedOut)
  const location = useLocation()

  if (status === 'unknown') {
    return (
      <div className="flex min-h-svh items-center justify-center text-muted-foreground">
        加载中…
      </div>
    )
  }
  if (status === 'anonymous') {
    if (signedOut) return <Navigate to="/login" replace />
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  return <Outlet />
}
