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
  resumed?: boolean
}

function progressContent(status: UploadStatus, uploadedCount: number, totalChunks: number, resumed: boolean) {
  if (status === 'hashing') {
    return { icon: <SafetyCertificateOutlined />, title: '正在校验文件', detail: '正在计算文件摘要，大文件会分段读取，不会一次占满内存。' }
  }
  if (status === 'paused') {
    return { icon: <PauseCircleOutlined />, title: '上传已暂停', detail: `已完成 ${uploadedCount} / ${totalChunks} 片，继续后只上传剩余部分。` }
  }
  if (status === 'uploading' && resumed) {
    return { icon: <LoadingOutlined spin />, title: '检测到未完成上传，正在从断点继续', detail: `已恢复 ${uploadedCount} / ${totalChunks} 片，只上传剩余部分。` }
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
  resumed = false,
}: UploadProgressCardProps) {
  if (status === 'merging') {
    return (
      <section className="audio-upload-finalizing" role="status" aria-live="polite">
        <span className="audio-upload-finalizing__icon"><LoadingOutlined spin /></span>
        <div>
          <strong>文件已上传，正在校验并保存…</strong>
          <span>完成后将自动显示上传结果。</span>
        </div>
      </section>
    )
  }

  const content = progressContent(status, uploadedCount, totalChunks, resumed)
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
