import type { PageResult } from '../types/api'
import type {
  Transcript,
  TranscriptSegment,
  TranscriptionTask,
  TranscriptionTaskListParams,
} from '../types/transcription'
import { ApiError, apiRequest, authorizedFetch, isValidResourceId, parseApiResponse } from './http'

export type TranscriptExportFormat = 'txt' | 'srt' | 'vtt'

export function createTranscriptionTask(
  audioFileId: string,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(audioFileId)) throw new Error('音频文件 ID 无效')
  return apiRequest<TranscriptionTask>('/api/audio-transcriptions/tasks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      audioFileId,
      language: 'zh',
      enableSpeakerDiarization: false,
    }),
    signal,
  })
}

export function getTranscriptionTasks(
  params: TranscriptionTaskListParams,
  signal?: AbortSignal,
) {
  const search = new URLSearchParams({
    current: String(params.current),
    size: String(params.size),
  })
  if (params.status) search.set('status', params.status)
  if (params.audioFileId) search.set('audioFileId', params.audioFileId)
  return apiRequest<PageResult<TranscriptionTask>>(
    `/api/audio-transcriptions/tasks?${search.toString()}`,
    { signal },
  )
}

export function getTranscriptionTask(taskId: string, signal?: AbortSignal) {
  if (!isValidResourceId(taskId)) throw new Error('转写任务 ID 无效')
  return apiRequest<TranscriptionTask>(
    `/api/audio-transcriptions/tasks/${encodeURIComponent(taskId)}`,
    { signal },
  )
}

export function getTranscript(taskId: string, signal?: AbortSignal) {
  if (!isValidResourceId(taskId)) throw new Error('转写任务 ID 无效')
  return apiRequest<Transcript>(
    `/api/audio-transcriptions/tasks/${encodeURIComponent(taskId)}/transcript`,
    { signal },
  )
}

export function updateTranscriptSegment(
  transcriptId: string,
  segmentId: string,
  payload: { text: string; speaker?: string | null },
  signal?: AbortSignal,
) {
  if (!isValidResourceId(transcriptId) || !isValidResourceId(segmentId)) {
    throw new Error('文字稿片段 ID 无效')
  }
  return apiRequest<TranscriptSegment>(
    `/api/audio-transcriptions/transcripts/${encodeURIComponent(transcriptId)}/segments/${encodeURIComponent(segmentId)}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal,
    },
  )
}

export async function downloadTranscript(
  transcriptId: string,
  format: TranscriptExportFormat,
  fileName: string,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(transcriptId)) throw new Error('文字稿 ID 无效')
  const response = await authorizedFetch(
    `/api/audio-transcriptions/transcripts/${encodeURIComponent(transcriptId)}/export?format=${format}`,
    { signal },
  )
  if (!response.ok) {
    const body = await response.text()
    try {
      const parsed = parseApiResponse<never>(body)
      throw new ApiError(parsed.message || `导出失败（${response.status}）`, parsed.code)
    } catch (error) {
      if (error instanceof ApiError) throw error
      throw new ApiError(`导出失败（${response.status}）`)
    }
  }
  const blobUrl = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  try {
    link.href = blobUrl
    const baseName = fileName.replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|\r\n]/g, '_') || 'transcript'
    link.download = `${baseName}.${format}`
    document.body.appendChild(link)
    link.click()
  } finally {
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  }
}
