import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getTranscriptionTask } from '../api/transcriptions'
import type { TranscriptionTask } from '../types/transcription'
import {
  TRANSCRIPTION_POLLING_TIMEOUT_MS,
  useTranscriptionTaskPolling,
} from './useTranscriptionTaskPolling'

vi.mock('../api/transcriptions', () => ({
  getTranscriptionTask: vi.fn(),
}))

const getTaskMock = vi.mocked(getTranscriptionTask)

function task(status: TranscriptionTask['status']): TranscriptionTask {
  return {
    taskId: '9007199254740995',
    audioFileId: '9007199254740993',
    audioFileName: 'meeting.wav',
    status,
    language: 'zh',
    enableSpeakerDiarization: false,
    progressPercent: status === 'SUCCESS' ? 100 : 45,
    retryCount: 0,
    createdAt: '2026-07-22T10:00:00',
    updatedAt: '2026-07-22T10:00:01',
  }
}

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
}

describe('useTranscriptionTaskPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    getTaskMock.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('polls running tasks and stops after success', async () => {
    getTaskMock
      .mockResolvedValueOnce(task('RUNNING'))
      .mockResolvedValueOnce(task('SUCCESS'))
    const { result } = renderHook(() => (
      useTranscriptionTaskPolling('9007199254740995')
    ))

    await act(flushPromises)
    expect(result.current.task?.status).toBe('RUNNING')
    expect(getTaskMock).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
      await flushPromises()
    })
    expect(result.current.task?.status).toBe('SUCCESS')
    expect(getTaskMock).toHaveBeenCalledTimes(2)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000)
      await flushPromises()
    })
    expect(getTaskMock).toHaveBeenCalledTimes(2)
  })

  it('loads the task again when the page is mounted with the same URL ID', async () => {
    getTaskMock.mockResolvedValue(task('SUCCESS'))
    const first = renderHook(() => (
      useTranscriptionTaskPolling('9007199254740995')
    ))
    await act(flushPromises)
    first.unmount()

    const second = renderHook(() => (
      useTranscriptionTaskPolling('9007199254740995')
    ))
    await act(flushPromises)

    expect(second.result.current.task?.status).toBe('SUCCESS')
    expect(getTaskMock).toHaveBeenCalledTimes(2)
  })

  it('clears the scheduled request when the page unmounts', async () => {
    getTaskMock.mockResolvedValue(task('RUNNING'))
    const view = renderHook(() => (
      useTranscriptionTaskPolling('9007199254740995')
    ))
    await act(flushPromises)
    expect(getTaskMock).toHaveBeenCalledOnce()

    view.unmount()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000)
      await flushPromises()
    })

    expect(getTaskMock).toHaveBeenCalledOnce()
  })

  it('stops automatic polling after fifteen minutes', async () => {
    const startedAt = new Date('2026-07-22T10:00:00Z')
    vi.setSystemTime(startedAt)
    getTaskMock.mockResolvedValue(task('RUNNING'))
    const { result } = renderHook(() => (
      useTranscriptionTaskPolling('9007199254740995')
    ))
    await act(flushPromises)

    vi.setSystemTime(new Date(startedAt.getTime() + TRANSCRIPTION_POLLING_TIMEOUT_MS))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
      await flushPromises()
    })

    expect(result.current.timedOut).toBe(true)
    expect(getTaskMock).toHaveBeenCalledOnce()
  })
})
