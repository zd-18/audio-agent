export function formatBytes(bytes?: number) {
  if (bytes === undefined || bytes === null) return '—'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  return `${value.toFixed(value >= 100 ? 0 : value >= 10 ? 1 : 2)} ${units[index]}`
}

export function formatDuration(durationMs?: number | null) {
  if (
    durationMs === undefined
    || durationMs === null
    || !Number.isFinite(durationMs)
  ) return '—'
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return hours > 0
    ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function formatDateTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

export function formatBitRate(bitRate?: number) {
  if (bitRate === undefined || bitRate === null) return '—'
  return `${Math.round(bitRate / 1000).toLocaleString('zh-CN')} kbps`
}

export function formatChannels(channels?: number) {
  if (channels === undefined || channels === null) return '—'
  if (channels === 1) return '单声道（1）'
  if (channels === 2) return '双声道（2）'
  return `${channels} 声道`
}
