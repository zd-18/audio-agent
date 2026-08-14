import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { retryAnalysisTask } from '../../api/analysisTasks'
import type { UserTaskProgress } from '../../types/userTask'
import UserTasksPage from './UserTasksPage'

const useUserTasks = vi.fn()

vi.mock('../../hooks/useUserTasks', () => ({
  useUserTasks: () => useUserTasks(),
}))

vi.mock('../../api/analysisTasks', () => ({
  retryAnalysisTask: vi.fn().mockResolvedValue({}),
}))

const stages: UserTaskProgress['stages'] = [
  ['FILE_UPLOAD', '文件上传', 'COMPLETED', '文件上传完成'],
  ['AUDIO_PARSING', '音频解析', 'FAILED', '文件解析失败，请确认音频文件是否完整。'],
  ['SPEECH_TRANSCRIPTION', '语音转写', 'PENDING', '尚未开始'],
  ['INTELLIGENT_ANALYSIS', '智能分析', 'PENDING', '尚未开始'],
  ['PROCESSING_PLAN', '处理方案', 'PENDING', '尚未开始'],
  ['USER_CONFIRMATION', '用户确认', 'PENDING', '尚未开始'],
  ['AUDIO_PROCESSING', '音频处理', 'PENDING', '尚未开始'],
  ['RESULT_GENERATION', '结果生成', 'PENDING', '尚未开始'],
].map(([code, label, status, description]) => ({
  code,
  label,
  status: status as UserTaskProgress['stages'][number]['status'],
  progressPercent: status === 'COMPLETED' ? 100 : 0,
  description,
}))

describe('UserTasksPage', () => {
  beforeEach(() => {
    useUserTasks.mockReturnValue({
      data: {
        records: [{
          taskId: '9007199254740993',
          audioFileId: '9007199254740995',
          fileName: '访谈录音.wav',
          status: 'FAILED',
          statusLabel: '未完成',
          currentStage: 'AUDIO_PARSING',
          currentStageLabel: '音频解析',
          progressPercent: 13,
          currentActivity: '文件解析失败，请确认音频文件是否完整。',
          requiresUserAction: true,
          failureReason: '文件解析失败，请确认音频文件是否完整。',
          stages,
          nextActions: [{
            type: 'RETRY_ANALYSIS',
            label: '重新分析',
            path: '/analysis/tasks/9007199254740993',
            primary: true,
          }],
          completedOperations: ['文件上传'],
          resultPath: null,
          createdAt: '2026-08-13T10:00:00',
          completedAt: null,
        } satisfies UserTaskProgress],
        current: 1,
        size: 100,
        total: 1,
        pages: 1,
      },
      loading: false,
      refreshing: false,
      error: null,
      refresh: vi.fn(),
    })
  })

  it('shows a productized failure and retries without opening technical details', async () => {
    render(<MemoryRouter><UserTasksPage /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: '任务进度' })).toBeInTheDocument()
    expect(screen.getAllByText('文件解析失败，请确认音频文件是否完整。').length).toBeGreaterThan(0)
    await userEvent.click(screen.getByRole('button', { name: /重新分析/ }))
    expect(retryAnalysisTask).toHaveBeenCalledWith('9007199254740993')
    expect(screen.queryByText(/FFprobe|RabbitMQ|executionId|failureCode/)).not.toBeInTheDocument()
  })
})
