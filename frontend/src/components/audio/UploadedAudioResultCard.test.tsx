import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import UploadedAudioResultCard from './UploadedAudioResultCard'

vi.mock('../analysis/CreateAnalysisTaskButton', () => ({
  default: ({ buttonType }: { buttonType?: string }) => (
    <button type="button" data-button-type={buttonType}>开始智能诊断</button>
  ),
}))

vi.mock('../transcription/CreateTranscriptionButton', () => ({
  default: ({ buttonType, label }: { buttonType?: string; label?: string }) => (
    <button type="button" data-button-type={buttonType}>{label}</button>
  ),
}))

describe('UploadedAudioResultCard', () => {
  it('shows user-facing file information and a clear CTA hierarchy', () => {
    const onContinue = vi.fn()
    const result = {
      fileId: '9007199254740995',
      originalName: 'silence-test.mp3',
      extension: 'mp3',
      mimeType: 'audio/mpeg',
      sizeBytes: 36_557,
      durationMs: 12_000,
      fileStatus: 'AVAILABLE',
      createdAt: '2026-08-21T02:30:00Z',
      sha256: 'a'.repeat(64),
    }

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <UploadedAudioResultCard result={result} onContinue={onContinue} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '音频上传成功' })).toBeInTheDocument()
    expect(screen.getByText('文件已完成完整性校验并安全保存。')).toBeInTheDocument()
    expect(screen.getByText('silence-test.mp3')).toBeInTheDocument()
    expect(screen.getByText('35.7 KB')).toBeInTheDocument()
    expect(screen.getByText('MP3')).toBeInTheDocument()
    expect(screen.getByText('00:12')).toBeInTheDocument()
    expect(screen.getByText('可用')).toBeInTheDocument()
    expect(screen.queryByText(result.fileId)).not.toBeInTheDocument()
    expect(screen.queryByText(result.mimeType)).not.toBeInTheDocument()
    expect(screen.queryByText(result.fileStatus)).not.toBeInTheDocument()
    expect(screen.queryByText(result.sha256)).not.toBeInTheDocument()

    expect(screen.getByRole('button', { name: '开始智能诊断' })).toHaveAttribute('data-button-type', 'primary')
    expect(screen.getByRole('button', { name: '生成文字稿' })).toHaveAttribute('data-button-type', 'default')
    expect(screen.getByRole('link', { name: /智能处理/ })).toHaveAttribute('href', `/audio/files/${result.fileId}/agent`)
    expect(screen.getByRole('link', { name: /查看文件/ })).toHaveAttribute('href', `/audio/files/${result.fileId}`)
    expect(screen.queryByRole('button', { name: '开始转写' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /继续上传/ }))
    expect(onContinue).toHaveBeenCalledTimes(1)
  })
})
