import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AudioFileListItem, PageResult } from '../../types/api'
import AudioFileLookupPage from './AudioFileLookupPage'

const useAudioFileListMock = vi.hoisted(() => vi.fn())

vi.mock('../../hooks/useAudioFileList', () => ({
  useAudioFileList: (query: unknown) => useAudioFileListMock(query),
}))
vi.mock('../../settings/UserSettingsContext', () => ({
  useUserSettings: () => ({ settings: { defaultPageSize: 10 } }),
}))
vi.mock('../../components/analysis/CreateAnalysisTaskButton', () => ({
  default: () => <button type="button">创建分析任务</button>,
}))
vi.mock('../../components/transcription/CreateTranscriptionButton', () => ({
  default: () => <button type="button">创建转写</button>,
}))

const longFileName = '2026年度客户访谈与产品体验复盘会议完整录音最终版本.wav'

const records: AudioFileListItem[] = [
  {
    audioFileId: '209032737857',
    originalFileName: longFileName,
    fileSize: 1024,
    contentType: 'audio/wav',
    duration: 90_000,
    status: 'AVAILABLE',
    createdAt: '2026-08-20T09:30:00',
    transcriptionTaskId: null,
    transcriptionStatus: 'SUCCESS',
  },
  {
    audioFileId: '209032737858',
    originalFileName: '无扩展名录音',
    fileSize: 2048,
    contentType: 'audio/mpeg',
    duration: 30_000,
    status: 'PROCESSING',
    createdAt: '2026-08-20T10:00:00',
    transcriptionTaskId: null,
    transcriptionStatus: null,
  },
]

function page(items: AudioFileListItem[]): PageResult<AudioFileListItem> {
  return { records: items, current: 1, size: 10, total: items.length, pages: 1 }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/audio/files']}>
      <Routes>
        <Route path="/audio/files" element={<AudioFileLookupPage />} />
        <Route path="/audio/files/:audioFileId" element={<div>音频详情目标页</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AudioFileLookupPage list experience', () => {
  beforeEach(() => {
    useAudioFileListMock.mockReturnValue({
      data: page(records),
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
  })

  it('shows user-facing file information without exposing the AudioFile id', async () => {
    renderPage()

    expect(screen.getByText('WAV')).toBeInTheDocument()
    expect(screen.getByText('MP3')).toBeInTheDocument()
    expect(screen.getByText('可用')).toBeInTheDocument()
    expect(screen.getByText('处理中')).toBeInTheDocument()
    expect(screen.getByText('转写完成')).toBeInTheDocument()
    expect(screen.queryByText('209032737857')).not.toBeInTheDocument()

    await userEvent.hover(screen.getByText(longFileName))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(longFileName)
  })

  it('opens details from the row with mouse and keyboard', async () => {
    const user = userEvent.setup()
    const { unmount } = renderPage()
    const row = screen.getByRole('row', { name: `查看音频详情：${longFileName}` })

    await user.click(row)
    expect(screen.getByText('音频详情目标页')).toBeInTheDocument()

    unmount()
    renderPage()
    const keyboardRow = screen.getByRole('row', { name: `查看音频详情：${longFileName}` })
    keyboardRow.focus()
    await user.keyboard('{Enter}')
    expect(screen.getByText('音频详情目标页')).toBeInTheDocument()
  })

  it('does not trigger row navigation from the more-actions button', async () => {
    const user = userEvent.setup()
    renderPage()
    const row = screen.getByRole('row', { name: `查看音频详情：${longFileName}` })

    await user.click(within(row).getByRole('button', { name: `${longFileName}更多操作` }))

    expect(screen.queryByText('音频详情目标页')).not.toBeInTheDocument()
    expect(screen.getByRole('menu')).toBeInTheDocument()
  })
})
