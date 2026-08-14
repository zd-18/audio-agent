import { describe, expect, it } from 'vitest'
import type { ProcessingStep } from '../types/processingPlan'
import {
  getOperationLabel,
  getParameterDisplayItems,
  isExecutableProcessingOperation,
  isWholeAudioOperation,
} from './processingPlanDisplay'
import {
  getStageLabel,
  OPERATION_LABELS,
} from './processingExecutionDisplay'

function step(operationType: ProcessingStep['operationType'], parameters: Record<string, unknown> = {}): ProcessingStep {
  return {
    stepId: '1',
    stepOrder: 1,
    operationType,
    sourceIssueId: null,
    startMs: null,
    endMs: null,
    priority: 'MEDIUM',
    riskLevel: 'MEDIUM',
    requiresConfirmation: true,
    parameters,
  }
}

describe('DENOISE display', () => {
  it('labels denoise as 智能降噪 in plan and execution pages', () => {
    expect(getOperationLabel('DENOISE')).toBe('智能降噪')
    expect(OPERATION_LABELS.DENOISE).toBe('智能降噪')
  })

  it('treats denoise as executable whole-audio operation', () => {
    expect(isExecutableProcessingOperation('DENOISE')).toBe(true)
    expect(isWholeAudioOperation('DENOISE')).toBe(true)
  })

  it('shows user-facing strength without ffmpeg details', () => {
    const items = getParameterDisplayItems(
      step('DENOISE', { strength: 'MEDIUM' }),
    )

    expect(items).toEqual([
      { label: '降噪强度', value: '中等' },
      { label: '处理范围', value: '整段音频' },
    ])
  })

  it('falls back gracefully when strength is missing', () => {
    const items = getParameterDisplayItems(step('DENOISE'))

    expect(items[0]).toEqual({ label: '降噪强度', value: '按检测结果处理' })
  })

  it('labels the denoising execution stage', () => {
    expect(getStageLabel('DENOISING')).toBe('正在处理音频')
  })
})
