import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { HomePage } from './HomePage'

const queryClient = new QueryClient()

const router = createBrowserRouter([{ path: '/', element: <HomePage /> }])

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}
