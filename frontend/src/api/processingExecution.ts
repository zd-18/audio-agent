import type {
  CreateProcessingExecutionPayload,
  ProcessingExecution,
  ProcessingExecutionListItem,
  ProcessingExecutionStep,
} from '../types/processingExecution'
import type { PageResult } from '../types/api'
import { apiRequest, isValidResourceId } from './http'

interface ProcessingExecutionStepPayload extends Omit<
  ProcessingExecutionStep,
  'startMs' | 'endMs' | 'skipReason' | 'failureMessage'
> {
  startMs?: number | null
  endMs?: number | null
  skipReason?: string | null
  failureMessage?: string | null
}

interface ProcessingExecutionPayload extends Omit<
  ProcessingExecution,
  | 'currentStage'
  | 'progressPercent'
  | 'acceptedStepCount'
  | 'executableStepCount'
  | 'skippedStepCount'
  | 'retryCount'
  | 'resultFileId'
  | 'failureCode'
  | 'failureMessage'
  | 'steps'
  | 'startedAt'
  | 'finishedAt'
  | 'createdAt'
  | 'updatedAt'
> {
  currentStage?: ProcessingExecution['currentStage']
  progressPercent?: number | null
  acceptedStepCount?: number | null
  executableStepCount?: number | null
  skippedStepCount?: number | null
  retryCount?: number | null
  resultFileId?: string | null
  failureCode?: string | null
  failureMessage?: string | null
  steps?: ProcessingExecutionStepPayload[] | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

function requireResourceId(value: string, label: string) {
  if (!isValidResourceId(value)) throw new Error(`${label} 无效`)
}

function headers(json = false): HeadersInit {
  return json ? { 'Content-Type': 'application/json' } : {}
}

function normalizeStep(payload: ProcessingExecutionStepPayload): ProcessingExecutionStep {
  return {
    ...payload,
    startMs: payload.startMs ?? null,
    endMs: payload.endMs ?? null,
    skipReason: payload.skipReason ?? null,
    failureMessage: payload.failureMessage ?? null,
  }
}

function normalizeExecution(payload: ProcessingExecutionPayload): ProcessingExecution {
  return {
    ...payload,
    currentStage: payload.currentStage ?? null,
    progressPercent: payload.progressPercent ?? 0,
    acceptedStepCount: payload.acceptedStepCount ?? 0,
    executableStepCount: payload.executableStepCount ?? 0,
    skippedStepCount: payload.skippedStepCount ?? 0,
    retryCount: payload.retryCount ?? 0,
    resultFileId: payload.resultFileId ?? null,
    failureCode: payload.failureCode ?? null,
    failureMessage: payload.failureMessage ?? null,
    steps: (payload.steps ?? []).map(normalizeStep),
    startedAt: payload.startedAt ?? null,
    finishedAt: payload.finishedAt ?? null,
    createdAt: payload.createdAt ?? null,
    updatedAt: payload.updatedAt ?? null,
  }
}

export interface ProcessingExecutionListParams {
  current: number
  size: number
  status?: string
}

export function getProcessingExecutionList(
  params: ProcessingExecutionListParams,
  signal?: AbortSignal,
) {
  const search = new URLSearchParams({
    current: String(params.current),
    size: String(params.size),
  })
  if (params.status) search.set('status', params.status)
  return apiRequest<PageResult<ProcessingExecutionListItem>>(
    `/api/audio-processing/executions?${search.toString()}`,
    { signal },
  )
}

export async function createProcessingExecution(
  confirmationId: string,
  signal?: AbortSignal,
) {
  requireResourceId(confirmationId, '确认单 ID')
  const body: CreateProcessingExecutionPayload = { confirmationId }
  const payload = await apiRequest<ProcessingExecutionPayload>('/api/audio-processing/executions', {
    method: 'POST',
    headers: headers(true),
    body: JSON.stringify(body),
    signal,
  })
  return normalizeExecution(payload)
}

export async function getProcessingExecution(executionId: string, signal?: AbortSignal) {
  requireResourceId(executionId, '执行任务 ID')
  const payload = await apiRequest<ProcessingExecutionPayload>(
    `/api/audio-processing/executions/${encodeURIComponent(executionId)}`,
    { headers: headers(), signal },
  )
  return normalizeExecution(payload)
}

export async function getProcessingExecutionByTaskId(taskId: string, signal?: AbortSignal) {
  requireResourceId(taskId, '分析任务 ID')
  const payload = await apiRequest<ProcessingExecutionPayload>(
    `/api/audio-analysis/tasks/${encodeURIComponent(taskId)}/processing-execution`,
    { headers: headers(), signal },
  )
  return normalizeExecution(payload)
}

export async function retryProcessingExecution(executionId: string, signal?: AbortSignal) {
  requireResourceId(executionId, '执行任务 ID')
  const payload = await apiRequest<ProcessingExecutionPayload>(
    `/api/audio-processing/executions/${encodeURIComponent(executionId)}/retry`,
    { method: 'POST', headers: headers(), signal },
  )
  return normalizeExecution(payload)
}

export async function cancelProcessingExecution(executionId: string, signal?: AbortSignal) {
  requireResourceId(executionId, '执行任务 ID')
  const payload = await apiRequest<ProcessingExecutionPayload>(
    `/api/audio-processing/executions/${encodeURIComponent(executionId)}/cancel`,
    { method: 'POST', headers: headers(), signal },
  )
  return normalizeExecution(payload)
}
