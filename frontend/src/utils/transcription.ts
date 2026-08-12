import type { TranscriptionTaskStatus } from '../types/transcription'

export function clampTranscriptionProgress(value: number | null | undefined) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, Math.round(value)))
}

export function getTranscriptionProgressText(
  value: number | null | undefined,
  status?: TranscriptionTaskStatus,
) {
  if (status === 'SUCCESS') return '转写完成'

  const progress = clampTranscriptionProgress(value)
  if (progress >= 100) return '转写完成'
  if (progress >= 85) return '正在保存文字稿'
  if (progress >= 50) return '正在识别语音'
  if (progress >= 30) return '正在转换音频格式'
  if (progress >= 10) return '正在准备音频'
  return '等待处理'
}
