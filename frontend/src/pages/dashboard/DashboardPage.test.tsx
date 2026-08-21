import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResult, AudioFileListItem } from '../../types/api'
import type { UserTaskProgress } from '../../types/userTask'
import DashboardPage from './DashboardPage'

const useAudioFileListMock = vi.hoisted(() => vi.fn())
const useUserTasksMock = vi.hoisted(() => vi.fn())

vi.mock('../../hooks/useAudioFileList', () => ({
  useAudioFileList: (query: unknown) => useAudioFileListMock(query),
}))
vi.mock('../../hooks/useUserTasks', () => ({
  useUserTasks: (size?: number) => useUserTasksMock(size),
}))

const audioRecord: AudioFileListItem = {
  audioFileId: '9007199254740999',
  originalFileName: '客户访谈.wav',
  fileSize: 1024,
  contentType: 'audio/wav',
  duration: 90_000,
  status: 'AVAILABLE',
  createdAt: '2026-08-20T09:30:00',
  transcriptionTaskId: null,
  transcriptionStatus: null,
}

const taskRecord: UserTaskProgress = {
  taskId: '9007199254741888',
  audioFileId: audioRecord.audioFileId,
  fileName: '客户访谈.wav',
  status: 'PROCESSING',
  statusLabel: '内部状态不会展示',
  currentStage: 'ANALYSIS',
  currentStageLabel: '智能诊断',
  progressPercent: 45,
  currentActivity: '正在分析',
  requiresUserAction: false,
  failureReason: null,
  stages: [],
  nextActions: [],
  completedOperations: [],
  resultPath: null,
  createdAt: '2026-08-20T09:35:00',
  completedAt: null,
}

function page<T>(records: T[]): PageResult<T> {
  return { records, current: 1, size: 5, total: records.length, pages: records.length ? 1 : 0 }
}

function audioState(overrides: Record<string, unknown> = {}) {
  return {
    data: page([audioRecord]),
    loading: false,
    error: null,
    refresh: vi.fn(),
    ...overrides,
  }
}

function taskState(overrides: Record<string, unknown> = {}) {
  return {
    data: page([taskRecord]),
    loading: false,
    refreshing: false,
    error: null,
    refresh: vi.fn(),
    ...overrides,
  }
}

function renderPage() {
  return render(<MemoryRouter><DashboardPage /></MemoryRouter>)
}

describe('DashboardPage', () => {
  beforeEach(() => {
    useAudioFileListMock.mockReturnValue(audioState())
    useUserTasksMock.mockReturnValue(taskState())
  })

  it('uses recent real data and removes demo metrics and duplicate quick actions', () => {
    renderPage()

    expect(useAudioFileListMock).toHaveBeenCalledWith({ current: 1, size: 5 })
    expect(useUserTasksMock).toHaveBeenCalledWith(5)
    expect(screen.getByRole('heading', { name: '最近音频' })).toBeInTheDocument()
    expect(screen.getAllByText('客户访谈.wav')).toHaveLength(2)
    expect(screen.getByText('01:30')).toBeInTheDocument()
    expect(screen.getByText('WAV')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '最近任务' })).toBeInTheDocument()
    expect(screen.getByText('处理中')).toBeInTheDocument()
    expect(screen.getByText('45%')).toBeInTheDocument()
    expect(screen.getByText('智能诊断')).toBeInTheDocument()

    expect(screen.queryByText('演示数据')).not.toBeInTheDocument()
    expect(screen.queryByText(/仅用于展示工作台布局/)).not.toBeInTheDocument()
    expect(screen.queryByText('已上传音频')).not.toBeInTheDocument()
    expect(screen.queryByText('待处理任务')).not.toBeInTheDocument()
    expect(screen.queryByText('分析成功')).not.toBeInTheDocument()
    expect(screen.queryByText('处理失败')).not.toBeInTheDocument()
    expect(screen.queryByText('24')).not.toBeInTheDocument()
    expect(screen.queryByText('3')).not.toBeInTheDocument()
    expect(screen.queryByText('18')).not.toBeInTheDocument()
    expect(screen.queryByText('2')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '快捷操作' })).not.toBeInTheDocument()
    expect(screen.queryByText('查询文件')).not.toBeInTheDocument()
    expect(screen.queryByText('音频转写')).not.toBeInTheDocument()
  })

  it('shows compact empty states without inventing data', () => {
    useAudioFileListMock.mockReturnValue(audioState({ data: page([]) }))
    useUserTasksMock.mockReturnValue(taskState({ data: page([]) }))
    renderPage()

    expect(screen.getByText('还没有上传音频')).toBeInTheDocument()
    expect(screen.getByText('上传第一段音频开始分析和处理')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '上传音频' })).toHaveAttribute('href', '/audio/upload')
    expect(screen.getByText('还没有任务记录')).toBeInTheDocument()
  })

  it('keeps the hero actions and section links pointed at existing pages', () => {
    renderPage()

    expect(screen.getByRole('link', { name: '上传新音频' })).toHaveAttribute('href', '/audio/upload')
    expect(screen.getByRole('link', { name: '查看全部音频' })).toHaveAttribute('href', '/audio/files')

    const audioSection = screen.getByRole('region', { name: '最近音频' })
    const taskSection = screen.getByRole('region', { name: '最近任务' })
    expect(within(audioSection).getByRole('link', { name: '查看全部' })).toHaveAttribute('href', '/audio/files')
    expect(within(taskSection).getByRole('link', { name: '查看全部' })).toHaveAttribute('href', '/tasks')
  })

  it('shows independent Chinese errors and retries each failed area', async () => {
    const refreshAudio = vi.fn()
    const refreshTasks = vi.fn()
    useAudioFileListMock.mockReturnValue(audioState({ data: page([]), error: 'network', refresh: refreshAudio }))
    useUserTasksMock.mockReturnValue(taskState({ data: page([]), error: 'network', refresh: refreshTasks }))
    renderPage()

    expect(screen.getByText('最近音频暂时无法加载')).toBeInTheDocument()
    expect(screen.getByText('最近任务暂时无法加载')).toBeInTheDocument()
    const retryButtons = screen.getAllByRole('button', { name: '重新加载' })
    await userEvent.click(retryButtons[0])
    await userEvent.click(retryButtons[1])
    expect(refreshAudio).toHaveBeenCalledOnce()
    expect(refreshTasks).toHaveBeenCalledOnce()
  })
})
