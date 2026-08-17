import { describe, expect, it } from 'vitest'
import type { ProcessingStepConfirmation } from '../types/processingConfirmation'
import type { ProcessingOperationType } from '../types/processingPlan'
import {
  buildStepUpdatePayload,
  createStepEditorDraft,
} from './processingParameterValidation'

function confirmationStep(
  operationType: ProcessingOperationType,
  parameters: Record<string, unknown>,
  startMs: number | null = null,
  endMs: number | null = null,
): ProcessingStepConfirmation {
  return {
    stepConfirmationId: '101',
    sourceStepId: '201',
    stepOrder: 1,
    operationType,
    decision: 'ACCEPTED',
    userConfirmed: true,
    requiresConfirmation: true,
    startMs,
    endMs,
    originalParameters: parameters,
    parameterOverrides: {},
    effectiveParameters: parameters,
    userNote: null,
  }
}

function validate(step: ProcessingStepConfirmation) {
  return buildStepUpdatePayload(step, createStepEditorDraft(step))
}

describe('processing parameter validation by operation type', () => {
  it('accepts SILENCE_CLEANUP COMPRESS without reading targetLufs', () => {
    const step = confirmationStep('SILENCE_CLEANUP', {
      mode: 'COMPRESS',
      minSilenceMs: 3000,
      keepSilenceMs: 800,
    })

    const draft = createStepEditorDraft(step)
    expect(draft.parameterValues).not.toHaveProperty('targetLufs')
    expect(validate(step)).toMatchObject({ fieldErrors: {}, payload: { parameterOverrides: {} } })
  })

  it('accepts SILENCE_CLEANUP REMOVE without targetLufs or keepSilenceMs', () => {
    const step = confirmationStep('SILENCE_CLEANUP', {
      mode: 'REMOVE',
      minSilenceMs: 3000,
    })

    const draft = createStepEditorDraft(step)
    expect(draft.parameterValues).toEqual({ mode: 'REMOVE', minSilenceMs: '3000' })
    expect(validate(step)).toMatchObject({ fieldErrors: {}, payload: { parameterOverrides: {} } })
  })

  it('accepts DENOISE without requiring targetLufs', () => {
    const step = confirmationStep('DENOISE', { strength: 'MEDIUM' })

    expect(createStepEditorDraft(step).parameterValues).toEqual({ strength: 'MEDIUM' })
    expect(validate(step).payload).not.toBeNull()
  })

  it('accepts NORMALIZE_VOLUME with a legal targetLufs', () => {
    const step = confirmationStep('NORMALIZE_VOLUME', { targetLufs: -16 })

    expect(createStepEditorDraft(step).parameterValues).toEqual({ targetLufs: '-16' })
    expect(validate(step).payload).not.toBeNull()
  })

  it.each(['', 'NaN', 'Infinity'])('rejects NORMALIZE_VOLUME targetLufs %j', (targetLufs) => {
    const step = confirmationStep('NORMALIZE_VOLUME', {})
    const draft = createStepEditorDraft(step)
    draft.parameterValues.targetLufs = targetLufs

    const result = buildStepUpdatePayload(step, draft)

    expect(result.payload).toBeNull()
    expect(result.fieldErrors.targetLufs).toBeTruthy()
  })

  it('validates only the TRIM_SEGMENT time range', () => {
    expect(validate(confirmationStep('TRIM_SEGMENT', {}, 1000, 2000)).payload).not.toBeNull()

    const invalid = validate(confirmationStep('TRIM_SEGMENT', {}, Number.NaN, 2000))
    expect(invalid.payload).toBeNull()
    expect(invalid.fieldErrors.startMs).toBeTruthy()
  })
})
