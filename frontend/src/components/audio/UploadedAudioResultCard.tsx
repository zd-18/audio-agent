import { CheckCircleFilled, FileSearchOutlined, UploadOutlined } from '@ant-design/icons'
import { Button, Descriptions } from 'antd'
import { Link } from 'react-router-dom'
import type { AudioFileRecord } from '../../types/api'
import { formatBytes, formatDuration } from '../../utils/formatters'
import CreateAnalysisTaskButton from '../analysis/CreateAnalysisTaskButton'
import CreateTranscriptionButton from '../transcription/CreateTranscriptionButton'

export default function UploadedAudioResultCard({ result, onContinue }: { result: AudioFileRecord; onContinue: () => void }) {
  const entries = [
    ['文件 ID', result.fileId],
    ['文件名称', result.originalName],
    ['文件大小', formatBytes(result.sizeBytes)],
    ['文件类型', result.mimeType || result.extension?.toUpperCase()],
    ['音频时长', formatDuration(result.durationMs)],
    ['文件状态', result.fileStatus],
    ['上传时间', result.createdAt ? new Date(result.createdAt).toLocaleString('zh-CN') : undefined],
    ['SHA-256', result.sha256],
  ].filter(([, value]) => value !== undefined && value !== null && value !== '')

  return (
    <section className="audio-upload-result" aria-labelledby="upload-result-title">
      <div className="audio-upload-result__heading"><CheckCircleFilled /><div><span>UPLOAD COMPLETE</span><h3 id="upload-result-title">音频上传成功</h3><p>后端已接收并完成文件元数据处理。</p></div></div>
      <Descriptions column={{ xs: 1, sm: 2 }} items={entries.map(([label, children]) => ({ key: String(label), label, children: String(children) }))} />
      <div className="audio-upload-result__actions">
        <CreateAnalysisTaskButton audioFileId={result.fileId} fileName={result.originalName} buttonType="default" />
        <CreateTranscriptionButton audioFileId={result.fileId} buttonType="primary" />
        <Link to={`/audio/files/${result.fileId}`}><Button icon={<FileSearchOutlined />}>查看文件信息</Button></Link>
        <Button type="text" icon={<UploadOutlined />} onClick={onContinue}>继续上传</Button>
      </div>
    </section>
  )
}
