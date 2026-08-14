import type {
  ProcessingOperationType,
  ProcessingPlanStatus,
  ProcessingPriority,
  ProcessingRiskLevel,
  ProcessingStep,
  ProcessingStepParameters,
} from '../types/processingPlan'

const OPERATION_LABELS: Record<ProcessingOperationType, string> = {
  NORMALIZE_VOLUME: '整段音量标准化',
  TRIM_SEGMENT: '裁剪指定片段',
  REVIEW_SILENCE: '检查静音片段',
  TRIM_SILENCE: '缩短较长静音',
  INCREASE_GAIN: '提升局部音量',
  DECREASE_GAIN: '降低突发音量',
  DENOISE_REVIEW: '检查疑似背景噪声',
  NORMALIZE_LOUDNESS: '统一整体响度',
  LIMIT_PEAK: '控制过高峰值',
}

const PLAN_STATUS_LABELS: Record<ProcessingPlanStatus, string> = {
  READY: '处理方案已生成',
  DRAFT: '处理方案待确认',
  INVALID: '当前方案不可用',
}

const PRIORITY_LABELS: Record<ProcessingPriority, string> = {
  HIGH: '优先处理',
  MEDIUM: '建议处理',
  LOW: '可优化',
}

const RISK_LABELS: Record<ProcessingRiskLevel, string> = {
  HIGH: '操作前请重点确认',
  MEDIUM: '建议试听确认',
  LOW: '处理风险较低',
}

const WHOLE_AUDIO_OPERATIONS = new Set<ProcessingOperationType>([
  'NORMALIZE_VOLUME',
  'NORMALIZE_LOUDNESS',
  'LIMIT_PEAK',
])

const EXECUTABLE_OPERATIONS = new Set<ProcessingOperationType>([
  'NORMALIZE_VOLUME',
  'TRIM_SEGMENT',
])

export function isExecutableProcessingOperation(operation: ProcessingOperationType) {
  return EXECUTABLE_OPERATIONS.has(operation)
}

export interface ParameterDisplayItem {
  label: string
  value: string
}

export function getOperationLabel(operationType: ProcessingOperationType, fallback?: string) {
  return OPERATION_LABELS[operationType] || fallback || '音频处理建议'
}

export function getPlanStatusLabel(status: ProcessingPlanStatus) {
  return PLAN_STATUS_LABELS[status] || '处理方案状态未知'
}

export function getPriorityLabel(priority: ProcessingPriority) {
  return PRIORITY_LABELS[priority] || '处理优先级待确认'
}

export function getRiskLabel(riskLevel: ProcessingRiskLevel) {
  return RISK_LABELS[riskLevel] || '建议处理前确认'
}

export function isWholeAudioOperation(operationType: ProcessingOperationType) {
  return WHOLE_AUDIO_OPERATIONS.has(operationType)
}

export function hasSegmentRange(
  step: ProcessingStep,
): step is ProcessingStep & { startMs: number; endMs: number } {
  return !isWholeAudioOperation(step.operationType)
    && typeof step.startMs === 'number'
    && Number.isFinite(step.startMs)
    && step.startMs >= 0
    && typeof step.endMs === 'number'
    && Number.isFinite(step.endMs)
    && step.endMs > step.startMs
}

export function getStepDurationMs(step: ProcessingStep) {
  return hasSegmentRange(step) ? step.endMs - step.startMs : null
}

function finiteNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function textValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function formatNumber(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1).replace(/\.0$/, '')
}

function formatSignedDb(value: number) {
  return `${value > 0 ? '+' : ''}${formatNumber(value)} dB`
}

function modeLabel(value: unknown) {
  if (value === 'REVIEW_BEFORE_APPLY') return '确认后处理'
  if (value === 'SUGGESTION_ONLY') return '仅提供建议参数'
  return '处理前确认'
}

function strengthLabel(value: unknown) {
  const normalized = textValue(value)?.toUpperCase()
  if (normalized === 'LIGHT' || normalized === 'MILD') return '轻度'
  if (normalized === 'MEDIUM' || normalized === 'MODERATE') return '中等'
  if (normalized === 'HIGH' || normalized === 'STRONG') return '较强'
  return normalized ? '建议试听后确认' : null
}

function addNumber(
  items: ParameterDisplayItem[],
  parameters: ProcessingStepParameters,
  key: string,
  label: string,
  format: (value: number) => string,
) {
  const value = finiteNumber(parameters[key])
  if (value !== null) items.push({ label, value: format(value) })
}

export function getParameterDisplayItems(step: ProcessingStep): ParameterDisplayItem[] {
  const parameters = step.parameters || {}
  const items: ParameterDisplayItem[] = []

  switch (step.operationType) {
    case 'NORMALIZE_VOLUME':
      addNumber(items, parameters, 'targetLufs', '目标响度', (value) => `${formatNumber(value)} LUFS`)
      addNumber(items, parameters, 'truePeakLimitDbfs', '真峰值上限', (value) => `${formatNumber(value)} dBFS`)
      break
    case 'TRIM_SEGMENT':
      items.push({ label: '处理方式', value: '移除所选 startMs / endMs 时间范围' })
      break
    case 'REVIEW_SILENCE':
      items.push({ label: '处理方式', value: modeLabel(parameters.mode) })
      break
    case 'TRIM_SILENCE':
      addNumber(items, parameters, 'suggestedKeepHeadMs', '建议保留开头', (value) => `${formatNumber(value)} ms`)
      addNumber(items, parameters, 'suggestedKeepTailMs', '建议保留结尾', (value) => `${formatNumber(value)} ms`)
      items.push({ label: '处理方式', value: modeLabel(parameters.mode) })
      break
    case 'INCREASE_GAIN':
      addNumber(items, parameters, 'suggestedGainDb', '建议增益', formatSignedDb)
      addNumber(items, parameters, 'maxGainDb', '最大建议增益', (value) => `${formatNumber(Math.abs(value))} dB`)
      break
    case 'DECREASE_GAIN':
      addNumber(items, parameters, 'suggestedGainDb', '建议增益', formatSignedDb)
      addNumber(items, parameters, 'maxAbsoluteGainDb', '最大调整幅度', (value) => `${formatNumber(Math.abs(value))} dB`)
      break
    case 'DENOISE_REVIEW': {
      const strength = strengthLabel(parameters.suggestedStrength)
      if (strength) items.push({ label: '建议强度', value: strength })
      const confidence = finiteNumber(parameters.confidence)
      if (confidence !== null) {
        const percentage = confidence <= 1 ? confidence * 100 : confidence
        items.push({ label: '参考置信度', value: `${formatNumber(percentage)}%` })
      }
      items.push({ label: '确认方式', value: '需要试听确认' })
      break
    }
    case 'NORMALIZE_LOUDNESS':
      addNumber(items, parameters, 'targetLufs', '目标响度', (value) => `${formatNumber(value)} LUFS`)
      addNumber(items, parameters, 'truePeakLimitDbfs', '峰值限制', (value) => `${formatNumber(value)} dBFS`)
      break
    case 'LIMIT_PEAK':
      addNumber(items, parameters, 'truePeakLimitDbfs', '建议峰值上限', (value) => `${formatNumber(value)} dBFS`)
      break
    default:
      break
  }

  return items.length > 0
    ? items
    : [{ label: '参数说明', value: '当前步骤使用系统建议参数，处理前请确认。' }]
}
