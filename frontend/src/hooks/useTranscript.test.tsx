import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getTranscript } from '../api/transcriptions'
import type { Transcript } from '../types/transcription'
import { useTranscript } from './useTranscript'

vi.mock('../api/transcriptions', () => ({
  getTranscript: vi.fn(),
}))

const getTranscriptMock = vi.mocked(getTranscript)
const transcript: Transcript = {
  transcriptId: '9007199254740997',
  audioFileId: '9007199254740993',
  audioFileName: 'meeting.wav',
  language: 'zh',
  fullText: '第一段文字',
  durationMs: 5616,
  speakerCount: null,
  segmentCount: 1,
  segments: [{
    segmentId: '9007199254740995',
    order: 1,
    segmentOrder: 1,
    startMs: 0,
    endMs: 5616,
    speaker: null,
    text: '第一段文字',
    confidence: null,
  }],
}

describe('useTranscript', () => {
  beforeEach(() => {
    getTranscriptMock.mockReset()
  })

  it('loads the complete transcript and its embedded segments after success', async () => {
    getTranscriptMock.mockResolvedValue(transcript)
    const { result } = renderHook(() => (
      useTranscript('9007199254740995', true)
    ))

    await waitFor(() => expect(result.current.transcript).toEqual(transcript))

    expect(getTranscriptMock).toHaveBeenCalledOnce()
    expect(getTranscriptMock.mock.calls[0][0]).toBe('9007199254740995')
    expect(result.current.transcript?.segments).toHaveLength(1)
  })

  it('normalizes a missing segments array to an empty list', async () => {
    getTranscriptMock.mockResolvedValue({
      ...transcript,
      segments: undefined,
    } as unknown as Transcript)
    const { result } = renderHook(() => (
      useTranscript('9007199254740995', true)
    ))

    await waitFor(() => expect(result.current.transcript).not.toBeNull())

    expect(result.current.transcript?.segments).toEqual([])
  })
})
