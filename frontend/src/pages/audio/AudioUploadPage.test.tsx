import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAudioUpload } from '../../hooks/useAudioUpload'
import AudioUploadPage from './AudioUploadPage'

vi.mock('../../hooks/useAudioUpload', () => ({ useAudioUpload: vi.fn() }))

describe('AudioUploadPage ready state', () => {
  const upload = vi.fn()
  const file = new File([new Uint8Array(36_557)], 'silence-test.mp3', { type: 'audio/mpeg' })

  beforeEach(() => {
    vi.mocked(useAudioUpload).mockReturnValue({
      file,
      status: 'ready',
      progress: 0,
      hashProgress: 0,
      uploadedCount: 0,
      totalChunks: 0,
      error: null,
      result: null,
      instantUpload: false,
      resumed: false,
      selectFile: vi.fn(),
      upload,
      pause: vi.fn(),
      reset: vi.fn(),
    })
  })

  it('keeps the ready state focused on the selected file and its actions', () => {
    render(<AudioUploadPage />)

    expect(screen.getByText('格式与大小')).toBeInTheDocument()
    expect(screen.getByText('稳定续传')).toBeInTheDocument()
    expect(screen.getByText('避免重复')).toBeInTheDocument()
    expect(screen.getByText('silence-test.mp3')).toBeInTheDocument()
    expect(screen.getByText('35.7 KB')).toBeInTheDocument()
    expect(screen.getByText('MP3')).toBeInTheDocument()
    expect(screen.getByText('等待上传')).toBeInTheDocument()
    expect(screen.queryByText('audio/mpeg')).not.toBeInTheDocument()
    expect(screen.queryByText('AUDIO INGEST')).not.toBeInTheDocument()
    expect(screen.queryByText('可靠上传流程')).not.toBeInTheDocument()
    expect(screen.queryByText('页面刷新后重新选择同一文件，会自动检查并只上传缺失部分。')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /开始上传/ }))
    expect(upload).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: /重新选择/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '移除已选择文件' })).toBeInTheDocument()
  })

  it('replaces the completed percentage with a server finalizing state', () => {
    vi.mocked(useAudioUpload).mockReturnValue({
      file,
      status: 'merging',
      progress: 100,
      hashProgress: 100,
      uploadedCount: 1,
      totalChunks: 1,
      error: null,
      result: null,
      instantUpload: false,
      resumed: false,
      selectFile: vi.fn(),
      upload,
      pause: vi.fn(),
      reset: vi.fn(),
    })

    render(<AudioUploadPage />)

    expect(screen.getByText('文件已上传，正在校验并保存…')).toBeInTheDocument()
    expect(screen.getByText('完成后将自动显示上传结果。')).toBeInTheDocument()
    expect(screen.queryByText('100%')).not.toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /重新选择/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: '移除已选择文件' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /开始上传|正在完成/ })).not.toBeInTheDocument()
  })
})
