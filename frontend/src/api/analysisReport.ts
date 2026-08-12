import type { AudioAnalysisReport } from '../types/analysisReport'
import { apiRequest, isValidResourceId } from './http'

export function getAnalysisReport(taskId: string, signal?: AbortSignal) {
  if (!isValidResourceId(taskId)) {
    throw new Error('分析任务 ID 无效')
  }

  return apiRequest<AudioAnalysisReport>(
    `/api/audio-analysis/tasks/${encodeURIComponent(taskId)}/report`,
    { signal },
  )
}
