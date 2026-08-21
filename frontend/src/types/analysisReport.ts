import type { ResourceId } from './api'

export type QualityGrade = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR'
export type ReportIssueType = 'SILENCE' | 'VOLUME_DROP' | 'VOLUME_SPIKE' | 'NOISE_RISK'
export type ReportSeverity = 'LOW' | 'MEDIUM' | 'HIGH'
export type RecommendationPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface AudioOverview {
  fileName: string | null
  durationMs: number | null
  format: string | null
  codec: string | null
  sampleRate: number | null
  channels: number | null
}

export interface LoudnessOverview {
  integratedLoudnessLufs?: number
  loudnessRangeLu?: number
  truePeakDbfs?: number
  loudnessLevel?: 'LOW' | 'NORMAL' | 'HIGH'
  peakRisk?: 'RISK' | 'NORMAL'
  dynamicRangeLevel?: 'NARROW' | 'NORMAL' | 'WIDE'
}

export interface IssueSummary {
  totalIssueCount: number | null
  totalIssueDurationMs: number | null
  silenceCount: number | null
  volumeDropCount: number | null
  volumeSpikeCount: number | null
  noiseRiskCount: number | null
  silenceDurationMs: number | null
  volumeIssueDurationMs: number | null
  noiseRiskDurationMs: number | null
}

export interface ReportTimelineItem {
  issueId: ResourceId | null
  issueType: ReportIssueType | null
  title: string | null
  startMs: number | null
  endMs: number | null
  durationMs: number | null
  severity: ReportSeverity | null
  description: string | null
}

export type KeyIssue = ReportTimelineItem

export interface Recommendation {
  priority?: RecommendationPriority
  issueId?: ResourceId
  startMs?: number
  endMs?: number
  message?: string
  recommendedMethod?: string
  recommendedParameters?: string
}

export interface AudioAnalysisReport {
  reportId: ResourceId
  reportVersion: string
  taskId: ResourceId
  audioFileId: ResourceId
  qualityScore: number
  qualityGrade: QualityGrade
  qualityGradeText: string
  summary?: string
  audioOverview: AudioOverview
  loudnessOverview?: LoudnessOverview
  issueSummary: IssueSummary
  keyIssues: KeyIssue[]
  timeline: ReportTimelineItem[]
  recommendations: Recommendation[]
  generatedAt: string
}
