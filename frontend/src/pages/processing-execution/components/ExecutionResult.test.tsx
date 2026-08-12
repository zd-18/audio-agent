import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AudioFileRecord } from '../../../types/api'
import type { ProcessingExecution } from '../../../types/processingExecution'
import ExecutionResult from './ExecutionResult'

vi.mock('./AudioComparisonPlayer', () => ({
  default: ({ sourceFileId, resultFileId }: {
    sourceFileId: string
    resultFileId: string
  }) => (
    <div data-testid="audio-comparison-player"
      data-source-file-id={sourceFileId}
      data-result-file-id={resultFileId}>
      原音频 / 处理后音频试听
    </div>
  ),
}))

const execution: ProcessingExecution = {
  executionId: '9001',
  taskId: '1001',
  audioFileId: '2001',
  confirmationId: '6001',
  executionStatus: 'SUCCESS',
  currentStage: 'COMPLETED',
  progressPercent: 100,
  acceptedStepCount: 1,
  executableStepCount: 1,
  skippedStepCount: 0,
  retryCount: 0,
  resultFileId: '2002',
  failureCode: null,
  failureMessage: null,
  steps: [],
  startedAt: null,
  finishedAt: null,
  createdAt: null,
  updatedAt: null,
}

function file(id: string, name: string): AudioFileRecord {
  return {
    fileId: id,
    originalName: name,
    extension: 'wav',
    mimeType: 'audio/wav',
    sizeBytes: 48_000,
    durationMs: 3_000,
    fileStatus: 'AVAILABLE',
    createdAt: '2026-08-12T10:00:00',
  }
}

describe('ExecutionResult', () => {
  it('connects the source and result files to the comparison player', () => {
    render(
      <ExecutionResult
        execution={execution}
        sourceFile={file('2001', 'source.wav')}
        resultFile={file('2002', 'result.wav')}
        resultLoading={false}
        resultError={null}
        downloading={false}
        onRefreshResult={vi.fn()}
        onDownload={vi.fn()}
      />,
    )

    expect(screen.getByText('处理完成')).toBeInTheDocument()
    const player = screen.getByTestId('audio-comparison-player')
    expect(player).toHaveAttribute('data-source-file-id', '2001')
    expect(player).toHaveAttribute('data-result-file-id', '2002')
  })
})
