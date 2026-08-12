export interface AudioPlaybackUrlResponse {
  fileId: string
  fileName: string
  mimeType?: string | null
  playbackUrl: string
  expiresAt: string
  expiresInSeconds: number
}
