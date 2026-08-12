import type { CSSProperties } from 'react'
import { formatDuration } from '../../utils/audioTime'

interface AudioProgressBarProps {
  currentTimeSeconds: number
  durationSeconds: number
  disabled?: boolean
  onSeek: (seconds: number) => void
}

export default function AudioProgressBar({
  currentTimeSeconds,
  durationSeconds,
  disabled = false,
  onSeek,
}: AudioProgressBarProps) {
  const safeDuration = Number.isFinite(durationSeconds) && durationSeconds > 0
    ? durationSeconds
    : 0
  const safeCurrentTime = safeDuration > 0
    ? Math.min(safeDuration, Math.max(0, Number.isFinite(currentTimeSeconds) ? currentTimeSeconds : 0))
    : 0
  const progress = safeDuration > 0 ? (safeCurrentTime / safeDuration) * 100 : 0
  const style = { '--audio-progress': `${Math.min(100, Math.max(0, progress))}%` } as CSSProperties

  return (
    <input
      className="report-audio-progress"
      type="range"
      min={0}
      max={safeDuration || 1}
      step={0.01}
      value={safeCurrentTime}
      disabled={disabled || safeDuration <= 0}
      aria-label="音频播放进度"
      aria-valuetext={`${formatDuration(safeCurrentTime * 1000)} / ${formatDuration(safeDuration * 1000)}`}
      style={style}
      onChange={(event) => onSeek(event.currentTarget.valueAsNumber)}
    />
  )
}
