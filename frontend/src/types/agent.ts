import type { PageResult, ResourceId } from './api'

export type AgentConversationStatus = 'ACTIVE' | 'ARCHIVED'
export type AgentMessageRole = 'USER' | 'ASSISTANT'
export type AgentMessageStatus = 'SUCCESS' | 'FAILED' | 'PROCESSING'

export interface AgentConversation {
  conversationId: ResourceId
  transcriptId: ResourceId
  title: string
  status: AgentConversationStatus
  modelName: string
  promptVersion: string
  lastMessageId: ResourceId | null
  lastMessageAt: string | null
  audioFileName: string | null
  audioDurationMs: number | null
  createdAt: string
  updatedAt: string
}

export type AgentConversationPage = PageResult<AgentConversation>

export interface AgentCitation {
  citationId: ResourceId
  segmentId: ResourceId
  segmentOrder: number
  startMs: number
  endMs: number
  quote: string
}

export interface AgentMessage {
  messageId: ResourceId
  role: AgentMessageRole
  content: string | null
  status: AgentMessageStatus
  sequenceNo: number
  replyToMessageId: ResourceId | null
  clientRequestId: string | null
  modelName: string | null
  promptVersion: string | null
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  failureCode: string | null
  failureMessage: string | null
  citations: AgentCitation[]
  createdAt: string
  finishedAt: string | null
}

export type AgentMessagePage = PageResult<AgentMessage>

export interface CreateAgentConversationRequest {
  transcriptId: ResourceId
  title: string | null
}

export interface SendAgentMessageRequest {
  content: string
  clientRequestId: string
}

export interface AgentMessagePair {
  userMessage: AgentMessage
  assistantMessage: AgentMessage
}
