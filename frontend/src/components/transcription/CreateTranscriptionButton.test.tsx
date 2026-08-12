import { App as AntdApp } from 'antd'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CreateTranscriptionButton from './CreateTranscriptionButton'

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  clearError: vi.fn(),
  navigate: vi.fn(),
}))

vi.mock('../../hooks/useCreateTranscription', () => ({
  useCreateTranscription: () => ({
    create: mocks.create,
    loading: false,
    error: null,
    clearError: mocks.clearError,
  }),
}))

vi.mock('react-router-dom', async (importOriginal) => {
  const original = await importOriginal<typeof import('react-router-dom')>()
  return { ...original, useNavigate: () => mocks.navigate }
})

function renderButton(props?: { taskId?: string; status?: string }) {
  return render(
    <AntdApp>
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CreateTranscriptionButton audioFileId="9007199254740993" {...props} />
      </MemoryRouter>
    </AntdApp>,
  )
}

describe('CreateTranscriptionButton', () => {
  beforeEach(() => {
    mocks.create.mockReset()
    mocks.clearError.mockReset()
    mocks.navigate.mockReset()
  })

  it('creates a task from a string file ID and opens its detail page', async () => {
    mocks.create.mockResolvedValue({ taskId: '9007199254740995' })
    renderButton()

    await userEvent.click(screen.getByRole('button', { name: /开始转写/ }))

    expect(mocks.create).toHaveBeenCalledWith('9007199254740993')
    await waitFor(() => {
      expect(mocks.navigate).toHaveBeenCalledWith(
        '/transcriptions/9007199254740995',
      )
    })
  })

  it('opens an existing successful transcript without creating another task', async () => {
    renderButton({ taskId: '9007199254740995', status: 'SUCCESS' })

    await userEvent.click(screen.getByRole('button', { name: /查看文字稿/ }))

    expect(mocks.create).not.toHaveBeenCalled()
    expect(mocks.navigate).toHaveBeenCalledWith(
      '/transcriptions/9007199254740995',
    )
  })
})
