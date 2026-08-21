import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from '../../auth/AuthContext'
import LandingHeader from './LandingHeader'

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }))

const useAuthMock = vi.mocked(useAuth)
const logoutMock = vi.fn().mockResolvedValue(undefined)

function renderHeader() {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <LandingHeader activeSection="home" isScrolled={false} onNavigate={vi.fn()} />
    </MemoryRouter>,
  )
}

function authenticatedAuthValue() {
  return {
    token: 'persisted-token',
    currentUser: {
      id: '7',
      username: 'audio-user',
      displayName: '音频创作者',
      avatarUrl: null,
    },
    authStatus: 'authenticated' as const,
    login: vi.fn(),
    register: vi.fn(),
    logout: logoutMock,
    changePassword: vi.fn(),
    loadCurrentUser: vi.fn(),
    updateCurrentUser: vi.fn(),
  }
}

describe('LandingHeader authentication state', () => {
  beforeEach(() => {
    logoutMock.mockClear()
    useAuthMock.mockReturnValue({
      token: null,
      currentUser: null,
      authStatus: 'anonymous',
      login: vi.fn(),
      register: vi.fn(),
      logout: logoutMock,
      changePassword: vi.fn(),
      loadCurrentUser: vi.fn(),
      updateCurrentUser: vi.fn(),
    })
  })

  it('shows only login when anonymous', () => {
    renderHeader()

    expect(screen.getByRole('link', { name: '登录' })).toHaveAttribute('href', '/login')
    expect(screen.queryByRole('link', { name: '立即体验' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '进入工作台' })).not.toBeInTheDocument()
  })

  it('shows the current user instead of login when authenticated', () => {
    useAuthMock.mockReturnValue(authenticatedAuthValue())

    renderHeader()

    expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument()
    expect(screen.getByText('音频创作者')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'audio-user的默认头像' })).toHaveTextContent('A')
    expect(screen.getByRole('button', { name: '打开音频创作者的用户菜单' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '进入工作台' })).toHaveAttribute('href', '/dashboard')
  })

  it('updates immediately when the shared authentication state changes', () => {
    const view = renderHeader()
    expect(screen.getByRole('link', { name: '登录' })).toBeInTheDocument()

    useAuthMock.mockReturnValue(authenticatedAuthValue())
    view.rerender(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <LandingHeader activeSection="home" isScrolled={false} onNavigate={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link', { name: '登录' })).not.toBeInTheDocument()
    expect(screen.getByText('音频创作者')).toBeInTheDocument()
  })

  it('reuses the existing logout action from the user menu', async () => {
    const user = userEvent.setup()
    useAuthMock.mockReturnValue(authenticatedAuthValue())
    renderHeader()

    await user.click(screen.getByRole('button', { name: '打开音频创作者的用户菜单' }))
    await user.click(await screen.findByText('退出登录'))

    expect(logoutMock).toHaveBeenCalledTimes(1)
  })
})
