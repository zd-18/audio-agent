import { App } from 'antd'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useContentAnalysis } from '../../hooks/useContentAnalysis'
import ContentAnalysisSection from './ContentAnalysisSection'

vi.mock('../../hooks/useContentAnalysis', () => ({
  useContentAnalysis: vi.fn(),
}))

const analysisMock = vi.mocked(useContentAnalysis)
const start = vi.fn()

function renderSection() {
  return render(
    <App>
      <ContentAnalysisSection transcriptId="8001" onLocateSegment={vi.fn()} />
    </App>,
  )
}

describe('ContentAnalysisSection presentation', () => {
  beforeEach(() => {
    analysisMock.mockReturnValue({
      taskId: undefined,
      task: null,
      result: null,
      creating: false,
      retrying: false,
      loading: false,
      error: null,
      timedOut: false,
      start,
      retry: vi.fn(),
      refresh: vi.fn(),
    })
  })

  it('shows a compact, provider-neutral empty state', async () => {
    renderSection()

    expect(screen.getByText('尚未生成智能分析')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '生成智能分析' })).toBeInTheDocument()
    expect(screen.queryByText(/DeepSeek/i)).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '生成智能分析' }))
    expect(screen.getByRole('dialog', { name: '开始智能分析' })).toBeInTheDocument()
  })
})
