import { AudioOutlined, DeleteOutlined, SwapOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import type { ReactNode } from 'react'

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}

interface SelectedAudioFileCardProps {
  file: File
  disabled?: boolean
  statusText?: string
  primaryAction?: ReactNode
  onRemove: () => void
  onReselect: () => void
}

export default function SelectedAudioFileCard({
  file,
  disabled,
  statusText = '等待上传',
  primaryAction,
  onRemove,
  onReselect,
}: SelectedAudioFileCardProps) {
  const extension = file.name.split('.').pop()?.toUpperCase() || 'AUDIO'
  return (
    <section className="audio-upload-file-card" aria-labelledby="selected-audio-title">
      <span className="audio-upload-file-card__icon"><AudioOutlined /></span>
      <div className="audio-upload-file-card__body">
        <strong id="selected-audio-title" title={file.name}>{file.name}</strong>
        <div className="audio-upload-file-card__meta">
          <span>{formatBytes(file.size)}</span><i aria-hidden="true">·</i>
          <span>{extension}</span><i aria-hidden="true">·</i>
          <span className="audio-upload-file-card__ready">{statusText}</span>
        </div>
      </div>
      <div className="audio-upload-file-card__actions">
        <Button icon={<SwapOutlined />} onClick={onReselect} disabled={disabled}>重新选择</Button>
        <Button type="text" danger icon={<DeleteOutlined />} onClick={onRemove} disabled={disabled} aria-label="移除已选择文件" />
        {primaryAction}
      </div>
    </section>
  )
}
