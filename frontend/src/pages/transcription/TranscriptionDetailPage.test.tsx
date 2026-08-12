import { App as AntdApp } from 'antd'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AudioPlaybackController } from '../../hooks/useAudioPlayback'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import { useCreateTranscription } from '../../hooks/useCreateTranscription'
import { useTranscript } from '../../hooks/useTranscript'
import { useTranscriptionTaskPolling } from '../../hooks/useTranscriptionTaskPolling'
import type { Transcript, TranscriptSegment, TranscriptionTask } from '../../types/transcription'
import TranscriptionDetailPage from './TranscriptionDetailPage'

vi.mock('../../hooks/useTranscriptionTaskPolling', () => ({
  useTranscriptionTaskPolling: vi.fn(),
}))
vi.mock('../../hooks/useTranscript', () => ({ useTranscript: vi.fn() }))
vi.mock('../../hooks/useAudioPlayback', () => ({ useAudioPlayback: vi.fn() }))
vi.mock('../../hooks/useCreateTranscription', () => ({ useCreateTranscription: vi.fn() }))
vi.mock('../../api/audioFiles', () => ({ downloadAudioFile: vi.fn() }))
vi.mock('../../components/audio/ReportAudioPlayer', () => ({
  default: () => <div aria-label="源音频播放器">播放器</div>,
}))
vi.mock('../../components/content-analysis/ContentAnalysisSection', () => ({
  default: ({ onLocateSegment }: { onLocateSegment: (segmentOrder: number) => void }) => (
    <div aria-label="智能分析测试入口">
      <button type="button" onClick={() => onLocateSegment(2)}>定位原文到 #2</button>
      <button type="button" onClick={() => onLocateSegment(99)}>定位缺失片段</button>
    </div>
  ),
}))

const pollingMock = vi.mocked(useTranscriptionTaskPolling)
const transcriptMock = vi.mocked(useTranscript)
const playbackMock = vi.mocked(useAudioPlayback)
const createMock = vi.fn()
const clearCreateErrorMock = vi.fn()
const createStateMock = vi.mocked(useCreateTranscription)
const seekTo = vi.fn()
const resumeWith = vi.fn()
const taskRefresh = vi.fn()
const transcriptRefresh = vi.fn()

const successfulTask: TranscriptionTask = {
  taskId: '9007199254740995',
  audioFileId: '9007199254740993',
  audioFileName: 'meeting.wav',
  status: 'SUCCESS',
  language: 'zh',
  enableSpeakerDiarization: false,
  progressPercent: 100,
  retryCount: 0,
  startedAt: '2026-07-22T10:00:00',
  finishedAt: '2026-07-22T10:00:05',
  createdAt: '2026-07-22T10:00:00',
  updatedAt: '2026-07-22T10:00:05',
}

const segments: TranscriptSegment[] = [
  {
    segmentId: '8101',
    order: 1,
    segmentOrder: 1,
    startMs: 1000,
    endMs: 3000,
    text: '第一段真实转写',
    confidence: 0.93,
  },
  {
    segmentId: '8102',
    order: 2,
    segmentOrder: 2,
    startMs: 3000,
    endMs: 5000,
    speaker: 'speaker-1',
    text: '第二段真实转写',
  },
]

const transcript: Transcript = {
  transcriptId: '8001',
  audioFileId: successfulTask.audioFileId,
  audioFileName: successfulTask.audioFileName,
  language: 'zh',
  fullText: '第一段真实转写。第二段真实转写。',
  durationMs: 5000,
  segmentCount: 2,
  segments,
}

