import { CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, SyncOutlined } from '@ant-design/icons'
import { Tag } from 'antd'
import type { TranscriptionTaskStatus } from '../../types/transcription'

const META: Record<TranscriptionTaskStatus, { label: string; icon: React.ReactNode; tone: string }> = {
  PENDING: { label: '等待处理', icon: <ClockCircleOutlined />, tone: 'pending' },
  RUNNING: { label: '正在转写', icon: <SyncOutlined spin />, tone: 'running' },
  SUCCESS: { label: '转写完成', icon: <CheckCircleOutlined />, tone: 'success' },
  FAILED: { label: '转写失败', icon: <CloseCircleOutlined />, tone: 'failed' },
}

export default function TranscriptionStatusBadge({ status }: { status?: string | null }) {
  const meta = META[status as TranscriptionTaskStatus]
  if (!meta) return <Tag className="transcription-status is-empty">未生成</Tag>
  return <Tag icon={meta.icon} className={`transcription-status is-${meta.tone}`}>{meta.label}</Tag>
}
