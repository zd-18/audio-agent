import type { ProcessingOperationType } from './processingPlan'

export type ProcessingConfirmationStatus = 'DRAFT' | 'CONFIRMED' | 'STALE' | 'CANCELLED'

export type ProcessingStepDecision = 'PENDING' | 'ACCEPTED' | 'REJECTED'

export type ProcessingParameterMap = Record<string, unknown>

export interface ProcessingStepConfirmation {
  stepConfirmationId: string
  sourceStepId: string
  stepOrder: number
  operationType: ProcessingOperationType
  title?: string
  decision: ProcessingStepDecision
  userConfirmed: boolean
  requiresConfirmation: boolean
  startMs: number | null
  endMs: number | null
  originalParameters: ProcessingParameterMap
  parameterOverrides: ProcessingParameterMap
  effectiveParameters: ProcessingParameterMap
  userNote: string | null
}

export interface ProcessingConfirmation {
  confirmationId: string
  taskId: string
  audioFileId: string
  planId: string
  sourcePlanRevision: number
  confirmationStatus: ProcessingConfirmationStatus
  acceptedStepCount: number
  rejectedStepCount: number
  pendingStepCount: number
  resultMessage: string | null
  steps: ProcessingStepConfirmation[]
  confirmedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface UpdateProcessingStepConfirmationPayload {
  decision: ProcessingStepDecision
  userConfirmed: boolean
  parameterOverrides: ProcessingParameterMap
  userNote: string | null
}