function player(currentTimeSeconds = 2): AudioPlaybackController {
  return {
    audioRef: { current: null },
    sourceUrl: 'https://example.test/audio.wav',
    playback: null,
    loading: false,
    refreshing: false,
    error: null,
    unsupported: false,
    currentTimeSeconds,
    durationSeconds: 5,
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
    seekTo,
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
        initialEntries={['/transcriptions/9007199254740995']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Routes>
          <Route path="/transcriptions/:taskId" element={<TranscriptionDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AntdApp>,
  )
}

describe('TranscriptionDetailPage', () => {
  beforeEach(() => {
    seekTo.mockReset()
    resumeWith.mockReset()
    taskRefresh.mockReset()
    transcriptRefresh.mockReset()
    createMock.mockReset()
    clearCreateErrorMock.mockReset()
    pollingMock.mockReturnValue({
      task: successfulTask,
      loading: false,
      refreshing: false,
      error: null,
      timedOut: false,
      refresh: taskRefresh,
      resumeWith,
    })
    transcriptMock.mockReturnValue({
      transcript,
      loading: false,
      error: null,
      refresh: transcriptRefresh,
    })
    createStateMock.mockReturnValue({
      create: createMock,
      loading: false,
      error: null,
      clearError: clearCreateErrorMock,
    })
    playbackMock.mockReturnValue(player())
  })

  it('shows the compact view by default and combines adjacent segments', () => {
    renderPage()

    expect(screen.getByText(transcript.fullText)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '简洁视图' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '逐句视图' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText('第一段真实转写 第二段真实转写')).toBeInTheDocument()
    expect(document.querySelectorAll('.transcript-paragraph')).toHaveLength(1)
    expect(document.querySelectorAll('.transcript-segment')).toHaveLength(0)
    expect(screen.queryByText('未知说话人')).not.toBeInTheDocument()
  })

  it('links a successful transcript to its contextual Agent conversation page', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /智能问答/ })).toHaveAttribute(
      'href',
      '/transcriptions/9007199254740995/agent',
    )
  })

  it('switches to the existing sentence view and preserves the active segment', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '逐句视图' }))

    expect(screen.getByRole('button', { name: '逐句视图' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /第一段真实转写/ }))
      .toHaveAttribute('aria-current', 'true')
    expect(screen.getByRole('button', { name: /第二段真实转写/ })).toBeInTheDocument()
    expect(seekTo).not.toHaveBeenCalled()
    expect(transcriptRefresh).not.toHaveBeenCalled()
  })

  it('seeks and starts playback from the paragraph range', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /播放段落 1/ }))

    expect(seekTo).toHaveBeenCalledWith(1, { play: true, endSeconds: 5 })
  })

  it('expands and collapses original segment details', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /展开明细/ }))

    expect(screen.getByRole('button', { name: /#1.*第一段真实转写/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /#2.*第二段真实转写/ })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /收起明细/ }))

    expect(screen.queryByRole('button', { name: /#1.*第一段真实转写/ })).not.toBeInTheDocument()
  })

  it('seeks and starts playback when an original segment detail is clicked', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /展开明细/ }))
    await userEvent.click(screen.getByRole('button', { name: /#2.*第二段真实转写/ }))

    expect(seekTo).toHaveBeenCalledWith(3, { play: true, endSeconds: 5 })
  })

  it('seeks and starts playback when a sentence-view segment is clicked', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '逐句视图' }))
    await userEvent.click(screen.getByRole('button', { name: /第一段真实转写/ }))

    expect(seekTo).toHaveBeenCalledWith(1, { play: true, endSeconds: 3 })
  })

  it('expands, scrolls to, and highlights the exact segment located by analysis', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '定位原文到 #2' }))

    const target = await screen.findByRole('button', { name: /#2.*第二段真实转写/ })
    expect(target).toHaveAttribute('aria-current', 'true')
    expect(target).toHaveFocus()
    expect(seekTo).toHaveBeenCalledWith(3)
  })

  it('keeps the located segment highlighted when switching views', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '定位原文到 #2' }))
    await userEvent.click(screen.getByRole('button', { name: '逐句视图' }))

    expect(screen.getByRole('button', { name: /第二段真实转写/ }))
      .toHaveAttribute('aria-current', 'true')
    expect(seekTo).toHaveBeenCalledTimes(1)
  })

  it('renders 18 segments as five compact paragraphs instead of 18 large cards', () => {
    const manySegments = Array.from({ length: 18 }, (_, index): TranscriptSegment => ({
      segmentId: `segment-${index + 1}`,
      order: index + 1,
      segmentOrder: index + 1,
      startMs: index * 4_000,
      endMs: (index + 1) * 4_000,
      text: `第 ${index + 1} 个片段`,
    }))
    transcriptMock.mockReturnValue({
      transcript: {
        ...transcript,
        fullText: manySegments.map((segment) => segment.text).join('。'),
        durationMs: 72_000,
        segmentCount: manySegments.length,
        segments: manySegments,
      },
      loading: false,
      error: null,
      refresh: transcriptRefresh,
    })

    renderPage()

    expect(document.querySelectorAll('.transcript-paragraph')).toHaveLength(5)
    expect(document.querySelectorAll('.transcript-segment')).toHaveLength(0)
  })

  it('copies the complete transcript', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /复制全文/ }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(transcript.fullText))
  })

  it('refreshes both task state and transcript result after success', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /刷新结果/ }))

    expect(taskRefresh).toHaveBeenCalledOnce()
    expect(transcriptRefresh).toHaveBeenCalledOnce()
  })

  it('shows friendly empty states for empty transcript content and segments', () => {
    transcriptMock.mockReturnValue({
      transcript: {
        ...transcript,
        fullText: '',
        segmentCount: 0,
        segments: [],
      },
      loading: false,
      error: null,
      refresh: transcriptRefresh,
    })
    renderPage()

    expect(screen.getByText('暂无完整文字稿内容')).toBeInTheDocument()
    expect(screen.getByText('暂无文字片段')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /复制全文/ })).toBeDisabled()
  })

  it('shows a friendly failure and creates a new task on retry', async () => {
    const failedTask: TranscriptionTask = {
      ...successfulTask,
      status: 'FAILED',
      progressPercent: 0,
      failureMessage: '音频中未识别到可转写的语音内容或格式无效',
    }
    const pendingTask: TranscriptionTask = {
      ...successfulTask,
      status: 'PENDING',
      progressPercent: 0,
    }
    pollingMock.mockReturnValue({
      task: failedTask,
      loading: false,
      refreshing: false,
      error: null,
      timedOut: false,
      refresh: vi.fn(),
      resumeWith,
    })
    transcriptMock.mockReturnValue({
      transcript: null,
      loading: false,
      error: null,
      refresh: transcriptRefresh,
    })
    createMock.mockResolvedValue(pendingTask)
    renderPage()

    expect(screen.getByText(failedTask.failureMessage!)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /重新创建转写任务/ }))

    await waitFor(() => expect(createMock).toHaveBeenCalledWith(failedTask.audioFileId))
  })

  it('offers a retry when transcript loading fails', async () => {
    transcriptMock.mockReturnValue({
      transcript: null,
      loading: false,
      error: '文字稿服务暂时不可用',
      refresh: transcriptRefresh,
    })
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /重\s*试/ }))

    expect(transcriptRefresh).toHaveBeenCalledOnce()
  })
})
