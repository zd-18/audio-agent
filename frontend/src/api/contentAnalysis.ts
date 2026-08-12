import type {
  ContentAnalysisResult,
  ContentAnalysisTask,
  CreateContentAnalysisTaskInput,
} from '../types/contentAnalysis'
import { apiRequest, isValidResourceId } from './http'

export function createContentAnalysisTask(
  input: CreateContentAnalysisTaskInput,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(input.transcriptId)) {
    throw new Error('文字稿 ID 无效')
  }
  return apiRequest<ContentAnalysisTask>('/api/content-analysis/tasks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
    signal,
  })
}

export function getContentAnalysisTask(
  taskId: string,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(taskId)) {
    throw new Error('智能分析任务 ID 无效')
  }
  return apiRequest<ContentAnalysisTask>(
    `/api/content-analysis/tasks/${encodeURIComponent(taskId)}`,
    { signal },
  )
}

export function getContentAnalysisResult(
  taskId: string,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(taskId)) {
    throw new Error('智能分析任务 ID 无效')
  }
  return apiRequest<ContentAnalysisResult>(
    `/api/content-analysis/tasks/${encodeURIComponent(taskId)}/result`,
    { signal },
  )
}

export function retryContentAnalysisTask(
  taskId: string,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(taskId)) {
    throw new Error('智能分析任务 ID 无效')
  }
  return apiRequest<ContentAnalysisTask>(
    `/api/content-analysis/tasks/${encodeURIComponent(taskId)}/retry`,
    { method: 'POST', signal },
  )
}
