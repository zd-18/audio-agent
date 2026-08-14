import type { ResourceId } from './api'

export type ProcessingPlanStatus = 'DRAFT' | 'READY' | 'INVALID'

export type ProcessingOperationType =
  | 'NORMALIZE_VOLUME'
  | 'TRIM_SEGMENT'
  | 'DENOISE'
  | 'REVIEW_SILENCE'
  | 'TRIM_SILENCE'
  | 'INCREASE_GAIN'
  | 'DECREASE_GAIN'
  | 'DENOISE_REVIEW'
  | 'NORMALIZE_LOUDNESS'
  | 'LIMIT_PEAK'

export type ProcessingPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type ProcessingRiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface ProcessingStepParameters {
  suggestedKeepHeadMs?: number
  suggestedKeepTailMs?: number
  suggestedGainDb?: number
  maxGainDb?: number
  maxAbsoluteGainDb?: number
  suggestedStrength?: string
  confidence?: number
  targetLufs?: number
  truePeakLimitDbfs?: number
  mode?: string
  [key: string]: unknown
}

export interface ProcessingStep {
  stepId: ResourceId
  stepOrder: number
  operationType: ProcessingOperationType
  title?: string
  description?: string
  sourceIssueId: ResourceId | null
  startMs: number | null
  endMs: number | null
  priority: ProcessingPriority
  riskLevel: ProcessingRiskLevel
  requiresConfirmation: boolean
  parameters: ProcessingStepParameters
  reason?: string
}

export interface ProcessingPlan {
  planId: ResourceId
  taskId: ResourceId
  audioFileId: ResourceId
  planVersion: number
  planRevision: number
  planStatus: ProcessingPlanStatus
  summary?: string
  stepCount: number
  estimatedOutputDurationMs: number | null
  steps: ProcessingStep[]
  generatedAt: string | null
}
