import { act, render, renderHook, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UploadProgressCard from '../components/audio/UploadProgressCard'
import {
  completeMultipartUpload,
  getMultipartUploadProgress,
  initializeMultipartUpload,
  uploadMultipartChunk,
} from '../api/audioFiles'
import { sha256File } from '../utils/sha256File'
import { useAudioUpload } from './useAudioUpload'

vi.mock('../api/audioFiles', () => ({
  completeMultipartUpload: vi.fn(),
  getMultipartUploadProgress: vi.fn(),
  initializeMultipartUpload: vi.fn(),
  uploadMultipartChunk: vi.fn(),
}))

vi.mock('../utils/sha256File', () => ({
  sha256File: vi.fn(),
}))

const CHUNK_SIZE = 8 * 1024 * 1024

describe('useAudioUpload resume', () => {
  beforeEach(() => {
    vi.mocked(sha256File).mockResolvedValue('a'.repeat(64))
    vi.mocked(initializeMultipartUpload).mockResolvedValue({
      uploadId: 'resume-upload-id',
      status: 'UPLOADING',
      instantUpload: false,
      resumed: true,
      chunkSize: CHUNK_SIZE,
      totalChunks: 3,
      uploadedChunks: [0, 2],
    })
    vi.mocked(getMultipartUploadProgress).mockResolvedValue({
      uploadId: 'resume-upload-id',
      status: 'UPLOADING',
      sizeBytes: CHUNK_SIZE * 3,
      chunkSize: CHUNK_SIZE,
      totalChunks: 3,
      uploadedCount: 2,
      uploadedChunks: [0, 2],
    })
    vi.mocked(completeMultipartUpload).mockResolvedValue({
      uploadId: 'resume-upload-id',
      status: 'COMPLETED',
      instantUpload: false,
      audioFile: { fileId: 'audio-file-id' },
    })
  })

  it('restores progress and uploads only missing chunks', async () => {
    let finishChunk: ((value: {
      uploadId: string
      status: 'UPLOADING'
      chunkIndex: number
      uploadedCount: number
      totalChunks: number
    }) => void) | undefined
    vi.mocked(uploadMultipartChunk).mockImplementation(() => new Promise((resolve) => {
      finishChunk = resolve
    }))
    const file = {
      name: 'upload-500mb-demo.wav',
      type: 'audio/wav',
      size: CHUNK_SIZE * 3,
      slice: (start: number, end: number) => new Blob([new Uint8Array(end - start)]),
    } as File
    const { result } = renderHook(() => useAudioUpload())

    act(() => {
      result.current.selectFile(file)
    })
    act(() => {
      void result.current.upload()
    })

    await waitFor(() => {
      expect(result.current.resumed).toBe(true)
      expect(result.current.uploadedCount).toBe(2)
      expect(result.current.totalChunks).toBe(3)
      expect(result.current.progress).toBe(67)
    })
    expect(uploadMultipartChunk).toHaveBeenCalledTimes(1)
    expect(uploadMultipartChunk).toHaveBeenCalledWith(
      'resume-upload-id', 1, expect.any(Blob), expect.any(AbortSignal),
    )

    act(() => {
      finishChunk?.({
        uploadId: 'resume-upload-id',
        status: 'UPLOADING',
        chunkIndex: 1,
        uploadedCount: 3,
        totalChunks: 3,
      })
    })
    await waitFor(() => expect(result.current.status).toBe('success'))
    expect(completeMultipartUpload).toHaveBeenCalledWith(
      'resume-upload-id', expect.any(AbortSignal),
    )
  })

  it('shows the resume notice in Chinese', () => {
    render(
      <UploadProgressCard
        status="uploading"
        progress={10}
        hashProgress={100}
        uploadedCount={6}
        totalChunks={63}
        resumed
      />,
    )

    expect(screen.getByText('检测到未完成上传，正在从断点继续')).toBeInTheDocument()
    expect(screen.getByText('已恢复 6 / 63 片，只上传剩余部分。')).toBeInTheDocument()
  })
})
