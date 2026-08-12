import { describe, expect, it } from 'vitest'
import type { AgentConversation, AgentMessage } from '../types/agent'
import { normalizeAgentMessages, selectLatestActiveConversation } from './agent'

function message(messageId: string, sequenceNo: number): AgentMessage {
  return {
    messageId,
    role: 'USER',
    content: `消息 ${messageId}`,
    status: 'SUCCESS',
    sequenceNo,
    replyToMessageId: null,
    clientRequestId: null,
    modelName: null,
    promptVersion: null,
    promptTokens: null,
    completionTokens: null,
    totalTokens: null,
    failureCode: null,
    failureMessage: null,
    citations: [],
    createdAt: `2026-08-04T10:00:0${sequenceNo}`,
    finishedAt: null,
  }
}

function conversation(
  conversationId: string,
  transcriptId: string,
  status: AgentConversation['status'],
  updatedAt: string,
): AgentConversation {
  return {
    conversationId,
    transcriptId,
    title: '测试对话',
    status,
    modelName: 'test-model',
    promptVersion: 'test-prompt',
    lastMessageId: null,
    lastMessageAt: null,
    audioFileName: null,
    audioDurationMs: null,
    createdAt: updatedAt,
    updatedAt,
  }
}

describe('Agent message utilities', () => {
  it('sorts messages by sequence and removes duplicate message IDs', () => {
    const normalized = normalizeAgentMessages([
      message('2084462342950039559', 2),
      message('2084462342950039558', 1),
      { ...message('2084462342950039558', 1), content: '保留后返回的数据' },
    ])

    expect(normalized.map((item) => item.messageId)).toEqual([
      '2084462342950039558',
      '2084462342950039559',
    ])
    expect(normalized[0].content).toBe('保留后返回的数据')
  })

  it('selects the newest active conversation for the exact transcript string', () => {
    const selected = selectLatestActiveConversation([
      conversation('2084462342950039553', '2084176870403137537', 'ACTIVE', '2026-08-04T09:00:00'),
      conversation('2084462342950039554', '2084176870403137537', 'ACTIVE', '2026-08-04T10:00:00'),
      conversation('2084462342950039555', '2084176870403137538', 'ACTIVE', '2026-08-04T11:00:00'),
      conversation('2084462342950039556', '2084176870403137537', 'ARCHIVED', '2026-08-04T12:00:00'),
    ], '2084176870403137537')

    expect(selected?.conversationId).toBe('2084462342950039554')
    expect(typeof selected?.conversationId).toBe('string')
  })
})
