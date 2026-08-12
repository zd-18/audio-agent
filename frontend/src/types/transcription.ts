import type { ResourceId } from './api'

export type TranscriptionTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface TranscriptionTask {
  taskId: ResourceId
  audioFileId: ResourceId
  audioFileName: string
  status: TranscriptionTaskStatus
  language: string
  enableSpeakerDiarization: boolean
  progressPercent: number
  retryCount: number
  failureCode?: string | null
  failureMessage?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface TranscriptSegment {
  segmentId: ResourceId
  order: number
  segmentOrder: number
  startMs: number
  endMs: number
  speaker?: string | null
  text: string
  confidence?: number | null
}

export interface Transcript {
  transcriptId: ResourceId
  audioFileId: ResourceId
  audioFileName: string
  language: string
  fullText: string
  durationMs: number
  speakerCount?: number | null
  segmentCount: number
  segments: TranscriptSegment[]
}

export interface TranscriptionTaskListParams {
  current: number
  size: number
  status?: TranscriptionTaskStatus
  audioFileId?: string
}
