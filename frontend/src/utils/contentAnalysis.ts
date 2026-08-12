import type {
  ContentAnalysisTaskStatus,
  SpeechIssueSeverity,
  SpeechIssueType,
} from '../types/contentAnalysis'

export const speechIssueTypeLabels: Record<SpeechIssueType, string> = {
  REPETITION: '重复表达',
  FILLER_WORD: '口头禅或填充词',
  INCOMPLETE_SENTENCE: '语句不完整',
  REDUNDANCY: '内容冗余',
  LOGIC_JUMP: '逻辑跳跃',
  AMBIGUOUS_EXPRESSION: '表达含糊',
  UNNECESSARY_DEVIATION: '偏离主题',
}

export const speechIssueSeverityLabels: Record<SpeechIssueSeverity, string> = {
  LOW: '轻微',
  MEDIUM: '中等',
  HIGH: '明显',
}

export function clampContentAnalysisProgress(value?: number | null) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, Math.round(value)))
}

export function getContentAnalysisProgressText(
  value?: number | null,
  status?: ContentAnalysisTaskStatus,
) {
  if (status === 'SUCCESS') return '分析完成'
  const progress = clampContentAnalysisProgress(value)
  if (progress >= 100) return '分析完成'
  if (progress >= 90) return '正在保存报告'
  if (progress >= 75) return '正在校验结果'
  if (progress >= 45) return '正在智能分析'
  if (progress >= 30) return '正在整理内容'
  if (progress >= 15) return '正在读取文字稿'
  return '等待分析'
}
