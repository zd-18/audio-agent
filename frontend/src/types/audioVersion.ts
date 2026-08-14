export interface AudioVersion {
  audioFileId: string
  parentAudioFileId: string | null
  versionNo: number
  versionSummary: string | null
  originalVersion: boolean
  fileName: string | null
  extension: string | null
  mimeType: string | null
  sizeBytes: number | null
  durationMs: number | null
  createdAt: string | null
}

export interface AudioVersionChain {
  versions: AudioVersion[]
}
