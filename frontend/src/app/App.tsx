import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { LoginPage, RegisterPage, RequireAuth } from '@/features/auth'
import { DocumentPage, DocumentSidebar, TrashPage } from '@/features/document'
import {
  CreateWorkspacePage,
  HomeRedirect,
  InvitePage,
  MembersPage,
  WorkspaceLayout,
  WorkspaceOverviewPage,
} from '@/features/workspace'
import { isNetworkError } from '@/shared/api/http'
import { refreshSession } from '@/shared/api/session'
import { Toaster } from '@/shared/ui/sonner'
import { TooltipProvider } from '@/shared/ui/tooltip'
import { SystemStatusPage } from './SystemStatusPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 业务错误（权限、不存在等）重试没有意义，只重试网络类错误
      retry: (failureCount, error) => isNetworkError(error) && failureCount < 2,
    },
  },
})

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/status', element: <SystemStatusPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <HomeRedirect /> },
      { path: '/workspaces/new', element: <CreateWorkspacePage /> },
      { path: '/invite/:token', element: <InvitePage /> },
      {
        path: '/w/:workspaceId',
        element: <WorkspaceLayout sidebar={<DocumentSidebar />} />,
        children: [
          { index: true, element: <WorkspaceOverviewPage /> },
          { path: 'members', element: <MembersPage /> },
          { path: 'd/:nodeId', element: <DocumentPage /> },
          { path: 'trash', element: <TrashPage /> },
        ],
      },
    ],
  },
])

export function App() {
  useEffect(() => {
    // 页面加载时用 Refresh Token Cookie 恢复登录状态
    void refreshSession()
  }, [])

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <RouterProvider router={router} />
      </TooltipProvider>
      <Toaster />
    </QueryClientProvider>
  )
}
