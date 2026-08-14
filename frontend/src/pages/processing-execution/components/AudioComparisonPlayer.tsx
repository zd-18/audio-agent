import { SwapOutlined } from '@ant-design/icons'
import { Button, Segmented } from 'antd'
import { useEffect, useRef, useState } from 'react'
import ReportAudioPlayer from '../../../components/audio/ReportAudioPlayer'
import { useAudioPlayback } from '../../../hooks/useAudioPlayback'
import { useUserSettings } from '../../../settings/UserSettingsContext'

type AudioKind = 'source' | 'result'

interface AudioComparisonPlayerProps {
  sourceFileId: string
  resultFileId: string
  sourceFileName?: string | null
  resultFileName?: string | null
  resultDurationMs?: number | null
  resultVersionLabel?: string
  downloading: boolean
  onDownload: (fileId: string, fileName: string) => void
}

export default function AudioComparisonPlayer({
  sourceFileId,
  resultFileId,
  sourceFileName,
  resultFileName,
  resultDurationMs,
  resultVersionLabel = '修复结果',
  downloading,
  onDownload,
}: AudioComparisonPlayerProps) {
  const [activeKind, setActiveKind] = useState<AudioKind>('result')
  const { settings } = useUserSettings()
  const pendingPositionRef = useRef<number | null>(null)
  const activeFileId = activeKind === 'source' ? sourceFileId : resultFileId
  const activeName = activeKind === 'source' ? sourceFileName : resultFileName
  const player = useAudioPlayback(activeFileId, activeKind === 'result' ? resultDurationMs : undefined)

  useEffect(() => {
    if (pendingPositionRef.current === null || player.playback?.fileId !== activeFileId) return
    player.seek(pendingPositionRef.current)
    pendingPositionRef.current = null
  }, [activeFileId, player, player.playback?.fileId])

  const switchTo = (nextKind: AudioKind) => {
    if (nextKind === activeKind) return
    const audio = player.audioRef.current
    if (audio) audio.pause()
    pendingPositionRef.current = settings?.preservePlaybackPosition
      ? player.currentTimeSeconds
      : 0
    setActiveKind(nextKind)
  }

  const otherKind: AudioKind = activeKind === 'source' ? 'result' : 'source'

  return (
    <section className="processing-audio-comparison" aria-labelledby="processing-audio-comparison-title">
      <div className="processing-execution-section-heading processing-audio-comparison__heading">
        <div>
          <span>A/B LISTENING</span>
          <h2 id="processing-audio-comparison-title">处理前后版本对比</h2>
          <p>当前播放：{activeKind === 'source' ? '本次处理输入' : resultVersionLabel}。切换时不会同时播放两条音频。</p>
        </div>
        <div className="processing-audio-comparison__switches">
          <Segmented<AudioKind>
            aria-label="选择试听音频"
            value={activeKind}
            options={[
              { label: '本次处理输入', value: 'source' },
              { label: resultVersionLabel, value: 'result' },
            ]}
            onChange={switchTo}
          />
          <Button icon={<SwapOutlined />} onClick={() => switchTo(otherKind)}>
            {settings?.preservePlaybackPosition ? '从相同位置切换' : '切换并从头播放'}
          </Button>
        </div>
      </div>

      <ReportAudioPlayer
        key={activeFileId}
        sectionId="processing-comparison-player"
        eyebrow={activeKind === 'source' ? 'PROCESSING INPUT' : 'SELECTED VERSION'}
        player={player}
        fallbackFileName={activeName || (activeKind === 'source' ? '本次处理输入' : resultVersionLabel)}
        downloading={downloading}
        onDownload={() => onDownload(activeFileId, activeName || `audio-${activeFileId}`)}
      />
    </section>
  )
}
