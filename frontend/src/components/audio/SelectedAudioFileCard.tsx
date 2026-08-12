import { AudioOutlined, DeleteOutlined, SwapOutlined } from '@ant-design/icons'
import { Button } from 'antd'

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}

export default function SelectedAudioFileCard({ file, disabled, onRemove, onReselect }: { file: File; disabled?: boolean; onRemove: () => void; onReselect: () => void }) {
  const extension = file.name.split('.').pop()?.toUpperCase() || 'AUDIO'
  return (
    <section className="audio-upload-file-card" aria-labelledby="selected-audio-title">
      <span className="audio-upload-file-card__icon"><AudioOutlined /></span>
      <div className="audio-upload-file-card__body">
        <span id="selected-audio-title">已选择文件</span>
        <strong title={file.name}>{file.name}</strong>
        <div><span>{formatBytes(file.size)}</span><span>{extension}</span><span>{file.type || '未知 MIME 类型'}</span><span className="audio-upload-file-card__ready">等待上传</span></div>
      </div>
      <div className="audio-upload-file-card__actions">
        <Button icon={<SwapOutlined />} onClick={onReselect} disabled={disabled}>重新选择</Button>
        <Button type="text" danger icon={<DeleteOutlined />} onClick={onRemove} disabled={disabled} aria-label="移除已选择文件" />
      </div>
    </section>
  )
}

