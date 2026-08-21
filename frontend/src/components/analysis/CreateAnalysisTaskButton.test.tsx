import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { useCreateAnalysisTask } from '../../hooks/useCreateAnalysisTask'
import CreateAnalysisTaskButton from './CreateAnalysisTaskButton'

vi.mock('../../hooks/useCreateAnalysisTask', () => ({
  useCreateAnalysisTask: vi.fn(),
}))

describe('CreateAnalysisTaskButton', () => {
  it('uses the smart diagnosis terminology on the file entry and dialog', async () => {
    vi.mocked(useCreateAnalysisTask).mockReturnValue({
      create: vi.fn(),
      loading: false,
      error: null,
      resetError: vi.fn(),
    })
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CreateAnalysisTaskButton audioFileId="21" fileName="demo.wav" />
      </MemoryRouter>,
    )

    await userEvent.click(screen.getByRole('button', { name: /开始智能诊断/ }))

    expect(screen.getByRole('dialog')).toHaveTextContent('开始智能诊断')
    expect(screen.queryByRole('button', { name: /开始分析/ })).not.toBeInTheDocument()
  })
})
