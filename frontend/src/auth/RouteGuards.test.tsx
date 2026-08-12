import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from './AuthContext'
import { ProtectedRoute } from './RouteGuards'

vi.mock('./AuthContext', () => ({ useAuth: vi.fn() }))

const useAuthMock = vi.mocked(useAuth)

describe('ProtectedRoute', () => {
  beforeEach(() => {
    useAuthMock.mockReturnValue({
      token: null,
      currentUser: null,
      authStatus: 'anonymous',
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      changePassword: vi.fn(),
      loadCurrentUser: vi.fn(),
      updateCurrentUser: vi.fn(),
    })
  })

  it('redirects an unauthenticated transcription URL to login', () => {
    render(
      <MemoryRouter
        initialEntries={['/transcriptions/9007199254740995']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/transcriptions/:taskId" element={<div>private transcript</div>} />
          </Route>
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('private transcript')).not.toBeInTheDocument()
  })
})
