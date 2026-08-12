import { apiRequest } from './http'
import type {
  ProcessingConfirmation,
  ProcessingStepConfirmation,
  UpdateProcessingStepConfirmationPayload,
} from '../types/processingConfirmation'

const POSITIVE_ID_PATTERN = /^[1-9]\d*$/

interface ProcessingStepConfirmationPayload extends Omit<
  ProcessingStepConfirmation,
  'startMs' | 'endMs' | 'originalParameters' | 'parameterOverrides' | 'effectiveParameters' | 'userNote'
> {
  startMs?: number | null
  endMs?: number | null
  originalParameters?: ProcessingStepConfirmation['originalParameters'] | null
  parameterOverrides?: ProcessingStepConfirmation['parameterOverrides'] | null
  effectiveParameters?: ProcessingStepConfirmation['effectiveParameters'] | null
  userNote?: string | null
}

interface ProcessingConfirmationPayload extends Omit<
  ProcessingConfirmation,
  'resultMessage' | 'steps' | 'confirmedAt' | 'createdAt' | 'updatedAt'
> {
  resultMessage?: string | null
  steps?: ProcessingStepConfirmationPayload[] | null
  confirmedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

function normalizeStep(payload: ProcessingStepConfirmationPayload): ProcessingStepConfirmation {
  return {
    ...payload,
    startMs: payload.startMs ?? null,
    endMs: payload.endMs ?? null,
    originalParameters: payload.originalParameters ?? {},
    parameterOverrides: payload.parameterOverrides ?? {},
    effectiveParameters: payload.effectiveParameters ?? {},
    userNote: payload.userNote ?? null,
  }
}

function normalizeConfirmation(payload: ProcessingConfirmationPayload): ProcessingConfirmation {
  return {
    ...payload,
    resultMessage: payload.resultMessage ?? null,
    steps: (payload.steps ?? []).map(normalizeStep),
    confirmedAt: payload.confirmedAt ?? null,
    createdAt: payload.createdAt ?? null,
    updatedAt: payload.updatedAt ?? null,
  }
}

function requireResourceId(value: string, label: string) {
  if (!POSITIVE_ID_PATTERN.test(value)) {
    throw new Error(`${label} 无效`)
  }
}

function confirmationHeaders(json = false): HeadersInit {
  return json ? { 'Content-Type': 'application/json' } : {}
}

function taskConfirmationUrl(taskId: string) {
  requireResourceId(taskId, '分析任务 ID')
  return `/api/audio-analysis/tasks/${encodeURIComponent(taskId)}/processing-confirmation`
}

function confirmationUrl(confirmationId: string, suffix: string) {
  requireResourceId(confirmationId, '确认单 ID')
  return `/api/audio-analysis/processing-confirmations/${encodeURIComponent(confirmationId)}${suffix}`
}

export async function createProcessingConfirmation(taskId: string, signal?: AbortSignal) {
  const payload = await apiRequest<ProcessingConfirmationPayload>(taskConfirmationUrl(taskId), {
    method: 'POST',
    headers: confirmationHeaders(),
    signal,
  })
  return normalizeConfirmation(payload)
}

export async function getProcessingConfirmation(taskId: string, signal?: AbortSignal) {
  const payload = await apiRequest<ProcessingConfirmationPayload>(taskConfirmationUrl(taskId), {
    headers: confirmationHeaders(),
    signal,
  })
  return normalizeConfirmation(payload)
}

export async function updateProcessingStepConfirmation(
  confirmationId: string,
  stepConfirmationId: string,
  payload: UpdateProcessingStepConfirmationPayload,
  signal?: AbortSignal,
) {
  requireResourceId(stepConfirmationId, '步骤确认 ID')
  const response = await apiRequest<ProcessingStepConfirmationPayload>(
    confirmationUrl(
      confirmationId,
      `/steps/${encodeURIComponent(stepConfirmationId)}`,
    ),
    {
      method: 'PUT',
      headers: confirmationHeaders(true),
      body: JSON.stringify(payload),
      signal,
    },
  )
  return normalizeStep(response)
}

export async function confirmProcessingConfirmation(confirmationId: string, signal?: AbortSignal) {
  const payload = await apiRequest<ProcessingConfirmationPayload>(confirmationUrl(confirmationId, '/confirm'), {
    method: 'POST',
    headers: confirmationHeaders(),
    signal,
  })
  return normalizeConfirmation(payload)
}

export async function cancelProcessingConfirmation(confirmationId: string, signal?: AbortSignal) {
  const payload = await apiRequest<ProcessingConfirmationPayload>(confirmationUrl(confirmationId, '/cancel'), {
    method: 'POST',
    headers: confirmationHeaders(),
    signal,
  })
  return normalizeConfirmation(payload)
}
