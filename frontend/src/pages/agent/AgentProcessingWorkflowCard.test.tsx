import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AgentProcessingWorkflow } from '../../types/agent'
import AgentProcessingWorkflowCard from './AgentProcessingWorkflowCard'

function workflow(
  status: AgentProcessingWorkflow['status'],
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
    executionId: status === 'WAITING_CONFIRMATION' ? null : '601',
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

describe('AgentProcessingWorkflowCard', () => {
  it('requires explicit confirmation before processing', () => {
    const onConfirm = vi.fn()
    render(
      <AgentProcessingWorkflowCard
        workflow={workflow('WAITING_CONFIRMATION')}
        confirming={false}
        onConfirm={onConfirm}
      />,
    )

    expect(screen.getByText('等待确认')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '确认并开始处理' }))
    expect(onConfirm).toHaveBeenCalledWith('700')
  })

  it('shows a clear critic failure reason', () => {
    render(
      <AgentProcessingWorkflowCard
        workflow={workflow('FAILED')}
        confirming={false}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByText('本次处理未通过检查')).toBeInTheDocument()
    expect(screen.getByText('处理后的音频时长异常')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '确认并开始处理' })).not.toBeInTheDocument()
  })
})
