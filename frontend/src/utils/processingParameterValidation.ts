import type {
  ProcessingParameterMap,
  ProcessingStepConfirmation,
  ProcessingStepDecision,
  UpdateProcessingStepConfirmationPayload,
} from '../types/processingConfirmation'
import type { ProcessingOperationType } from '../types/processingPlan'

export interface EditableParameterDefinition {
  key: string
  label: string
  unit?: string
  kind: 'number' | 'select'
  min?: number
  max?: number
  exclusiveMin?: boolean
  exclusiveMax?: boolean
  step?: number
  options?: Array<{ value: string; label: string }>
  helper: string
}

export interface StepEditorDraft {
  decision: ProcessingStepDecision
  userConfirmed: boolean
  parameterValues: Record<string, string>
  userNote: string
}

export interface StepPayloadResult {
  payload: UpdateProcessingStepConfirmationPayload | null
  fieldErrors: Record<string, string>
}

const EDITABLE_PARAMETERS: Record<ProcessingOperationType, EditableParameterDefinition[]> = {
  NORMALIZE_VOLUME: [
    {
      key: 'targetLufs',
      label: '目标响度',
      unit: 'LUFS',
      kind: 'number',
      min: -24,
      max: -8,
      step: 0.1,
      helper: '允许范围 -24 至 -8 LUFS。',
    },
    {
      key: 'truePeakLimitDbfs',
      label: '真峰值上限',
      unit: 'dBFS',
      kind: 'number',
      min: -6,
      max: 0,
      step: 0.1,
      helper: '允许范围 -6 至 0 dBFS。',
    },
  ],
  TRIM_SEGMENT: [],
  DENOISE: [
    {
      key: 'strength',
      label: '降噪强度',
      kind: 'select',
      options: [
        { value: 'LIGHT', label: '轻度' },
        { value: 'MEDIUM', label: '中度' },
        { value: 'STRONG', label: '强力' },
      ],
      helper: '轻度适合轻微底噪，强力适合明显持续噪声。',
    },
  ],
  REVIEW_SILENCE: [],
  TRIM_SILENCE: [
    {
      key: 'suggestedKeepHeadMs',
      label: '保留开头静音',
      unit: 'ms',
      kind: 'number',
      min: 0,
      step: 10,
      helper: '不得小于 0；开头与结尾保留之和必须短于当前片段。',
    },
    {
      key: 'suggestedKeepTailMs',
      label: '保留结尾静音',
      unit: 'ms',
      kind: 'number',
      min: 0,
      step: 10,
      helper: '不得小于 0；开头与结尾保留之和必须短于当前片段。',
    },
  ],
  INCREASE_GAIN: [
    {
      key: 'suggestedGainDb',
      label: '建议增益',
      unit: 'dB',
      kind: 'number',
      min: 0,
      max: 6,
      exclusiveMin: true,
      step: 0.1,
      helper: '必须大于 0，且不超过后端默认上限 6 dB。',
    },
  ],
  DECREASE_GAIN: [
    {
      key: 'suggestedGainDb',
      label: '建议增益',
      unit: 'dB',
      kind: 'number',
      min: -6,
      max: 0,
      exclusiveMax: true,
      step: 0.1,
      helper: '必须小于 0，且绝对值不超过后端默认上限 6 dB。',
    },
  ],
  DENOISE_REVIEW: [
    {
      key: 'suggestedStrength',
      label: '降噪强度',
      kind: 'select',
      options: [
        { value: 'LIGHT', label: '轻度' },
        { value: 'MEDIUM', label: '中度' },
      ],
      helper: '后端当前仅允许轻度或中度。',
    },
  ],
  NORMALIZE_LOUDNESS: [
    {
      key: 'targetLufs',
      label: '目标响度',
      unit: 'LUFS',
      kind: 'number',
      min: -24,
      max: -8,
      step: 0.1,
      helper: '允许范围 -24 至 -8 LUFS。',
    },
    {
      key: 'truePeakLimitDbfs',
      label: '真峰值上限',
      unit: 'dBFS',
      kind: 'number',
      min: -6,
      max: 0,
      step: 0.1,
      helper: '允许范围 -6 至 0 dBFS。',
    },
  ],
  LIMIT_PEAK: [
    {
      key: 'truePeakLimitDbfs',
      label: '真峰值上限',
      unit: 'dBFS',
      kind: 'number',
      min: -6,
      max: 0,
      step: 0.1,
      helper: '允许范围 -6 至 0 dBFS。',
    },
  ],
}

