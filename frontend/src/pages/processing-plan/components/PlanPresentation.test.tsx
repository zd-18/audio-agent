import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ProcessingPlan } from '../../../types/processingPlan'
import PlanAnalysisBasis from './PlanAnalysisBasis'
import PlanSummary from './PlanSummary'
import ProcessingStepList from './ProcessingStepList'

const plan: ProcessingPlan = {
  planId: 'plan-1',
  taskId: 'task-1',
  audioFileId: 'audio-1',
  planVersion: 1,
  planRevision: 1,
  planStatus: 'READY',
  summary: '建议先降低背景噪声，再统一整段响度。',
  stepCount: 2,
  estimatedOutputDurationMs: 60_000,
  generatedAt: '2026-08-21T08:00:00Z',
  steps: [
    {
      stepId: 'step-1',
      stepOrder: 1,
      operationType: 'DENOISE_REVIEW',
      title: '降低背景噪声',
      description: '减弱持续底噪，同时保留人声细节。',
      sourceIssueId: 'issue-1',
      startMs: 1_000,
      endMs: 8_000,
      priority: 'HIGH',
      riskLevel: 'LOW',
      requiresConfirmation: true,
      parameters: { confidence: 0.92 },
      reason: '检测到持续底噪。',
    },
    {
      stepId: 'step-2',
      stepOrder: 2,
      operationType: 'NORMALIZE_LOUDNESS',
      title: '统一整段响度',
      description: '让整体音量更稳定。',
      sourceIssueId: null,
      startMs: null,
      endMs: null,
      priority: 'MEDIUM',
      riskLevel: 'LOW',
      requiresConfirmation: false,
      parameters: {},
      reason: '整体响度偏低。',
    },
  ],
}

describe('processing plan presentation', () => {
  it('keeps the summary focused on detected issues and processing advice', () => {
    render(<PlanSummary plan={plan} />)

    expect(screen.getByText('检测问题')).toBeInTheDocument()
    expect(screen.getByText(/检测到持续底噪/)).toBeInTheDocument()
    expect(screen.getByText('处理建议')).toBeInTheDocument()
    expect(screen.getByText(plan.summary!)).toBeInTheDocument()
    expect(screen.queryByText('后端方案记录')).not.toBeInTheDocument()
  })

  it('shows analysis metrics and the sources already present in the plan', () => {
    render(<PlanAnalysisBasis plan={plan} />)

    const basis = screen.getByRole('region', { name: 'AI 分析依据' })
    expect(within(basis).getByText('关联问题').nextSibling).toHaveTextContent('1')
    expect(within(basis).getByText('问题片段').nextSibling).toHaveTextContent('1')
    expect(within(basis).getByText('优先处理').nextSibling).toHaveTextContent('1')
    expect(within(basis).getByText('音频问题检测结果')).toBeInTheDocument()
    expect(within(basis).getByText('模型置信度')).toBeInTheDocument()
  })

  it('hides zero metrics and describes the detection result when no count is available', () => {
    const planWithoutMetrics: ProcessingPlan = {
      ...plan,
      steps: [{
        ...plan.steps[1],
        priority: 'MEDIUM',
        reason: '整体响度偏低，各片段听感不一致。',
      }],
    }
    render(<PlanAnalysisBasis plan={planWithoutMetrics} />)

    const basis = screen.getByRole('region', { name: 'AI 分析依据' })
    expect(within(basis).queryByText('关联问题')).not.toBeInTheDocument()
    expect(within(basis).queryByText('问题片段')).not.toBeInTheDocument()
    expect(within(basis).queryByText('优先处理')).not.toBeInTheDocument()
    expect(within(basis).getByText('检测结果')).toBeInTheDocument()
    expect(within(basis).getByText('整体响度偏低，各片段听感不一致。')).toBeInTheDocument()
    expect(within(basis).getByText('步骤分析原因')).toBeInTheDocument()
  })

  it('uses a step name and short description in the left navigation', () => {
    render(
      <ProcessingStepList
        steps={plan.steps}
        selectedStepId="step-1"
        onSelect={vi.fn()}
      />,
    )

    const selectedStep = screen.getByRole('button', { name: /降低噪声/ })
    expect(within(selectedStep).getByText('检查疑似噪声片段，确认是否需要降噪。')).toBeInTheDocument()
    expect(selectedStep).toHaveAttribute('aria-current', 'step')
  })
})
