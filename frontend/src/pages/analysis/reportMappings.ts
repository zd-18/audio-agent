import type {
  RecommendationPriority,
  ReportIssueType,
  ReportSeverity,
} from '../../types/analysisReport'

export const ISSUE_TYPE_META: Record<ReportIssueType, { label: string; shortLabel: string }> = {
  SILENCE: { label: '长静音', shortLabel: '静音' },
  VOLUME_DROP: { label: '音量偏低', shortLabel: '偏低' },
  VOLUME_SPIKE: { label: '音量突升', shortLabel: '突升' },
  NOISE_RISK: { label: '疑似背景噪声', shortLabel: '噪声' },
}

export const SEVERITY_META: Record<ReportSeverity, { label: string; rank: number }> = {
  LOW: { label: '轻微', rank: 1 },
  MEDIUM: { label: '中等', rank: 2 },
  HIGH: { label: '较高', rank: 3 },
}

export const PRIORITY_META: Record<RecommendationPriority, { label: string }> = {
  HIGH: { label: '优先处理' },
  MEDIUM: { label: '建议处理' },
  LOW: { label: '可优化' },
}

export function getIssueTypeLabel(type: ReportIssueType | null | undefined) {
  return type ? ISSUE_TYPE_META[type].label : '未分类问题'
}

export function getSeverityLabel(severity: ReportSeverity | null | undefined) {
  return severity ? SEVERITY_META[severity].label : '未标注'
}

export function getIssueClassName(type: ReportIssueType | null | undefined) {
  return type ? type.toLowerCase().replace(/_/g, '-') : 'unknown'
}
