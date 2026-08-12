import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createContentAnalysisTask,
  getContentAnalysisResult,
  getContentAnalysisTask,
  retryContentAnalysisTask,
} from '../api/contentAnalysis'
import type {
  ContentAnalysisResult,
  ContentAnalysisTask,
} from '../types/contentAnalysis'
import { useContentAnalysis } from './useContentAnalysis'

vi.mock('../api/contentAnalysis', () => ({
  createContentAnalysisTask: vi.fn(),
  getContentAnalysisTask: vi.fn(),
  getContentAnalysisResult: vi.fn(),
  retryContentAnalysisTask: vi.fn(),
}))

const createMock = vi.mocked(createContentAnalysisTask)
const taskMock = vi.mocked(getContentAnalysisTask)
const resultMock = vi.mocked(getContentAnalysisResult)
const retryMock = vi.mocked(retryContentAnalysisTask)
const transcriptId = '9007199254740993'
const taskId = '9007199254740995'

const pendingTask: ContentAnalysisTask = {
  taskId,
  transcriptId,
  status: 'PENDING',
  analysisTypes: ['SUMMARY', 'KEY_POINTS', 'CHAPTERS', 'SPEECH_ISSUES'],
  progressPercent: 0,
  modelName: 'deepseek-test',
  retryCount: 0,
  createdAt: '2026-07-31T10:00:00',
  updatedAt: '2026-07-31T10:00:00',
}

const successTask: ContentAnalysisTask = {
  ...pendingTask,
  status: 'SUCCESS',
  progressPercent: 100,
}

const analysisResult: ContentAnalysisResult = {
  taskId,
  transcriptId,
  audioFileId: '9007199254740997',
  audioFileName: 'meeting.wav',
  modelName: 'deepseek-test',
  promptVersion: 'content-analysis-v1',
  summary: {
    oneSentence: '一句摘要',
    detailed: '详细摘要',
    topics: ['主题'],
  },
  keyPoints: [],
  chapters: [],
  speechIssues: [],
  usage: {
    promptTokens: 10,
    completionTokens: 20,
    totalTokens: 30,
  },
  createdAt: '2026-07-31T10:01:00',
}

describe('useContentAnalysis', () => {
  beforeEach(() => {
    window.localStorage.clear()
    taskMock.mockResolvedValue(pendingTask)
    resultMock.mockResolvedValue(analysisResult)
    retryMock.mockResolvedValue(pendingTask)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('creates a task without converting snowflake IDs', async () => {
    createMock.mockResolvedValue(pendingTask)
    const { result, unmount } = renderHook(
      () => useContentAnalysis(transcriptId),
    )

    await act(async () => {
      await result.current.start([
        'SUMMARY',
        'KEY_POINTS',
        'CHAPTERS',
        'SPEECH_ISSUES',
      ])
    })

    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({ transcriptId }),
    )
    expect(result.current.taskId).toBe(taskId)
    expect(window.localStorage.getItem(
      `audio-agent:content-analysis:${transcriptId}`,
    )).toBe(taskId)
    unmount()
  })

  it('restores a successful task and loads its result once', async () => {
    window.localStorage.setItem(
      `audio-agent:content-analysis:${transcriptId}`,
      taskId,
    )
    taskMock.mockResolvedValue(successTask)
    const { result } = renderHook(
      () => useContentAnalysis(transcriptId),
    )

    await waitFor(() => {
      expect(result.current.result?.taskId).toBe(taskId)
    })

    expect(taskMock).toHaveBeenCalledTimes(1)
    expect(resultMock).toHaveBeenCalledTimes(1)
  })

  it('stops polling after failure', async () => {
    vi.useFakeTimers()
    window.localStorage.setItem(
      `audio-agent:content-analysis:${transcriptId}`,
      taskId,
    )
    taskMock.mockResolvedValue({
      ...pendingTask,
      status: 'FAILED',
      failureCode: 'AI_RESPONSE_INVALID',
      failureMessage: '智能分析中的原文引用无法验证，请重试。',
    })
    const { result } = renderHook(
      () => useContentAnalysis(transcriptId),
    )

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(result.current.task?.status).toBe('FAILED')
    expect(result.current.task?.failureMessage).toBe(
      '智能分析中的原文引用无法验证，请重试。',
    )
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(taskMock).toHaveBeenCalledTimes(1)
  })

  it('clears the polling timer on unmount', async () => {
    vi.useFakeTimers()
    window.localStorage.setItem(
      `audio-agent:content-analysis:${transcriptId}`,
      taskId,
    )
    const { unmount } = renderHook(
      () => useContentAnalysis(transcriptId),
    )

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(taskMock).toHaveBeenCalledTimes(1)
    unmount()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
    })
    expect(taskMock).toHaveBeenCalledTimes(1)
  })
})
