export interface ApiResponse<T> {
  code: number
  message: string
  data?: T
  requestId?: string
}

export type ResourceId = string

export interface AudioFileRecord {
  fileId: ResourceId
  originalName?: string
  extension?: string
  mimeType?: string
  sizeBytes?: number
  sha256?: string
  fileRole?: string
  fileStatus?: string
  versionNo?: number
  versionSummary?: string
  durationMs?: number
  createdAt?: string
}

export type MultipartUploadStatus = 'INIT' | 'UPLOADING' | 'MERGING' | 'COMPLETED' | 'FAILED'

export interface MultipartUploadInitResult {
  uploadId?: string
  status: MultipartUploadStatus
  instantUpload: boolean
  chunkSize: number
  totalChunks: number
  uploadedChunks: number[]
  audioFile?: AudioFileRecord
}

export interface MultipartUploadProgress {
  uploadId: string
  status: MultipartUploadStatus
  sizeBytes: number
  chunkSize: number
  totalChunks: number
  uploadedCount: number
  uploadedChunks: number[]
}

export interface MultipartChunkResult {
  uploadId: string
  status: MultipartUploadStatus
  chunkIndex: number
  uploadedCount: number
  totalChunks: number
}

export interface MultipartUploadCompleteResult {
  uploadId: string
  status: 'COMPLETED'
  instantUpload: boolean
  audioFile: AudioFileRecord
}

export interface AudioFileListItem {
  audioFileId: ResourceId
  originalFileName?: string
  fileSize?: number
  contentType?: string
  duration?: number
  status?: string
  sha256?: string
  createdAt?: string
  transcriptionTaskId?: ResourceId | null
  transcriptionStatus?: string | null
}

export interface PageResult<T> {
  records: T[]
  current: number
  size: number
  total: number
  pages: number
}

export type AnalysisTaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export interface AudioAnalysisResult {
  formatName?: string
  codecName?: string
  durationMs?: number
  sampleRate?: number
  channels?: number
  bitRate?: number
  fileSize?: number
}

export interface AnalysisTaskRecord {
  taskId: ResourceId
  audioFileId: ResourceId
  analysisType?: string
  status: AnalysisTaskStatus
  progress?: number
  errorMessage?: string
  retryCount?: number
  maxRetryCount?: number
  nextRetryAt?: string
  lastErrorCode?: string
  lastMessageId?: string
  createdAt?: string
  startedAt?: string
  finishedAt?: string
  result?: AudioAnalysisResult
}

export interface AnalysisTaskListItem {
  taskId: ResourceId
  audioFileId: ResourceId
  fileName?: string
  analysisType?: string
  status: AnalysisTaskStatus
  progress?: number
  retryCount?: number
  maxRetryCount?: number
  lastErrorCode?: string
  errorMessage?: string
  createdAt?: string
  startedAt?: string
  finishedAt?: string
}
