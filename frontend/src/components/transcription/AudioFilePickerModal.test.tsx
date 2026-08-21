import { App } from 'antd'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AudioFileListItem, PageResult } from '../../types/api'
import AudioFilePickerModal from './AudioFilePickerModal'

const useAudioFileListMock = vi.hoisted(() => vi.fn())
const useCreateTranscriptionMock = vi.hoisted(() => vi.fn())

vi.mock('../../hooks/useAudioFileList', () => ({
  useAudioFileList: (query: unknown) => useAudioFileListMock(query),
}))
vi.mock('../../hooks/useCreateTranscription', () => ({
  useCreateTranscription: () => useCreateTranscriptionMock(),
}))

const files = [
  {
    audioFileId: '209032737801',
    originalName: 'demo-chat.wav',
    fileSize: 2_048_000,
    contentType: 'audio/wav',
    duration: 125_000,
    sha256: 'do-not-render',
    transcriptionStatus: 'SUCCESS',
  },
  {
    audioFileId: '209032737802',
    fileName: 'interview.mp3',
    fileSize: 1024,
    duration: 5_000,
  },
] satisfies (AudioFileListItem & { originalName?: string; fileName?: string })[]

function page(records = files): PageResult<AudioFileListItem> {
  return { records, current: 1, size: 8, total: records.length, pages: 1 }
}

function renderModal(props?: Partial<React.ComponentProps<typeof AudioFilePickerModal>>) {
  return render(
    <App>
      <AudioFilePickerModal
        open
        onCancel={vi.fn()}
        onCreated={vi.fn()}
        {...props}
      />
    </App>,
  )
}

describe('AudioFilePickerModal', () => {
  const create = vi.fn()
  const clearError = vi.fn()

  beforeEach(() => {
    useAudioFileListMock.mockReturnValue({
      data: page(), loading: false, error: null, refresh: vi.fn(),
    })
    useCreateTranscriptionMock.mockReturnValue({
      create, loading: false, error: null, clearError,
    })
  })

  it('loads a concise audio list and disables creation before selection', () => {
    renderModal()

    expect(screen.getByRole('dialog', { name: '选择要生成文字稿的音频' })).toBeInTheDocument()
    expect(useAudioFileListMock).toHaveBeenCalledWith({ current: 1, size: 8, keyword: '' })
    expect(screen.getByText('demo-chat.wav')).toBeInTheDocument()
    expect(screen.getByText('interview.mp3')).toBeInTheDocument()
    expect(screen.getByText('WAV · 02:05 · 1.95 MB')).toBeInTheDocument()
    expect(screen.queryByText(/格式 WAV|时长 02:05|大小 1.95 MB/)).not.toBeInTheDocument()
    expect(screen.getByText('已有文字稿')).toBeInTheDocument()
    expect(screen.queryByText('209032737801')).not.toBeInTheDocument()
    expect(screen.queryByText('do-not-render')).not.toBeInTheDocument()
    expect(screen.queryByText('audio/wav')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '生成文字稿' })).toBeDisabled()
  })

  it('searches by file name and creates the task for one selected audio', async () => {
    const user = userEvent.setup()
    const onCreated = vi.fn()
    const onCancel = vi.fn()
    create.mockResolvedValue({ taskId: 'task-1' })
    renderModal({ onCreated, onCancel })

    await user.type(screen.getByLabelText('按文件名搜索'), 'demo{enter}')
    await waitFor(() => {
      expect(useAudioFileListMock).toHaveBeenLastCalledWith({ current: 1, size: 8, keyword: 'demo' })
    })

    const fileName = screen.getByText('demo-chat.wav')
    await user.hover(fileName)
    expect(await screen.findByRole('tooltip')).toHaveTextContent('demo-chat.wav')
    await user.click(fileName)
    expect(screen.getByRole('radio', { name: '选择音频：demo-chat.wav' })).toBeChecked()
    expect(fileName.closest('.audio-picker__item')).toHaveClass('is-selected')
    expect(screen.getByRole('button', { name: '生成文字稿' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: '生成文字稿' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith('209032737801'))
    expect(onCreated).toHaveBeenCalledTimes(1)
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('文字稿任务已创建')).toBeInTheDocument()
  })

  it('replaces an English backend exception with a Chinese error', () => {
    useCreateTranscriptionMock.mockReturnValue({
      create, loading: false, error: 'InternalServerException: worker unavailable', clearError,
    })
    renderModal()

    expect(screen.getByText('文字稿任务创建失败，请稍后重试')).toBeInTheDocument()
    expect(screen.queryByText(/InternalServerException|worker unavailable/)).not.toBeInTheDocument()
  })
})
