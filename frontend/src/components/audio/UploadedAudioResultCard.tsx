import { CheckCircleFilled, FileSearchOutlined, ToolOutlined, UploadOutlined } from '@ant-design/icons'
import { Button, Descriptions } from 'antd'
import { Link } from 'react-router-dom'
import type { AudioFileRecord } from '../../types/api'
import { formatBytes, formatDateTime, formatDuration } from '../../utils/formatters'
import CreateAnalysisTaskButton from '../analysis/CreateAnalysisTaskButton'
import CreateTranscriptionButton from '../transcription/CreateTranscriptionButton'

const MIME_FORMATS: Record<string, string> = {
  'audio/mpeg': 'MP3',
  'audio/wav': 'WAV',
  'audio/x-wav': 'WAV',
  'audio/mp4': 'M4A',
  'video/mp4': 'MP4',
}

function displayFormat(result: AudioFileRecord) {
  const fileNameExtension = result.originalName?.includes('.')
    ? result.originalName.split('.').pop()
    : undefined
  const extension = result.extension?.replace(/^\./, '') || fileNameExtension
  return extension?.toUpperCase() || MIME_FORMATS[result.mimeType || ''] || '—'
}

function displayStatus(status?: string) {
  if (status === 'AVAILABLE') return '可用'
  if (status === 'PROCESSING') return '处理中'
  if (status === 'FAILED') return '处理失败'
  return '已保存'
}

export default function UploadedAudioResultCard({ result, instantUpload = false, onContinue }: { result: AudioFileRecord; instantUpload?: boolean; onContinue: () => void }) {
  const entries = [
    ['文件名称', result.originalName],
    ['文件大小', formatBytes(result.sizeBytes)],
    ['文件格式', displayFormat(result)],
    ['音频时长', formatDuration(result.durationMs)],
    ['上传时间', formatDateTime(result.createdAt)],
    ['文件状态', displayStatus(result.fileStatus)],
  ].filter(([, value]) => value !== undefined && value !== null && value !== '')

  return (
    <section className="audio-upload-result" aria-labelledby="upload-result-title">
      <div className="audio-upload-result__heading"><CheckCircleFilled /><div><span>UPLOAD COMPLETE</span><h3 id="upload-result-title">{instantUpload ? '文件已存在，已完成上传' : '音频上传成功'}</h3><p>{instantUpload ? '已找到内容相同的文件，无需重复上传。' : '文件已完成完整性校验并安全保存。'}</p></div></div>
      <Descriptions column={{ xs: 1, sm: 2 }} items={entries.map(([label, children]) => ({ key: String(label), label, children: String(children) }))} />
      <div className="audio-upload-result__actions">
        <CreateAnalysisTaskButton audioFileId={result.fileId} fileName={result.originalName} buttonType="primary" />
        <Link to={`/audio/files/${encodeURIComponent(result.fileId)}/agent`}><Button icon={<ToolOutlined />}>智能处理</Button></Link>
        <CreateTranscriptionButton audioFileId={result.fileId} buttonType="default" label="生成文字稿" />
        <Link to={`/audio/files/${encodeURIComponent(result.fileId)}`}><Button icon={<FileSearchOutlined />}>查看文件</Button></Link>
        <Button className="audio-upload-result__continue" type="text" icon={<UploadOutlined />} onClick={onContinue}>继续上传</Button>
      </div>
    </section>
  )
}
