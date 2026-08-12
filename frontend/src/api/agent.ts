import type {
  AgentConversation,
  AgentConversationPage,
  AgentMessagePage,
  AgentMessagePair,
  CreateAgentConversationRequest,
  SendAgentMessageRequest,
} from '../types/agent'
import { apiRequest, isValidResourceId } from './http'

const AGENT_CONVERSATIONS_PATH = '/api/agent/conversations'

export interface AgentConversationListParams {
  current?: number
  size?: number
  transcriptId?: string
  status?: string
}

export function createAgentConversation(
  request: CreateAgentConversationRequest,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(request.transcriptId)) throw new Error('文字稿 ID 无效')
  return apiRequest<AgentConversation>(AGENT_CONVERSATIONS_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal,
  })
}

export function getAgentConversations(
  params: AgentConversationListParams = {},
  signal?: AbortSignal,
) {
  const search = new URLSearchParams({
    current: String(params.current ?? 1),
    size: String(params.size ?? 20),
  })
  if (params.transcriptId) {
    if (!isValidResourceId(params.transcriptId)) throw new Error('文字稿 ID 无效')
    search.set('transcriptId', params.transcriptId)
  }
  if (params.status) search.set('status', params.status)
  return apiRequest<AgentConversationPage>(`${AGENT_CONVERSATIONS_PATH}?${search.toString()}`, {
    signal,
  })
}

export function getAgentConversation(conversationId: string, signal?: AbortSignal) {
  if (!isValidResourceId(conversationId)) throw new Error('会话 ID 无效')
  return apiRequest<AgentConversation>(
    `${AGENT_CONVERSATIONS_PATH}/${encodeURIComponent(conversationId)}`,
    { signal },
  )
}

export function getAgentMessages(
  conversationId: string,
  current = 1,
  size = 50,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(conversationId)) throw new Error('会话 ID 无效')
  const search = new URLSearchParams({ current: String(current), size: String(size) })
  return apiRequest<AgentMessagePage>(
    `${AGENT_CONVERSATIONS_PATH}/${encodeURIComponent(conversationId)}/messages?${search.toString()}`,
    { signal },
  )
}

export function sendAgentMessage(
  conversationId: string,
  request: SendAgentMessageRequest,
  signal?: AbortSignal,
) {
  if (!isValidResourceId(conversationId)) throw new Error('会话 ID 无效')
  return apiRequest<AgentMessagePair>(
    `${AGENT_CONVERSATIONS_PATH}/${encodeURIComponent(conversationId)}/messages`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal,
    },
  )
}
