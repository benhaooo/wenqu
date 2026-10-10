import { cleanup, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { clearSession, setAccessToken } from '@/shared/api/session'
import { RequireAuth } from './RequireAuth'

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname + location.search}</div>
}

function renderAt(path: string) {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LocationProbe /> },
      { element: <RequireAuth />, children: [{ path: '/w/:id', element: <p>空间页</p> }] },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
}

describe('RequireAuth', () => {
  afterEach(cleanup)

  it('renders the page when authenticated', () => {
    setAccessToken('token')
    renderAt('/w/1')
    expect(screen.getByText('空间页')).toBeDefined()
  })

  it('remembers the page when the session expired', () => {
    clearSession()
    renderAt('/w/1?tab=members')
    expect(screen.getByTestId('location').textContent).toBe(
      `/login?redirect=${encodeURIComponent('/w/1?tab=members')}`,
    )
  })

  it('does not remember the page after signing out', () => {
    clearSession({ signedOut: true })
    renderAt('/w/1')
    expect(screen.getByTestId('location').textContent).toBe('/login')
  })
})
