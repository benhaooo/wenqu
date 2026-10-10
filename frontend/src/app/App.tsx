import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AuthPage, RequireAuth } from '@/features/auth'
import { AskAiLink, ChatNavLink, ChatPage } from '@/features/chat'
import {
  DocumentHomePage,
  DocumentPage,
  SidebarSearch,
  TrashNavLink,
  TrashPage,
} from '@/features/document'
import { ImportNavLink, ImportPage, ReindexSection, SourceFileButton } from '@/features/ingestion'
import {
  CreateWorkspacePage,
  HomeRedirect,
  InvitePage,
  SettingsPage,
  WorkspaceLayout,
} from '@/features/workspace'
import { isNetworkError } from '@/shared/api/http'
import { refreshSession } from '@/shared/api/session'
import { Toaster } from '@/shared/ui/sonner'
import { SidebarSecondary } from './SidebarSecondary'
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
  { path: '/login', element: <AuthPage mode="login" /> },
  { path: '/register', element: <AuthPage mode="register" /> },
  { path: '/status', element: <SystemStatusPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <HomeRedirect /> },
      { path: '/workspaces/new', element: <CreateWorkspacePage /> },
      { path: '/invite/:token', element: <InvitePage /> },
      {
        path: '/w/:workspaceId',
        element: (
          <WorkspaceLayout
            search={<SidebarSearch />}
            navItems={
              <>
                <ChatNavLink />
                <ImportNavLink />
                <TrashNavLink />
              </>
            }
            tree={<SidebarSecondary />}
          />
        ),
        children: [
          { index: true, element: <DocumentHomePage /> },
          { path: 'settings', element: <SettingsPage generalSections={<ReindexSection />} /> },
          {
            path: 'd/:nodeId',
            element: (
              <DocumentPage
                headerActions={(documentId) => (
                  <>
                    <SourceFileButton documentId={documentId} />
                    <AskAiLink documentId={documentId} />
                  </>
                )}
              />
            ),
          },
          { path: 'trash', element: <TrashPage /> },
          { path: 'import', element: <ImportPage /> },
          // 同一个路由元素：新会话发出第一个问题后切换到会话地址，流式回答不中断
          { path: 'chat/:conversationId?', element: <ChatPage /> },
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
      <RouterProvider router={router} />
      <Toaster />
    </QueryClientProvider>
  )
}
