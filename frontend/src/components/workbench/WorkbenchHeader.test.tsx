import { App } from 'antd'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import WorkbenchHeader from './WorkbenchHeader'

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    currentUser: {
      id: 'user-1',
      username: '测试用户',
      displayName: '音频创作者',
      avatarUrl: null,
    },
    logout: vi.fn(),
  }),
}))

describe('WorkbenchHeader', () => {
  it('shows the username-generated avatar in the top user menu', () => {
    render(
      <App>
        <MemoryRouter initialEntries={['/dashboard']}>
          <WorkbenchHeader onOpenMenu={vi.fn()} />
        </MemoryRouter>
      </App>,
    )

    expect(screen.getByRole('img', { name: '测试用户的默认头像' })).toHaveTextContent('测')
    expect(screen.getByRole('button', { name: '打开用户菜单' })).toBeInTheDocument()
  })
})
