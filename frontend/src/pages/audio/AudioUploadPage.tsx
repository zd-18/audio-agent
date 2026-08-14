import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  FileProtectOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Alert, Button } from 'antd'
import { useRef } from 'react'
import AudioUploadDropzone from '../../components/audio/AudioUploadDropzone'
import SelectedAudioFileCard from '../../components/audio/SelectedAudioFileCard'
import UploadProgressCard from '../../components/audio/UploadProgressCard'
import UploadedAudioResultCard from '../../components/audio/UploadedAudioResultCard'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioUpload } from '../../hooks/useAudioUpload'

const STATUS_TEXT = {
  ready: '等待上传',
  hashing: '正在校验',
  uploading: '正在上传',
  paused: '已暂停',
  merging: '正在完成',
  error: '等待重试',
} as const

export default function AudioUploadPage() {
  const uploadAreaRef = useRef<HTMLDivElement>(null)
  const {
    file,
    status,
    progress,
    hashProgress,
    uploadedCount,
    totalChunks,
    error,
    result,
    instantUpload,
    selectFile,
    upload,
    pause,
    reset,
  } = useAudioUpload()

  const returnToDropzone = () => {
    reset()
    window.requestAnimationFrame(() => uploadAreaRef.current?.querySelector('button')?.focus())
  }
  const working = status === 'hashing' || status === 'uploading' || status === 'merging'
  const showProgress = ['hashing', 'uploading', 'paused', 'merging', 'error'].includes(status)

  return (
    <PageContainer>
      <PageTitle eyebrow="AUDIO INGEST" title="上传音频" description="大文件会自动分段上传；中断后重新选择同一文件，即可从已完成位置继续。" />

      <section className="audio-upload-guide" aria-label="使用说明">
        <div><FileProtectOutlined /><span><strong>格式与大小</strong><small>MP3 / WAV / M4A / MP4，最大 20GB</small></span></div>
        <div><CloudUploadOutlined /><span><strong>稳定续传</strong><small>支持暂停、继续与失败重试</small></span></div>
        <div><ThunderboltOutlined /><span><strong>避免重复</strong><small>相同文件可直接完成上传</small></span></div>
      </section>

      <div className="audio-upload-workspace" ref={uploadAreaRef}>
        {!result && !file && <AudioUploadDropzone disabled={working} onSelect={selectFile} />}

        {error && status === 'error' && !file && <Alert type="error" showIcon message={error} description="请重新选择符合要求的文件。" />}

        {file && !result && (
          <>
            <SelectedAudioFileCard
              file={file}
              disabled={working}
              statusText={STATUS_TEXT[status as keyof typeof STATUS_TEXT] || '等待上传'}
              onRemove={reset}
              onReselect={returnToDropzone}
            />
            {showProgress && (
              <UploadProgressCard
                status={status}
                progress={progress}
                hashProgress={hashProgress}
                uploadedCount={uploadedCount}
                totalChunks={totalChunks}
                error={error}
              />
            )}
            <div className="audio-upload-submit">
              <div><CheckCircleOutlined /><span>页面刷新后重新选择同一文件，会自动检查并只上传缺失部分。</span></div>
              <div className="audio-upload-submit__actions">
                {status === 'uploading' && (
                  <Button size="large" icon={<PauseCircleOutlined />} onClick={pause}>暂停</Button>
                )}
                {status === 'merging' && (
                  <Button type="primary" size="large" loading disabled>正在完成</Button>
                )}
                {status === 'paused' && (
                  <Button type="primary" size="large" icon={<PlayCircleOutlined />} onClick={upload}>继续上传</Button>
                )}
                {status === 'error' && (
                  <Button type="primary" size="large" icon={<ReloadOutlined />} onClick={upload}>重试</Button>
                )}
                {status === 'ready' && (
                  <Button type="primary" size="large" icon={<CloudUploadOutlined />} onClick={upload}>开始上传</Button>
                )}
              </div>
            </div>
          </>
        )}

        {result && <UploadedAudioResultCard result={result} instantUpload={instantUpload} onContinue={returnToDropzone} />}
      </div>

      <section className="audio-upload-flow">
        <div className="workbench-section__heading"><div><span>UPLOAD FLOW</span><h3>可靠上传流程</h3></div></div>
        <ol>
          {['选择本地音频', '校验文件完整性', '上传并自动续传', '完成服务端复核'].map((label, index) => <li key={label}><b>{index + 1}</b><span>{label}</span></li>)}
        </ol>
      </section>
    </PageContainer>
  )
}
