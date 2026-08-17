import { describe, expect, it } from 'vitest'
import type { ProcessingStepConfirmation } from '../types/processingConfirmation'
import type { ProcessingStep } from '../types/processingPlan'
import {
  getOperationLabel,
  getParameterDisplayItems,
  isExecutableProcessingOperation,
  isWholeAudioOperation,
} from './processingPlanDisplay'
import { buildStepUpdatePayload, createStepEditorDraft } from './processingParameterValidation'
import {
  getStageLabel,
  OPERATION_LABELS,
  STAGE_LABELS,
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

describe('SILENCE_CLEANUP display', () => {
  it('labels silence cleanup by mode on plan pages', () => {
    expect(getOperationLabel('SILENCE_CLEANUP', 'title',
      step('SILENCE_CLEANUP', { mode: 'COMPRESS' }))).toBe('压缩长静音')
    expect(getOperationLabel('SILENCE_CLEANUP', 'title',
      step('SILENCE_CLEANUP', { mode: 'REMOVE' }))).toBe('删除长静音')
    expect(getOperationLabel('SILENCE_CLEANUP', 'title',
      step('SILENCE_CLEANUP', {}))).toBe('长静音处理')
    expect(getOperationLabel('SILENCE_CLEANUP')).toBe('长静音处理')
  })

  it('keeps the execution page label static and consistent', () => {
    expect(OPERATION_LABELS.SILENCE_CLEANUP).toBe('长静音处理')
  })

  it('treats silence cleanup as executable whole-audio operation', () => {
    expect(isExecutableProcessingOperation('SILENCE_CLEANUP')).toBe(true)
    expect(isWholeAudioOperation('SILENCE_CLEANUP')).toBe(true)
  })

  it('shows compress mode with user-facing threshold and keep duration', () => {
    const items = getParameterDisplayItems(
      step('SILENCE_CLEANUP', { mode: 'COMPRESS', minSilenceMs: 3000, keepSilenceMs: 800 }),
    )

    expect(items).toEqual([
      { label: '处理模式', value: '压缩长静音' },
      { label: '处理阈值', value: '处理超过 3 秒的长停顿' },
      { label: '保留停顿', value: '每段保留约 0.8 秒自然停顿' },
      { label: '处理范围', value: '整段音频' },
    ])
  })

  it('shows remove mode without keep-duration detail', () => {
    const items = getParameterDisplayItems(
      step('SILENCE_CLEANUP', { mode: 'REMOVE', minSilenceMs: 3000 }),
    )

    expect(items).toEqual([
      { label: '处理模式', value: '删除长静音' },
      { label: '处理阈值', value: '处理超过 3 秒的长停顿' },
      { label: '处理范围', value: '整段音频' },
    ])
  })

  it('never exposes technical parameters to users', () => {
    const items = getParameterDisplayItems(
      step('SILENCE_CLEANUP', { mode: 'COMPRESS', minSilenceMs: 3000, keepSilenceMs: 800 }),
    )
    const visible = JSON.stringify(items)

    expect(visible).not.toContain('silencedetect')
    expect(visible).not.toContain('dB')
    expect(visible).not.toContain('FFmpeg')
    expect(visible).not.toContain('filter')
  })

  it('labels the silence cleaning execution stage understandably', () => {
    expect(getStageLabel('SILENCE_CLEANING')).toBe('正在处理长停顿')
    expect(STAGE_LABELS.SILENCE_CLEANING).toBe('正在处理长停顿')
  })
})

describe('SILENCE_CLEANUP parameter editing', () => {
  function confirmationStep(parameters: Record<string, unknown>): ProcessingStepConfirmation {
    return {
      stepConfirmationId: 'c1',
      sourceStepId: 's1',
      stepOrder: 1,
      operationType: 'SILENCE_CLEANUP',
      decision: 'PENDING',
      userConfirmed: false,
      requiresConfirmation: true,
      startMs: null,
      endMs: null,
      originalParameters: parameters,
      parameterOverrides: {},
      effectiveParameters: { ...parameters },
      userNote: null,
    }
  }

  it('loads editable parameters into the draft', () => {
    const draft = createStepEditorDraft(confirmationStep({
      mode: 'COMPRESS',
      minSilenceMs: 3000,
      keepSilenceMs: 800,
    }))

    expect(draft.parameterValues).toEqual({
      mode: 'COMPRESS',
      minSilenceMs: '3000',
      keepSilenceMs: '800',
    })
  })

  it('builds a payload with changed parameters only', () => {
    const result = buildStepUpdatePayload(
      confirmationStep({ mode: 'COMPRESS', minSilenceMs: 3000, keepSilenceMs: 800 }),
      {
        decision: 'ACCEPTED',
        userConfirmed: true,
        parameterValues: { mode: 'REMOVE', minSilenceMs: '4000', keepSilenceMs: '800' },
        userNote: '',
      },
    )

    expect(result.fieldErrors).toEqual({})
    expect(result.payload?.parameterOverrides).toEqual({
      mode: 'REMOVE',
      minSilenceMs: 4000,
    })
  })

  it('rejects out-of-range minSilenceMs and keepSilenceMs', () => {
    const minError = buildStepUpdatePayload(
      confirmationStep({ mode: 'COMPRESS', minSilenceMs: 3000, keepSilenceMs: 800 }),
      {
        decision: 'ACCEPTED',
        userConfirmed: true,
        parameterValues: { mode: 'COMPRESS', minSilenceMs: '500', keepSilenceMs: '800' },
        userNote: '',
      },
    )
    const keepError = buildStepUpdatePayload(
      confirmationStep({ mode: 'COMPRESS', minSilenceMs: 3000, keepSilenceMs: 800 }),
      {
        decision: 'ACCEPTED',
        userConfirmed: true,
        parameterValues: { mode: 'COMPRESS', minSilenceMs: '3000', keepSilenceMs: '8000' },
        userNote: '',
      },
    )

    expect(minError.payload).toBeNull()
    expect(minError.fieldErrors.minSilenceMs).toBeTruthy()
    expect(keepError.payload).toBeNull()
    expect(keepError.fieldErrors.keepSilenceMs).toBeTruthy()
  })

  it('rejects invalid mode option', () => {
    const result = buildStepUpdatePayload(
      confirmationStep({ mode: 'COMPRESS', minSilenceMs: 3000, keepSilenceMs: 800 }),
      {
        decision: 'ACCEPTED',
        userConfirmed: true,
        parameterValues: { mode: 'TRUNCATE', minSilenceMs: '3000', keepSilenceMs: '800' },
        userNote: '',
      },
    )

    expect(result.payload).toBeNull()
    expect(result.fieldErrors.mode).toBeTruthy()
  })
})
