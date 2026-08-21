import { App as AntdApp } from 'antd'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AudioPlaybackController } from '../../hooks/useAudioPlayback'
import ReportAudioPlayer from './ReportAudioPlayer'

function player(overrides: Partial<AudioPlaybackController> = {}): AudioPlaybackController {
  return {
    audioRef: { current: null },
    sourceUrl: 'https://example.test/demo-chat.wav',
    playback: null,
    loading: false,
    refreshing: false,
    error: null,
    unsupported: false,
    currentTimeSeconds: 0,
    durationSeconds: 29,
    isPlaying: false,
    isWaiting: false,
    playbackRange: null,
    volume: 1,
    muted: false,
    playbackRate: 1,
    pause: vi.fn(),
    resume: vi.fn(),
    togglePlayback: vi.fn(),
    seek: vi.fn(),
    seekTo: vi.fn(),
    setVolume: vi.fn(),
    toggleMuted: vi.fn(),
    setPlaybackRate: vi.fn(),
    reload: vi.fn(),
    ...overrides,
  }
}

function renderPlayer(controller: AudioPlaybackController) {
  return render(
    <AntdApp>
      <ReportAudioPlayer
        player={controller}
        fallbackFileName="demo-chat.wav"
        onDownload={vi.fn()}
        eyebrow="音频播放器"
        compact
      />
    </AntdApp>,
  )
}

describe('ReportAudioPlayer', () => {
  it('shows only playback controls in the normal compact state', () => {
    renderPlayer(player())

    expect(screen.queryByText('音频播放器')).not.toBeInTheDocument()
    expect(screen.queryByText('已就绪')).not.toBeInTheDocument()
    expect(screen.queryByText('正在播放')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '播放音频' })).toBeInTheDocument()
    expect(screen.getByLabelText('音量')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '播放速度' })).toBeInTheDocument()
    expect(screen.getByText('00:00 / 00:29')).toBeInTheDocument()
  })

  it('keeps necessary loading and failure feedback', () => {
    const { rerender } = renderPlayer(player({ isWaiting: true }))
    expect(screen.getByText('正在缓冲')).toBeInTheDocument()

    rerender(
      <AntdApp>
        <ReportAudioPlayer
          player={player({ error: '音频加载失败，请稍后重试。' })}
          fallbackFileName="demo-chat.wav"
          onDownload={vi.fn()}
          compact
        />
      </AntdApp>,
    )
    expect(screen.getByText('播放失败')).toBeInTheDocument()
    expect(screen.getByText('音频加载失败，请稍后重试。')).toBeInTheDocument()
  })
})
