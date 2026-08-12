import type { AgentConversation, AgentMessage } from '../types/agent'

export function normalizeAgentMessages(messages: AgentMessage[] | null | undefined) {
  const byId = new Map<string, AgentMessage>()
  for (const message of Array.isArray(messages) ? messages : []) {
    if (!message?.messageId) continue
    byId.set(message.messageId, {
      ...message,
      citations: Array.isArray(message.citations) ? message.citations : [],
    })
  }
  return [...byId.values()].sort((left, right) => (
    left.sequenceNo - right.sequenceNo
    || left.createdAt.localeCompare(right.createdAt)
    || left.messageId.localeCompare(right.messageId)
  ))
}

export function selectLatestActiveConversation(
  conversations: AgentConversation[],
  transcriptId: string,
) {
  return conversations
    .filter((conversation) => (
      conversation.transcriptId === transcriptId
      && conversation.status === 'ACTIVE'
    ))
    .sort((left, right) => {
      const leftTime = Date.parse(left.lastMessageAt || left.updatedAt || left.createdAt)
      const rightTime = Date.parse(right.lastMessageAt || right.updatedAt || right.createdAt)
      return rightTime - leftTime
    })[0] ?? null
}

export function createClientRequestId() {
  return window.crypto.randomUUID()
}
