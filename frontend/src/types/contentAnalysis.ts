import type { ResourceId } from './api'

export type ContentAnalysisType =
  | 'SUMMARY'
  | 'KEY_POINTS'
  | 'CHAPTERS'
  | 'SPEECH_ISSUES'

export type ContentAnalysisTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'

export type SummaryStyle = 'CONCISE' | 'STANDARD' | 'DETAILED'
export type TimePrecision = 'SEGMENT' | 'TRANSCRIPT'

export type SpeechIssueType =
  | 'REPETITION'
  | 'FILLER_WORD'
  | 'INCOMPLETE_SENTENCE'
  | 'REDUNDANCY'
  | 'LOGIC_JUMP'
  | 'AMBIGUOUS_EXPRESSION'
  | 'UNNECESSARY_DEVIATION'

export type SpeechIssueSeverity = 'LOW' | 'MEDIUM' | 'HIGH'

export interface ContentAnalysisTask {
  taskId: ResourceId
  transcriptId: ResourceId
  status: ContentAnalysisTaskStatus
  analysisTypes: ContentAnalysisType[]
  progressPercent: number
  modelName: string
  retryCount: number
  failureCode?: string | null
  failureMessage?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface ContentAnalysisSummary {
  oneSentence: string
  detailed: string
  topics: string[]
}

export interface ContentAnalysisKeyPoint {
  order: number
  title: string
  description: string
  evidenceChunkIds: string[]
  evidenceQuote: string
  startMs?: number | null
  endMs?: number | null
  sourceSegmentOrders: number[]
  timePrecision: TimePrecision
}

export interface ContentAnalysisChapter {
  order: number
  title: string
  summary: string
  startChunkId: string
  endChunkId: string
  startMs?: number | null
  endMs?: number | null
  timePrecision: TimePrecision
}

export interface ContentAnalysisSpeechIssue {
  order: number
  type: SpeechIssueType
  severity: SpeechIssueSeverity
  description: string
  evidenceChunkIds: string[]
  evidenceQuote: string
  suggestion: string
  startMs?: number | null
  endMs?: number | null
  sourceSegmentOrders: number[]
  timePrecision: TimePrecision
}

export interface ContentAnalysisResult {
  taskId: ResourceId
  transcriptId: ResourceId
  audioFileId: ResourceId
  audioFileName: string
  modelName: string
  promptVersion: string
  summary: ContentAnalysisSummary
  keyPoints: ContentAnalysisKeyPoint[]
  chapters: ContentAnalysisChapter[]
  speechIssues: ContentAnalysisSpeechIssue[]
  usage: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  }
  createdAt: string
}

export interface CreateContentAnalysisTaskInput {
  transcriptId: ResourceId
  analysisTypes: ContentAnalysisType[]
  summaryStyle: SummaryStyle
}
