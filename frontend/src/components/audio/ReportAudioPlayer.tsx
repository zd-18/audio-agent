import {
  AudioOutlined,
  DownloadOutlined,
  LoadingOutlined,
  PauseOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  AudioMutedOutlined,
  SoundOutlined,
} from '@ant-design/icons'
import { Alert, Button, Select, Skeleton, Tooltip } from 'antd'
import type { AudioPlaybackController } from '../../hooks/useAudioPlayback'
import { formatDuration } from '../../utils/audioTime'
import AudioProgressBar from './AudioProgressBar'

const RATE_OPTIONS = [0.75, 1, 1.25, 1.5, 2].map((rate) => ({
  value: rate,
  label: `${rate}x`,
}))

interface ReportAudioPlayerProps {
  player: AudioPlaybackController
  fallbackFileName?: string | null
  downloading?: boolean
  onDownload: () => void
  sectionId?: string
  eyebrow?: string
  compact?: boolean
}

export default function ReportAudioPlayer({
  player,
  fallbackFileName,
  downloading = false,
  onDownload,
  sectionId = 'report-audio-player',
  eyebrow = 'SOURCE AUDIO',
  compact = false,
}: ReportAudioPlayerProps) {
  const fileName = player.playback?.fileName || fallbackFileName || '未命名音频'
  const controlsDisabled = player.loading || player.refreshing || !player.sourceUrl || player.unsupported
  const timeLabel = `${formatDuration(player.currentTimeSeconds * 1000)} / ${formatDuration(player.durationSeconds * 1000)}`
  const statusText = player.loading
    ? '正在获取播放地址'
    : player.refreshing
      ? '正在刷新播放地址'
      : player.isWaiting
        ? '正在缓冲'
        : player.unsupported
          ? '音频不可用'
          : player.error
            ? '播放失败'
            : null

  return (
    <section
      id={sectionId}
      className={`report-audio-player report-reveal-section${compact ? ' is-compact' : ''}`}
      aria-label={compact ? `${fileName} 播放器` : undefined}
      aria-labelledby={compact ? undefined : `${sectionId}-title`}
    >
      <audio
        ref={player.audioRef}
        src={player.sourceUrl}
        preload="metadata"
        aria-hidden="true"
      />

      {(!compact || statusText) && (
        <div className="report-audio-player__header">
          {!compact && (
            <div className="report-audio-player__identity">
              <span className="report-audio-player__signal" aria-hidden="true">
                <AudioOutlined />
              </span>
              <div>
                <span className="report-section-kicker">{eyebrow}</span>
                <h2 id={`${sectionId}-title`} title={fileName}>{fileName}</h2>
              </div>
            </div>
          )}
          {statusText && (
            <div className="report-audio-player__status" aria-live="polite">
              {(player.loading || player.refreshing || player.isWaiting) && <LoadingOutlined spin />}
              <span>{statusText}</span>
            </div>
          )}
        </div>
      )}

      {player.loading && !player.playback ? (
        <div className="report-audio-player__skeleton" aria-label="正在加载音频播放器">
          <Skeleton.Input active block size="small" />
          <Skeleton.Button active shape="circle" />
        </div>
      ) : (
        <>
          {player.error && (
            <Alert
              className="report-audio-player__alert"
              type={player.unsupported ? 'warning' : 'error'}
              showIcon
              message={player.error}
              action={(
                <div className="report-audio-player__error-actions">
                  {!player.unsupported && (
                    <Button
                      icon={<ReloadOutlined />}
                      loading={player.refreshing}
                      onClick={() => { void player.reload() }}
                    >
                      重新加载音频
                    </Button>
                  )}
                  <Button icon={<DownloadOutlined />} loading={downloading} onClick={onDownload}>
                    下载音频
                  </Button>
                </div>
              )}
            />
          )}

          <div className="report-audio-player__controls">
            <Tooltip title={player.isPlaying ? '暂停' : '播放'}>
              <Button
                className="report-audio-player__play"
                type="primary"
                shape="circle"
                icon={player.isPlaying ? <PauseOutlined /> : <PlayCircleOutlined />}
                aria-label={player.isPlaying ? '暂停音频' : '播放音频'}
                disabled={controlsDisabled}
                onClick={() => { void player.togglePlayback() }}
              />
            </Tooltip>

            <div className="report-audio-player__progress-group">
              <AudioProgressBar
                currentTimeSeconds={player.currentTimeSeconds}
                durationSeconds={player.durationSeconds}
                disabled={controlsDisabled}
                onSeek={player.seek}
              />
              <span className="report-audio-player__time" aria-live="off">{timeLabel}</span>
            </div>

            <div className="report-audio-player__volume">
              <Button
                type="text"
                icon={player.muted ? <AudioMutedOutlined /> : <SoundOutlined />}
                aria-label={player.muted ? '取消静音' : '静音'}
                aria-pressed={player.muted}
                disabled={controlsDisabled}
                onClick={player.toggleMuted}
              />
              <input
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={player.muted ? 0 : player.volume}
                aria-label="音量"
                disabled={controlsDisabled}
                onChange={(event) => player.setVolume(event.currentTarget.valueAsNumber)}
              />
            </div>

            <Select
              className="report-audio-player__rate"
              aria-label="播放速度"
              value={player.playbackRate}
              options={RATE_OPTIONS}
              disabled={controlsDisabled}
              onChange={player.setPlaybackRate}
            />
          </div>
        </>
      )}
    </section>
  )
}
