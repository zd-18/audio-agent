import type { PageResult } from '../types/api'
import type {
  Transcript,
  TranscriptionTask,
  TranscriptionTaskListParams,
} from '../types/transcription'
import { apiRequest, isValidResourceId } from './http'

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
