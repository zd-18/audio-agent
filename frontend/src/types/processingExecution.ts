import type { ProcessingOperationType } from './processingPlan'

export type ProcessingExecutionStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'DEAD_LETTER'

export type ProcessingExecutionStage =
  | 'PREPARING'
  | 'LOCAL_PROCESSING'
  | 'TRIMMING'
  | 'SILENCE_CLEANING'
  | 'DENOISING'
  | 'LOUDNESS_NORMALIZING'
  | 'PEAK_LIMITING'
  | 'UPLOADING'
  | 'METADATA_EXTRACTING'
  | 'COMPLETED'

export type ProcessingExecutionStepStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'FAILED'
  | 'SKIPPED'

export interface ProcessingExecutionStep {
  executionStepId: string
  stepOrder: number
  operationType: ProcessingOperationType
  executionStatus: ProcessingExecutionStepStatus
  startMs: number | null
  endMs: number | null
  skipReason: string | null
  failureMessage: string | null
}

export interface ProcessingExecution {
  executionId: string
  taskId: string
  audioFileId: string
  confirmationId: string
  executionStatus: ProcessingExecutionStatus
  currentStage: ProcessingExecutionStage | null
  progressPercent: number
  acceptedStepCount: number
  executableStepCount: number
  skippedStepCount: number
  retryCount: number
  resultFileId: string | null
  failureCode: string | null
  failureMessage: string | null
  steps: ProcessingExecutionStep[]
  startedAt: string | null
  finishedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface CreateProcessingExecutionPayload {
  confirmationId: string
}

export interface ProcessingExecutionListItem {
  executionId: string
  taskId: string
  audioFileId: string
  fileName: string | null
  executionStatus: ProcessingExecutionStatus
  currentStage: ProcessingExecutionStage | null
  progressPercent: number
  resultFileId: string | null
  failureCode: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string | null
}
