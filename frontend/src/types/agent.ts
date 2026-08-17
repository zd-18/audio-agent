import type { PageResult, ResourceId } from './api'

export type AgentConversationStatus = 'ACTIVE' | 'ARCHIVED'
export type AgentMessageRole = 'USER' | 'ASSISTANT'
export type AgentMessageStatus = 'SUCCESS' | 'FAILED' | 'PROCESSING'
export type AgentRequestMode = 'CHAT' | 'PROCESSING'
export type AgentWorkflowStatus =
  | 'PLANNING'
  | 'WAITING_CONFIRMATION'
  | 'EXECUTING'
  | 'REVIEWING'
  | 'SUCCESS'
  | 'FAILED'

export interface AgentConversation {
  conversationId: ResourceId
  transcriptId: ResourceId | null
  audioFileId: ResourceId | null
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
  transcriptId?: ResourceId | null
  audioFileId?: ResourceId | null
  title: string | null
}

export interface SendAgentMessageRequest {
  content: string
  clientRequestId: string
  mode?: AgentRequestMode
}

export interface AgentMessagePair {
  userMessage: AgentMessage
  assistantMessage: AgentMessage
  processingWorkflow?: AgentProcessingWorkflow | null
}

export interface AgentProcessingWorkflowStep {
  order: number
  operationType: 'NORMALIZE_VOLUME' | 'TRIM_SEGMENT' | 'DENOISE' | 'SILENCE_CLEANUP'
  title: string
  reason: string | null
  startMs: number | null
  endMs: number | null
}

export interface AgentProcessingWorkflow {
  workflowId: ResourceId
  conversationId: ResourceId
  userMessageId: ResourceId
  assistantMessageId: ResourceId
  taskId: ResourceId
  audioFileId: ResourceId
  planId: ResourceId | null
  confirmationId: ResourceId | null
  executionId: ResourceId | null
  resultFileId: ResourceId | null
  status: AgentWorkflowStatus
  summary: string | null
  steps: AgentProcessingWorkflowStep[]
  progressPercent: number | null
  failureReason: string | null
  createdAt: string
  updatedAt: string
  finishedAt: string | null
}