export function getEditableParameterDefinitions(operationType: ProcessingOperationType) {
  return EDITABLE_PARAMETERS[operationType]
}

function parameterText(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  return typeof value === 'string' ? value : ''
}

export function createStepEditorDraft(step: ProcessingStepConfirmation): StepEditorDraft {
  const parameterValues: Record<string, string> = {}
  for (const definition of getEditableParameterDefinitions(step.operationType)) {
    parameterValues[definition.key] = parameterText(step.effectiveParameters[definition.key])
  }
  return {
    decision: step.decision,
    userConfirmed: step.userConfirmed,
    parameterValues,
    userNote: step.userNote || '',
  }
}

export function createOriginalParameterValues(step: ProcessingStepConfirmation) {
  const parameterValues: Record<string, string> = {}
  for (const definition of getEditableParameterDefinitions(step.operationType)) {
    parameterValues[definition.key] = parameterText(step.originalParameters[definition.key])
  }
  return parameterValues
}

function parseParameter(
  value: string,
  definition: EditableParameterDefinition,
  fieldErrors: Record<string, string>,
) {
  if (definition.kind === 'select') {
    if (!definition.options?.some((option) => option.value === value)) {
      fieldErrors[definition.key] = `请选择有效的${definition.label}`
      return null
    }
    return value
  }

  if (value.trim() === '') {
    fieldErrors[definition.key] = `${definition.label}不能为空`
    return null
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    fieldErrors[definition.key] = `${definition.label}必须是有效数字`
    return null
  }
  if (definition.min !== undefined) {
    const invalid = definition.exclusiveMin ? parsed <= definition.min : parsed < definition.min
    if (invalid) fieldErrors[definition.key] = `${definition.label}不能低于允许范围`
  }
  if (definition.max !== undefined) {
    const invalid = definition.exclusiveMax ? parsed >= definition.max : parsed > definition.max
    if (invalid) fieldErrors[definition.key] = `${definition.label}不能超过允许范围`
  }
  return parsed
}

function valuesEqual(first: unknown, second: unknown) {
  if (typeof first === 'number' && typeof second === 'number') return first === second
  return first === second
}

export function buildStepUpdatePayload(
  step: ProcessingStepConfirmation,
  draft: StepEditorDraft,
): StepPayloadResult {
  const fieldErrors: Record<string, string> = {}
  const parameterOverrides: ProcessingParameterMap = {}
  const effectiveValues: ProcessingParameterMap = {}

  for (const definition of getEditableParameterDefinitions(step.operationType)) {
    const parsed = parseParameter(draft.parameterValues[definition.key] ?? '', definition, fieldErrors)
    if (parsed !== null) {
      effectiveValues[definition.key] = parsed
      if (!valuesEqual(parsed, step.originalParameters[definition.key])) {
        parameterOverrides[definition.key] = parsed
      }
    }
  }

  if (step.operationType === 'TRIM_SILENCE'
    && typeof step.startMs === 'number'
    && typeof step.endMs === 'number') {
    const head = effectiveValues.suggestedKeepHeadMs
    const tail = effectiveValues.suggestedKeepTailMs
    if (typeof head === 'number' && typeof tail === 'number'
      && head + tail >= step.endMs - step.startMs) {
      const message = '开头与结尾保留之和必须短于当前静音片段'
      fieldErrors.suggestedKeepHeadMs = message
      fieldErrors.suggestedKeepTailMs = message
    }
  }

  if (draft.userNote.length > 500) {
    fieldErrors.userNote = '备注不能超过 500 个字符'
  }

  if (Object.keys(fieldErrors).length > 0) {
    return { payload: null, fieldErrors }
  }

  return {
    payload: {
      decision: draft.decision,
      userConfirmed: draft.decision === 'ACCEPTED' ? draft.userConfirmed : false,
      parameterOverrides,
      userNote: draft.userNote.trim() || null,
    },
    fieldErrors,
  }
}

export function stepEditorDraftsEqual(first: StepEditorDraft, second: StepEditorDraft) {
  return first.decision === second.decision
    && first.userConfirmed === second.userConfirmed
    && first.userNote === second.userNote
    && JSON.stringify(first.parameterValues) === JSON.stringify(second.parameterValues)
}
