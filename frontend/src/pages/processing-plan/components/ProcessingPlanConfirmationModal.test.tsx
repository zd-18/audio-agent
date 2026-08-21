import { App as AntdApp } from 'antd'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ProcessingPlan } from '../../../types/processingPlan'
import ProcessingPlanConfirmationModal from './ProcessingPlanConfirmationModal'
import StepParameters from './StepParameters'

const plan: ProcessingPlan = {
  planId: '3001',
  taskId: '1001',
  audioFileId: '2001',
  planVersion: 1,
  planRevision: 1,
  planStatus: 'READY',
  summary: '先降低背景噪声，再统一整体响度。',
  stepCount: 1,
  estimatedOutputDurationMs: 60_000,
  generatedAt: '2026-08-21T10:00:00',
  steps: [{
    stepId: '4001',
    stepOrder: 1,
    operationType: 'NORMALIZE_VOLUME',
    sourceIssueId: null,
    startMs: null,
    endMs: null,
    priority: 'HIGH',
    riskLevel: 'LOW',
    requiresConfirmation: true,
    parameters: { targetLufs: -16, truePeakLimitDbfs: -1 },
  }],
}

describe('ProcessingPlanConfirmationModal', () => {
  it('shows the compact review fields and starts with the optional note', async () => {
    const onStart = vi.fn().mockResolvedValue(undefined)
    render(
      <AntdApp>
        <ProcessingPlanConfirmationModal
          open
          audioName="demo.wav"
          plan={plan}
          starting={false}
          error={null}
          onCancel={vi.fn()}
          onStart={onStart}
        />
      </AntdApp>,
    )

    expect(screen.getByText('demo.wav')).toBeInTheDocument()
    expect(screen.getByText(plan.summary!)).toBeInTheDocument()
    expect(screen.getByText('-16 LUFS')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('可选备注'), { target: { value: '人声优先' } })
    fireEvent.click(screen.getByRole('button', { name: '开始处理' }))

    expect(onStart).toHaveBeenCalledWith('人声优先')
  })

  it('keeps advanced parameters collapsed by default', () => {
    render(<StepParameters step={plan.steps[0]} />)

    expect(screen.getByRole('button', { name: /高级参数/ })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByLabelText('参数摘要')).toBeInTheDocument()
  })
})
