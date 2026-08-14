import {
  LoadingOutlined,
  PauseCircleOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Progress } from 'antd'
import type { UploadStatus } from '../../hooks/useAudioUpload'

interface UploadProgressCardProps {
  status: UploadStatus
  progress: number
  hashProgress: number
  uploadedCount: number
  totalChunks: number
  error?: string | null
}

function progressContent(status: UploadStatus, uploadedCount: number, totalChunks: number) {
  if (status === 'hashing') {
    return { icon: <SafetyCertificateOutlined />, title: '正在校验文件', detail: '正在计算文件摘要，大文件会分段读取，不会一次占满内存。' }
  }
  if (status === 'paused') {
    return { icon: <PauseCircleOutlined />, title: '上传已暂停', detail: `已完成 ${uploadedCount} / ${totalChunks} 片，继续后只上传剩余部分。` }
  }
  if (status === 'merging') {
    return { icon: <LoadingOutlined spin />, title: '正在完成上传', detail: '正在校验文件完整性，请稍候，不要重复提交。' }
  }
  return { icon: <LoadingOutlined spin />, title: '正在上传音频', detail: `已完成 ${uploadedCount} / ${totalChunks} 片，可随时暂停后继续。` }
}

export default function UploadProgressCard({
  status,
  progress,
  hashProgress,
  uploadedCount,
  totalChunks,
  error,
}: UploadProgressCardProps) {
  const content = progressContent(status, uploadedCount, totalChunks)
  const shownProgress = status === 'hashing' ? hashProgress : progress
  return (
    <section className={`audio-upload-progress${error ? ' audio-upload-progress--error' : ''}`} role={error ? 'alert' : 'status'} aria-live="polite">
      <div>
        <span className="audio-upload-progress__icon">{error ? <WarningOutlined /> : content.icon}</span>
        <div><strong>{error ? '上传未完成' : content.title}</strong><span>{error || content.detail}</span></div>
        <b>{shownProgress}%</b>
      </div>
      <Progress percent={shownProgress} status={error ? 'exception' : status === 'paused' ? 'normal' : 'active'} showInfo={false} />
    </section>
  )
}
