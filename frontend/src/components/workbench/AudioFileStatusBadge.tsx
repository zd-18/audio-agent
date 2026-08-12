const labels: Record<string, string> = {
  UPLOADING: '上传中',
  AVAILABLE: '可用',
  PROCESSING: '处理中',
  FAILED: '失败',
  DELETED: '已删除',
}

const tones: Record<string, string> = {
  UPLOADING: 'processing',
  AVAILABLE: 'success',
  PROCESSING: 'processing',
  FAILED: 'failed',
  DELETED: 'pending',
}

export default function AudioFileStatusBadge({ status }: { status?: string }) {
  const normalized = status || 'UNKNOWN'
  const tone = tones[normalized] || 'pending'
  return <span className={`task-status-badge task-status-badge--${tone}`}><i aria-hidden="true" />{labels[normalized] || normalized}</span>
}
