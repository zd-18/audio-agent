import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { AgentProcessingWorkflow } from '../../types/agent'
import AgentProcessingWorkflowCard from './AgentProcessingWorkflowCard'

function workflow(
  status: AgentProcessingWorkflow['status'],
  executionId: string | null = status === 'WAITING_CONFIRMATION' ? null : '601',
): AgentProcessingWorkflow {
  return {
    workflowId: '700',
    conversationId: '10',
    userMessageId: '101',
    assistantMessageId: '102',
    taskId: '31',
    audioFileId: '21',
    planId: '401',
    confirmationId: '501',
    executionId,
    resultFileId: status === 'SUCCESS' ? '701' : null,
    status,
    summary: '裁剪无关片段并统一音量',
    steps: [
      {
        order: 1,
        operationType: 'TRIM_SEGMENT',
        title: '裁剪指定音频片段',
        reason: '移除无关片段',
        startMs: 1_000,
        endMs: 2_000,
      },
    ],
    progressPercent: status === 'EXECUTING' ? 42 : null,
    failureReason: status === 'FAILED' ? '处理后的音频时长异常' : null,
    createdAt: '2026-08-13T10:00:00Z',
    updatedAt: '2026-08-13T10:00:00Z',
    finishedAt: null,
  }
}

function renderCard(workflow: AgentProcessingWorkflow) {
  let location: ReturnType<typeof useLocation> | null = null
  function LocationProbe() {
    location = useLocation()
    return null
  }
  const result = render(
    <MemoryRouter
      initialEntries={['/transcriptions/10/agent']}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <AgentProcessingWorkflowCard
        workflow={workflow}
      />
      <LocationProbe />
    </MemoryRouter>,
  )
  return {
    currentLocation: () => location,
    unmount: () => result.unmount(),
  }
}

describe('AgentProcessingWorkflowCard', () => {
  it('routes the Agent plan into the unified confirmation page', () => {
    const { currentLocation } = renderCard(workflow('WAITING_CONFIRMATION'))

    expect(screen.getByText('等待确认')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '查看并确认处理方案' }))
    expect(currentLocation()?.pathname)
      .toBe('/analysis/tasks/31/processing-plan')
    expect(currentLocation()?.search)
      .toBe('?source=processing&audioFileId=21')
  })

  it('shows a clear critic failure reason', () => {
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AgentProcessingWorkflowCard workflow={workflow('FAILED')} />
      </MemoryRouter>,
    )

    expect(screen.getByText('本次处理未通过检查')).toBeInTheDocument()
    expect(screen.getByText('处理后的音频时长异常')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '查看并确认处理方案' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '查看处理结果' })).not.toBeInTheDocument()
  })

  it('navigates to the execution result page after success', () => {
    const { currentLocation } = renderCard(workflow('SUCCESS'))

    fireEvent.click(screen.getByRole('button', { name: '查看处理结果' }))

    expect(currentLocation()?.pathname)
      .toBe('/analysis/tasks/31/processing-execution')
  })

  it('hides the result button when no execution exists', () => {
    renderCard(workflow('SUCCESS', null))

    expect(screen.getByText('音频处理完成')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '查看处理结果' }))
      .not.toBeInTheDocument()
  })

  it('hides the result button while executing or waiting', () => {
    const { unmount } = renderCard(workflow('EXECUTING'))
    expect(screen.queryByRole('button', { name: '查看处理结果' }))
      .not.toBeInTheDocument()
    unmount()

    renderCard(workflow('WAITING_CONFIRMATION'))
    expect(screen.queryByRole('button', { name: '查看处理结果' }))
      .not.toBeInTheDocument()
  })
})
