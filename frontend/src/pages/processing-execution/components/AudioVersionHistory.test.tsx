import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AudioVersion } from '../../../types/audioVersion'
import AudioVersionHistory from './AudioVersionHistory'

const createTask = vi.hoisted(() => vi.fn())
const resetError = vi.hoisted(() => vi.fn())

vi.mock('../../../hooks/useCreateAnalysisTask', () => ({
  useCreateAnalysisTask: () => ({
    create: createTask,
    loading: false,
    error: null,
    resetError,
  }),
}))

const rootId = '9223372036854775001'
const versionOneId = '9223372036854775002'

const versions: AudioVersion[] = [
  {
    audioFileId: rootId,
    parentAudioFileId: null,
    versionNo: 0,
    versionSummary: '原始版本',
    originalVersion: true,
    fileName: 'source.wav',
    extension: 'wav',
    mimeType: 'audio/wav',
    sizeBytes: 48_000,
    durationMs: 3_000,
    createdAt: '2026-08-12T10:00:00',
  },
  {
    audioFileId: versionOneId,
    parentAudioFileId: rootId,
    versionNo: 1,
    versionSummary: '音量优化',
    originalVersion: false,
    fileName: 'result.wav',
    extension: 'wav',
    mimeType: 'audio/wav',
    sizeBytes: 49_000,
    durationMs: 3_050,
    createdAt: '2026-08-12T10:01:00',
  },
]

function renderHistory(overrides: Partial<React.ComponentProps<typeof AudioVersionHistory>> = {}) {
  const props: React.ComponentProps<typeof AudioVersionHistory> = {
    versions,
    loading: false,
    error: null,
    currentAudioFileId: versionOneId,
    selectedAudioFileId: versionOneId,
    downloading: false,
    onRefresh: vi.fn(),
    onPreview: vi.fn(),
    onDownload: vi.fn(),
    ...overrides,
  }

  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="*" element={<AudioVersionHistory {...props} />} />
        <Route path="/analysis/tasks/:taskId" element={<div>已进入新修改任务</div>} />
      </Routes>
    </MemoryRouter>,
  )
  return props
}

describe('AudioVersionHistory', () => {
  beforeEach(() => {
    createTask.mockReset()
    resetError.mockReset()
  })

  it('shows the ordered user-facing versions without exposing snowflake ids', () => {
    renderHistory()

    expect(screen.getByText('原始版本', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText('版本 1', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText('当前版本')).toBeInTheDocument()
    expect(screen.queryByText(rootId, { exact: false })).not.toBeInTheDocument()
    expect(screen.queryByText(versionOneId, { exact: false })).not.toBeInTheDocument()
    expect(screen.queryByText('parentAudioFileId', { exact: false })).not.toBeInTheDocument()
  })

  it('previews and downloads the selected historical version using string ids', async () => {
    const user = userEvent.setup()
    const onPreview = vi.fn()
    const onDownload = vi.fn()
    renderHistory({ selectedAudioFileId: versionOneId, onPreview, onDownload })

    await user.click(screen.getAllByRole('button', { name: /试听/ })[0])
    expect(onPreview).toHaveBeenCalledWith(versions[0])

    await user.click(screen.getAllByRole('button', { name: /下载/ })[0])
    expect(onDownload).toHaveBeenCalledWith(rootId, 'source.wav')
    expect(typeof onDownload.mock.calls[0][0]).toBe('string')
  })

  it('creates a new task from the explicitly selected version', async () => {
    const user = userEvent.setup()
    createTask.mockResolvedValue({ taskId: '9223372036854775999' })
    renderHistory()

    await user.click(screen.getAllByRole('button', { name: /基于此版本继续修改/ })[1])
    expect(screen.getByRole('dialog')).toHaveTextContent('版本 1 · 音量优化')
    await user.click(screen.getByRole('button', { name: '创建新的修改任务' }))

    expect(createTask).toHaveBeenCalledWith(versionOneId)
    expect(typeof createTask.mock.calls[0][0]).toBe('string')
    expect(await screen.findByText('已进入新修改任务')).toBeInTheDocument()
  })
})
