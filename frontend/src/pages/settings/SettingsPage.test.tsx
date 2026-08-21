import { App } from 'antd'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import SettingsPage from './SettingsPage'

const mocks = vi.hoisted(() => {
  const settings = {
    defaultDenoiseStrength: 'LIGHT' as const,
    processingStrategy: 'CONSERVATIVE' as const,
    autoLimitPeak: false,
    requireStepConfirmation: true,
    preservePlaybackPosition: true,
    issueContextSeconds: 3,
    defaultPlaybackVolume: 0.8,
    defaultPageSize: 20 as const,
    notifyOnTaskComplete: true,
    autoOpenResultPage: true,
  }
  return {
    settings,
    save: vi.fn(),
    logout: vi.fn(),
    updateCurrentUser: vi.fn(),
  }
})

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    currentUser: {
      id: 'user-1',
      username: 'audio-user',
      displayName: '音频创作者',
      avatarUrl: null,
    },
    updateCurrentUser: mocks.updateCurrentUser,
    logout: mocks.logout,
  }),
}))

vi.mock('../../settings/UserSettingsContext', () => ({
  useUserSettings: () => ({
    settings: mocks.settings,
    loading: false,
    saving: false,
    error: null,
    refresh: vi.fn(),
    save: mocks.save,
  }),
}))

vi.mock('../../api/settings', () => ({
  updateCurrentUserProfile: vi.fn(),
}))

function renderPage() {
  return render(
    <App>
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>
    </App>,
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    window.localStorage.clear()
    mocks.save.mockReset()
    mocks.save.mockResolvedValue(mocks.settings)
  })

  it('renders only user-facing settings with balanced optimization selected by default', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '账号信息' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '音频处理偏好' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '通知设置' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'audio-user的默认头像' })).toHaveTextContent('A')
    expect(screen.getByRole('radio', { name: '平衡优化' })).toBeChecked()
    expect(screen.getByRole('button', { name: /修改密码$/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /退出登录$/ })).toBeInTheDocument()
    expect(screen.queryByText('播放与试听')).not.toBeInTheDocument()
    expect(screen.queryByText('默认分页数量')).not.toBeInTheDocument()
    expect(screen.queryByText('PERSONAL PREFERENCES')).not.toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /保存偏好设置$/ })
      .every((button) => button.hasAttribute('disabled'))).toBe(true)
    expect(within(screen.getByRole('contentinfo')).queryByText('偏好设置已保存')).not.toBeInTheDocument()
  })

  it('keeps the complete backend payload while translating the deep mode', async () => {
    const user = userEvent.setup()
    mocks.save.mockResolvedValue({
      ...mocks.settings,
      defaultDenoiseStrength: 'MEDIUM',
      processingStrategy: 'BALANCED',
      autoLimitPeak: true,
    })
    renderPage()

    const saveButtons = screen.getAllByRole('button', { name: /保存偏好设置$/ })
    const notificationSwitch = screen.getByRole('switch', { name: '任务完成提醒' })
    await user.click(notificationSwitch)
    expect(saveButtons.every((button) => !button.hasAttribute('disabled'))).toBe(true)
    await user.click(notificationSwitch)
    expect(saveButtons.every((button) => button.hasAttribute('disabled'))).toBe(true)

    await user.click(screen.getByText('深度优化'))
    expect(saveButtons.every((button) => !button.hasAttribute('disabled'))).toBe(true)
    await user.click(saveButtons[0])

    await waitFor(() => expect(mocks.save).toHaveBeenCalledWith({
      ...mocks.settings,
      defaultDenoiseStrength: 'MEDIUM',
      processingStrategy: 'BALANCED',
      autoLimitPeak: true,
    }))
    expect(window.localStorage.getItem('audioagent:processing-mode:user-1')).toBe('DEEP')
    await waitFor(() => expect(saveButtons.every((button) => button.hasAttribute('disabled'))).toBe(true))
    expect(within(screen.getByRole('contentinfo')).queryByText('偏好设置已保存')).not.toBeInTheDocument()
  })
})
