import { App as AntdApp } from 'antd'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { RefObject } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { downloadAudioFile } from '../../api/audioFiles'
import { useCreateAnalysisTask } from '../../hooks/useCreateAnalysisTask'
import { useCreateTranscription } from '../../hooks/useCreateTranscription'
import { useAudioFileDetail } from '../../hooks/useAudioFileDetail'
import type { AudioPlaybackController } from '../../hooks/useAudioPlayback'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import { useTranscriptionTaskList } from '../../hooks/useTranscriptionTaskList'
import AudioFileDetailPage from './AudioFileDetailPage'

vi.mock('../../api/audioFiles', () => ({ downloadAudioFile: vi.fn() }))
vi.mock('../../hooks/useAudioFileDetail', () => ({ useAudioFileDetail: vi.fn() }))
vi.mock('../../hooks/useAudioPlayback', () => ({ useAudioPlayback: vi.fn() }))
vi.mock('../../hooks/useTranscriptionTaskList', () => ({ useTranscriptionTaskList: vi.fn() }))
vi.mock('../../hooks/useCreateAnalysisTask', () => ({ useCreateAnalysisTask: vi.fn() }))
vi.mock('../../hooks/useCreateTranscription', () => ({ useCreateTranscription: vi.fn() }))

const file = {
  fileId: '2089000000001234',
  originalName: '现场采访 demo.wav',
  extension: 'wav',
  mimeType: 'audio/wav',
  sizeBytes: 2 * 1024 * 1024,
  sha256: '40f89e395b66422f931d78a3f4e05d48b89fd9d10721f1e7f8e75ca1f46f4fd4',
  fileRole: 'ORIGINAL',
  fileStatus: 'AVAILABLE',
  durationMs: 125_000,
  createdAt: '2026-08-19T10:30:00Z',
}

const transcript = {
  taskId: '3099000000005678',
  audioFileId: file.fileId,
  audioFileName: file.originalName,
  status: 'SUCCESS' as const,
  language: 'zh',
  enableSpeakerDiarization: false,
  progressPercent: 100,
  retryCount: 0,
  createdAt: '2026-08-19T10:31:00Z',
  updatedAt: '2026-08-19T10:32:00Z',
}

function playbackController(error: string | null = null): AudioPlaybackController {
  return {
    audioRef: { current: null } as RefObject<HTMLAudioElement>,
    sourceUrl: error ? undefined : 'https://example.test/audio.wav',
    playback: error ? null : {
      fileId: file.fileId,
      fileName: file.originalName,
      mimeType: file.mimeType,
      playbackUrl: 'https://example.test/audio.wav',
      expiresAt: '2026-08-19T12:00:00Z',
      expiresInSeconds: 600,
    },
    loading: false,
    refreshing: false,
    error,
    unsupported: false,
    currentTimeSeconds: 12,
    durationSeconds: 125,
    isPlaying: false,
    isWaiting: false,
    playbackRange: null,
    volume: 0.8,
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
  }
}

function renderPage() {
  return render(
    <AntdApp>
      <MemoryRouter
        initialEntries={[`/audio/files/${file.fileId}`]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Routes>
          <Route path="/audio/files/:audioFileId" element={<AudioFileDetailPage />} />
          <Route path="/audio/files/:audioFileId/agent" element={<div>智能处理工作区</div>} />
          <Route path="/transcriptions/:taskId/agent" element={<div>智能问答工作区</div>} />
          <Route path="/transcriptions/:taskId" element={<div>文字稿详情</div>} />
          <Route path="/tasks" element={<div>诊断任务中心</div>} />
        </Routes>
      </MemoryRouter>
    </AntdApp>,
  )
}

describe('AudioFileDetailPage', () => {
  beforeEach(() => {
    vi.mocked(useAudioFileDetail).mockReturnValue({
      data: file,
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
    vi.mocked(useAudioPlayback).mockReturnValue(playbackController())
    vi.mocked(useTranscriptionTaskList).mockReturnValue({
      data: { records: [transcript], current: 1, size: 1, total: 1, pages: 1 },
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
    vi.mocked(useCreateAnalysisTask).mockReturnValue({
      create: vi.fn().mockResolvedValue({ taskId: '4100' }),
      loading: false,
      error: null,
      resetError: vi.fn(),
    })
    vi.mocked(useCreateTranscription).mockReturnValue({
      create: vi.fn(),
      loading: false,
      error: null,
      clearError: vi.fn(),
    })
    vi.mocked(downloadAudioFile).mockResolvedValue(undefined)
  })

  it('shows the player, three equal core entries, and the reorganized file information', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '接下来你想做什么？' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '智能诊断' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '智能问答' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '智能处理' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '播放音频' })).toBeInTheDocument()
    expect(screen.getByLabelText('音量')).toBeInTheDocument()
    expect(screen.getByText('00:12 / 02:05')).toBeInTheDocument()
    expect(screen.getAllByText(file.originalName).length).toBeGreaterThan(0)
    expect(screen.getByText('2.00 MB')).toBeInTheDocument()
    expect(screen.getByText('audio/wav · WAV')).toBeInTheDocument()
    expect(screen.getByText('文件编号：20890000…1234')).toBeInTheDocument()
    expect(screen.getByText('技术信息')).toBeInTheDocument()
  })

  it('keeps the smart processing and smart Q&A routes unchanged', async () => {
    renderPage()
    expect(screen.getByRole('link', { name: /智能处理$/ })).toHaveAttribute(
      'href',
      `/audio/files/${file.fileId}/agent`,
    )

    fireEvent.click(screen.getByRole('button', { name: /智能问答$/ }))
    expect(await screen.findByText('智能问答工作区')).toBeInTheDocument()
  })

  it('keeps the current smart diagnosis creation flow', async () => {
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /智能诊断$/ }))

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('开始智能诊断')
    fireEvent.click(within(dialog).getByRole('button', { name: '开始智能诊断' }))

    expect(await screen.findByText('诊断任务中心')).toBeInTheDocument()
  })

  it('keeps the download action working', async () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /下载文件$/ }))
    await waitFor(() => {
      expect(downloadAudioFile).toHaveBeenCalledWith(
        file.fileId,
        file.originalName,
        expect.any(AbortSignal),
      )
    })
  })

  it('keeps the transcript shortcut routed to the existing detail page', async () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /查看文字稿$/ }))
    expect(await screen.findByText('文字稿详情')).toBeInTheDocument()
  })

  it('hides the transcript shortcut when no transcript exists without creating a dead entry', () => {
    vi.mocked(useTranscriptionTaskList).mockReturnValue({
      data: { records: [], current: 1, size: 1, total: 0, pages: 0 },
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    renderPage()

    expect(screen.queryByRole('button', { name: /查看文字稿$/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /智能问答$/ })).toBeInTheDocument()
  })

  it('shows a Chinese recovery message when audio playback loading fails', () => {
    vi.mocked(useAudioPlayback).mockReturnValue(playbackController('音频加载失败，请稍后重试。'))

    renderPage()

    expect(screen.getByText('音频加载失败，请稍后重试。')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /重新加载音频$/ })).toBeInTheDocument()
  })

  it('preserves the existing ownership error state and does not expose file actions', () => {
    vi.mocked(useAudioFileDetail).mockReturnValue({
      data: null,
      loading: false,
      error: '无权访问该音频文件',
      refresh: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('文件查询失败')).toBeInTheDocument()
    expect(screen.getByText('无权访问该音频文件')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /智能处理$/ })).not.toBeInTheDocument()
    expect(useAudioPlayback).toHaveBeenCalledWith(undefined, undefined)
  })
})
