import type { AnalysisTaskStatus } from '../types/api'

export const dashboardMetrics = [
  { label: '已上传音频', value: 24, hint: '演示累计数据', tone: 'purple' as const },
  { label: '待处理任务', value: 3, hint: '演示队列状态', tone: 'blue' as const },
  { label: '分析成功', value: 18, hint: '演示累计数据', tone: 'cyan' as const },
  { label: '处理失败', value: 2, hint: '演示累计数据', tone: 'red' as const },
]

export const recentAudioMock = [
  { key: '1', name: 'podcast-episode-18.mp3', format: 'MP3', size: '42.8 MB', duration: '38:24', createdAt: '今天 14:32' },
  { key: '2', name: 'product-interview.wav', format: 'WAV', size: '86.1 MB', duration: '26:10', createdAt: '今天 11:08' },
  { key: '3', name: 'weekly-sync.m4a', format: 'M4A', size: '19.6 MB', duration: '51:03', createdAt: '昨天 18:46' },
]

export const recentTasksMock: Array<{ key: string; id: string; file: string; status: AnalysisTaskStatus; progress: number; createdAt: string }> = [
  { key: '1', id: 'TASK-1048', file: 'podcast-episode-18.mp3', status: 'PROCESSING', progress: 68, createdAt: '14:35' },
  { key: '2', id: 'TASK-1047', file: 'product-interview.wav', status: 'SUCCESS', progress: 100, createdAt: '11:12' },
  { key: '3', id: 'TASK-1046', file: 'weekly-sync.m4a', status: 'FAILED', progress: 36, createdAt: '昨天 18:49' },
  { key: '4', id: 'TASK-1045', file: 'customer-call.mp3', status: 'PENDING', progress: 0, createdAt: '昨天 16:20' },
]

export const taskStatusOverview = [
  { status: 'PENDING' as const, label: '待处理', value: 3 },
  { status: 'PROCESSING' as const, label: '处理中', value: 1 },
  { status: 'SUCCESS' as const, label: '已成功', value: 18 },
  { status: 'FAILED' as const, label: '失败', value: 2 },
]

