import { apiRequest, isValidResourceId } from './http'
import type { AnalysisTaskListItem, AnalysisTaskRecord, PageResult } from '../types/api'

export interface AnalysisTaskListParams {
  current: number
  size: number
  status?: string
  keyword?: string
  audioFileId?: string
  analysisType?: string
}

export function getAnalysisTaskList(params: AnalysisTaskListParams, signal?: AbortSignal) {
  const search = new URLSearchParams({
    current: String(params.current),
    size: String(params.size),
  })

  if (params.status) search.set('status', params.status)
  if (params.keyword) search.set('keyword', params.keyword)
  if (params.audioFileId) search.set('audioFileId', params.audioFileId)
  if (params.analysisType) search.set('analysisType', params.analysisType)

  return apiRequest<PageResult<AnalysisTaskListItem>>(`/api/audio-analysis/tasks?${search.toString()}`, { signal })
}

export function createAnalysisTask(audioFileId: string, signal?: AbortSignal) {
  if (!isValidResourceId(audioFileId)) {
    throw new Error('音频文件 ID 无效')
  }

  return apiRequest<AnalysisTaskRecord>('/api/audio-analysis/tasks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ audioFileId, analysisType: 'FULL' }),
    signal,
  })
}

export function getAnalysisTaskDetail(taskId: string, signal?: AbortSignal) {
  return apiRequest<AnalysisTaskRecord>(`/api/audio-analysis/tasks/${encodeURIComponent(taskId)}`, { signal })
}

export function retryAnalysisTask(taskId: string, signal?: AbortSignal) {
  return apiRequest<AnalysisTaskRecord>(`/api/audio-analysis/tasks/${encodeURIComponent(taskId)}/retry`, {
    method: 'POST',
    signal,
  })
}
