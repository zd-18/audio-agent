import { App } from 'antd'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult } from '../../types/api'
import type { TranscriptionTask } from '../../types/transcription'
import TranscriptionListPage from './TranscriptionListPage'

const useTranscriptionTaskListMock = vi.hoisted(() => vi.fn())
const useAudioFileListMock = vi.hoisted(() => vi.fn())
const useCreateTranscriptionMock = vi.hoisted(() => vi.fn())
const refreshTasks = vi.hoisted(() => vi.fn())
const createTranscription = vi.hoisted(() => vi.fn())

vi.mock('../../hooks/useTranscriptionTaskList', () => ({
  useTranscriptionTaskList: (query: unknown) => useTranscriptionTaskListMock(query),
}))
vi.mock('../../hooks/useAudioFileList', () => ({
  useAudioFileList: (query: unknown) => useAudioFileListMock(query),
}))
vi.mock('../../hooks/useCreateTranscription', () => ({
  useCreateTranscription: () => useCreateTranscriptionMock(),
}))
vi.mock('../../settings/UserSettingsContext', () => ({
  useUserSettings: () => ({ settings: { defaultPageSize: 10 } }),
}))

const records: TranscriptionTask[] = [
  {
    taskId: '209032737901',
    audioFileId: '209032737801',
    audioFileName: 'demo-chat.wav',
    status: 'SUCCESS',
    language: 'zh',
    enableSpeakerDiarization: true,
    progressPercent: 100,
    retryCount: 0,
    createdAt: '2026-08-20T09:30:00',
    updatedAt: '2026-08-20T09:32:00',
  },
  {
    taskId: '209032737902',
    audioFileId: '209032737801',
    audioFileName: 'demo-chat.wav',
    status: 'FAILED',
    language: 'en',
    enableSpeakerDiarization: false,
    progressPercent: 36,
    retryCount: 1,
    failureMessage: 'java.lang.IllegalStateException: FunASR worker unavailable',
    createdAt: '2026-08-20T10:30:00',
    updatedAt: '2026-08-20T10:31:00',
  },
  {
    taskId: '209032737903',
    audioFileId: '209032737802',
    audioFileName: '会议录音.wav',
    status: 'RUNNING',
    language: 'zh-CN',
    enableSpeakerDiarization: false,
    progressPercent: 52,
    retryCount: 0,
    createdAt: '2026-08-20T11:30:00',
    updatedAt: '2026-08-20T11:31:00',
  },
]

function page(items: TranscriptionTask[]): PageResult<TranscriptionTask> {
  return { records: items, current: 1, size: 10, total: items.length, pages: 1 }
}

function renderPage() {
  return render(
    <App>
      <MemoryRouter initialEntries={['/transcriptions']}>
        <Routes>
          <Route path="/transcriptions" element={<TranscriptionListPage />} />
        </Routes>
      </MemoryRouter>
    </App>,
  )
}

describe('TranscriptionListPage list experience', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: true,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }),
    })
    useTranscriptionTaskListMock.mockReturnValue({
      data: page(records),
      loading: false,
      error: null,
      refresh: refreshTasks,
    })
    useAudioFileListMock.mockReturnValue({
      data: {
        records: [{
          audioFileId: '209032737801', originalFileName: '产品会议.wav', fileSize: 2048,
          duration: 60_000, transcriptionStatus: 'SUCCESS',
        }],
        current: 1, size: 8, total: 1, pages: 1,
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
    useCreateTranscriptionMock.mockReturnValue({
      create: createTranscription, loading: false, error: null, clearError: vi.fn(),
    })
  })

  it('uses one compact, user-facing task toolbar', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '转写任务' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /生成文字稿/ })).toBeInTheDocument()
    expect(screen.getByText('全部状态')).toBeInTheDocument()
    expect(screen.queryByText('TRANSCRIPTIONS')).not.toBeInTheDocument()
    expect(screen.queryByText('音频转写')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '重置筛选' })).not.toBeInTheDocument()
  })

  it('opens audio selection in place, creates a task, and refreshes the list', async () => {
    const user = userEvent.setup()
    createTranscription.mockResolvedValue({ taskId: '209032737999' })
    renderPage()

    await user.click(screen.getByRole('button', { name: /生成文字稿/ }))

    expect(screen.getByRole('heading', { name: '转写任务' })).toBeInTheDocument()
    const dialog = screen.getByRole('dialog', { name: '选择要生成文字稿的音频' })
    expect(screen.getByText('产品会议.wav')).toBeInTheDocument()
    await user.click(screen.getByText('产品会议.wav'))
    await user.click(within(dialog).getByRole('button', { name: '生成文字稿' }))

    await waitFor(() => expect(createTranscription).toHaveBeenCalledWith('209032737801'))
    expect(refreshTasks).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog', { name: '选择要生成文字稿的音频' })).not.toBeInTheDocument()
    expect(await screen.findByText('文字稿任务已创建')).toBeInTheDocument()
  })

  it('hides unverified language metadata and gives every task a clear action', () => {
    renderPage()

    expect(screen.queryByRole('columnheader', { name: '语言' })).not.toBeInTheDocument()
    expect(screen.queryByText('中文')).not.toBeInTheDocument()
    expect(screen.queryByText('英文')).not.toBeInTheDocument()
    expect(screen.getByText('转写中')).toBeInTheDocument()
    expect(screen.getByText('语音识别服务暂时不可用')).toBeInTheDocument()
    expect(screen.queryByText(/IllegalStateException|FunASR/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /查看文字稿/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /查看详情/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /查看进度/ })).toBeInTheDocument()
    expect(screen.getAllByText('本次转写任务')).toHaveLength(3)
  })
})
