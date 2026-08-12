import type { AnalysisTaskStatus } from '../../types/api'

const labels: Record<AnalysisTaskStatus, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  SUCCESS: '已成功',
  FAILED: '失败',
}

interface TaskStatusBadgeProps {
  status: AnalysisTaskStatus
  label?: string
}

export default function TaskStatusBadge({ status, label }: TaskStatusBadgeProps) {
  return <span className={`task-status-badge task-status-badge--${status.toLowerCase()}`}><i aria-hidden="true" />{label || labels[status]}</span>
}
