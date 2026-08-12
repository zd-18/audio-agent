import { LoadingOutlined, WarningOutlined } from '@ant-design/icons'
import { Progress } from 'antd'

interface UploadProgressCardProps {
  progress: number
  error?: string | null
}

export default function UploadProgressCard({ progress, error }: UploadProgressCardProps) {
  return (
    <section className={`audio-upload-progress${error ? ' audio-upload-progress--error' : ''}`} role={error ? 'alert' : 'status'} aria-live="polite">
      <div>
        <span className="audio-upload-progress__icon">{error ? <WarningOutlined /> : <LoadingOutlined spin />}</span>
        <div><strong>{error ? '上传失败' : '正在上传音频'}</strong><span>{error || '请保持页面开启，上传完成后会自动展示文件信息。'}</span></div>
        {!error && <b>{progress}%</b>}
      </div>
      <Progress percent={error ? 100 : progress} status={error ? 'exception' : 'active'} showInfo={false} />
    </section>
  )
}

