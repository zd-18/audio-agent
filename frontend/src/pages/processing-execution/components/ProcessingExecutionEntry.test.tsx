import { App as AntdApp } from 'antd'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createProcessingExecution } from '../../../api/processingExecution'
import { useProcessingExecution } from '../../../hooks/useProcessingExecution'
import type { ProcessingConfirmation } from '../../../types/processingConfirmation'
import ProcessingExecutionEntry from './ProcessingExecutionEntry'

vi.mock('../../../api/processingExecution', () => ({
  createProcessingExecution: vi.fn(),
}))
vi.mock('../../../hooks/useProcessingExecution', () => ({
  useProcessingExecution: vi.fn(),
}))

const createMock = vi.mocked(createProcessingExecution)
const executionStateMock = vi.mocked(useProcessingExecution)

const confirmation: ProcessingConfirmation = {
  confirmationId: '6001',
  taskId: '1001',
  audioFileId: '2001',
  planId: '3001',
  sourcePlanRevision: 1,
  confirmationStatus: 'CONFIRMED',
  acceptedStepCount: 1,
  rejectedStepCount: 0,
  pendingStepCount: 0,
  resultMessage: null,
  steps: [],
  confirmedAt: '2026-08-12T10:00:00',
  createdAt: '2026-08-12T09:59:00',
  updatedAt: '2026-08-12T10:00:00',
}

describe('ProcessingExecutionEntry', () => {
  beforeEach(() => {
    createMock.mockReset()
    executionStateMock.mockReturnValue({
      execution: null,
      loading: false,
      refreshing: false,
      retrying: false,
      cancelling: false,
      error: null,
      notFound: true,
      refresh: vi.fn(),
      retry: vi.fn(),
      cancel: vi.fn(),
    })
  })

  it('locks repeated start clicks to one execution request', async () => {
    let resolveRequest!: () => void
    createMock.mockImplementation(() => new Promise((resolve) => {
      resolveRequest = () => resolve({} as never)
    }))
    render(
      <AntdApp>
        <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <ProcessingExecutionEntry confirmation={confirmation} />
        </MemoryRouter>
      </AntdApp>,
    )

    await userEvent.click(screen.getByRole('button', { name: /开始处理/ }))
    const confirmButton = await screen.findByRole('button', { name: /确认开始处理/ })
    await userEvent.dblClick(confirmButton)

    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1))
    expect(createMock).toHaveBeenCalledWith('6001', expect.any(AbortSignal))
    resolveRequest()
  })

  it('requires regeneration for an accepted legacy operation', async () => {
    const onRegenerate = vi.fn()
    const legacyConfirmation: ProcessingConfirmation = {
      ...confirmation,
      steps: [{
        stepConfirmationId: '7001',
        sourceStepId: '4001',
        stepOrder: 1,
        operationType: 'LIMIT_PEAK',
        title: '控制过高峰值',
        decision: 'ACCEPTED',
        userConfirmed: true,
        requiresConfirmation: true,
        startMs: null,
        endMs: null,
        originalParameters: { truePeakLimitDbfs: -1 },
        parameterOverrides: {},
        effectiveParameters: { truePeakLimitDbfs: -1 },
        userNote: null,
      }],
    }
    render(
      <AntdApp>
        <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <ProcessingExecutionEntry
            confirmation={legacyConfirmation}
            onRegenerate={onRegenerate}
          />
        </MemoryRouter>
      </AntdApp>,
    )

    expect(screen.getByText('当前确认基于旧版处理方案')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^开始处理$/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '重新生成处理方案' }))
    expect(onRegenerate).toHaveBeenCalledTimes(1)
    expect(createMock).not.toHaveBeenCalled()
  })
})
