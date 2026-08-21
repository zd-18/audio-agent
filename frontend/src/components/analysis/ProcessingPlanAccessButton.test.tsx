import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { generateProcessingPlan, getProcessingPlan } from '../../api/processingPlan'
import { ApiError } from '../../api/http'
import type { ProcessingPlan } from '../../types/processingPlan'
import ProcessingPlanAccessButton from './ProcessingPlanAccessButton'

vi.mock('../../api/processingPlan', () => ({
  generateProcessingPlan: vi.fn(),
  getProcessingPlan: vi.fn(),
}))

const plan: ProcessingPlan = {
  planId: '401',
  taskId: '31',
  audioFileId: '21',
  planVersion: 1,
  planRevision: 1,
  planStatus: 'READY',
  summary: '统一响度',
  stepCount: 0,
  estimatedOutputDurationMs: null,
  steps: [],
  generatedAt: '2026-08-19T10:00:00Z',
}

describe('ProcessingPlanAccessButton', () => {
  it('creates a missing diagnosis plan once and enters the unified plan page', async () => {
    vi.mocked(getProcessingPlan).mockRejectedValue(new ApiError('不存在', 40209))
    vi.mocked(generateProcessingPlan).mockResolvedValue(plan)
    let currentPath = ''
    function LocationProbe() {
      const location = useLocation()
      currentPath = `${location.pathname}${location.search}`
      return null
    }
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <ProcessingPlanAccessButton taskId="31" status="SUCCESS" type="primary" />
        <LocationProbe />
      </MemoryRouter>,
    )

    const entry = await screen.findByRole('button', { name: /按推荐方案处理/ })
    await waitFor(() => expect(entry).not.toBeDisabled())
    await userEvent.click(entry)
    await userEvent.click(await screen.findByRole('button', { name: '生成并查看方案' }))

    await waitFor(() => expect(generateProcessingPlan).toHaveBeenCalledTimes(1))
    expect(currentPath).toBe('/analysis/tasks/31/processing-plan?source=diagnosis')
  })
})
