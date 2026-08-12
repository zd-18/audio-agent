export { formatDuration } from './formatters'

function normalizeMilliseconds(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? Math.floor(value)
    : null
}

function pad(value: number, length = 2) {
  return String(value).padStart(length, '0')
}

export function formatTimestamp(value: number | null | undefined) {
  const milliseconds = normalizeMilliseconds(value)
  if (milliseconds === null) return '—'

  const totalSeconds = Math.floor(milliseconds / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  if (hours > 0) return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
  return `${pad(minutes)}:${pad(seconds)}.${pad(milliseconds % 1000, 3)}`
}
