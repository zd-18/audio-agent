import { apiRequest, isValidResourceId } from './http'
import type { ProcessingPlan, ProcessingStep } from '../types/processingPlan'

interface ProcessingStepPayload extends Omit<
  ProcessingStep,
  'sourceIssueId' | 'startMs' | 'endMs' | 'parameters'
> {
  sourceIssueId?: string | null
  startMs?: number | null
  endMs?: number | null
  parameters?: ProcessingStep['parameters'] | null
}

interface ProcessingPlanPayload extends Omit<
  ProcessingPlan,
  'estimatedOutputDurationMs' | 'steps' | 'generatedAt'
> {
  estimatedOutputDurationMs?: number | null
  steps?: ProcessingStepPayload[] | null
  generatedAt?: string | null
}

function normalizeProcessingPlan(payload: ProcessingPlanPayload): ProcessingPlan {
  return {
    ...payload,
    estimatedOutputDurationMs: payload.estimatedOutputDurationMs ?? null,
    generatedAt: payload.generatedAt ?? null,
    steps: (payload.steps ?? []).map((step) => ({
      ...step,
      sourceIssueId: step.sourceIssueId ?? null,
      startMs: step.startMs ?? null,
      endMs: step.endMs ?? null,
      parameters: step.parameters ?? {},
    })),
  }
}

function processingPlanUrl(taskId: string) {
  if (!isValidResourceId(taskId)) {
    throw new Error('分析任务 ID 无效')
  }
  return `/api/audio-analysis/tasks/${encodeURIComponent(taskId)}/processing-plan`
}

export async function generateProcessingPlan(taskId: string, signal?: AbortSignal) {
  const payload = await apiRequest<ProcessingPlanPayload>(processingPlanUrl(taskId), {
    method: 'POST',
    signal,
  })
  return normalizeProcessingPlan(payload)
}

export async function getProcessingPlan(taskId: string, signal?: AbortSignal) {
  const payload = await apiRequest<ProcessingPlanPayload>(processingPlanUrl(taskId), { signal })
  return normalizeProcessingPlan(payload)
}
