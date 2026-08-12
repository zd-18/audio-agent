import { CheckCircleOutlined, CloudUploadOutlined, FileProtectOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { Alert, Button } from 'antd'
import { useRef } from 'react'
import AudioUploadDropzone from '../../components/audio/AudioUploadDropzone'
import SelectedAudioFileCard from '../../components/audio/SelectedAudioFileCard'
import UploadProgressCard from '../../components/audio/UploadProgressCard'
import UploadedAudioResultCard from '../../components/audio/UploadedAudioResultCard'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioUpload } from '../../hooks/useAudioUpload'

export default function AudioUploadPage() {
  const uploadAreaRef = useRef<HTMLDivElement>(null)
  const { file, status, progress, error, result, selectFile, upload, reset } = useAudioUpload()

  const returnToDropzone = () => {
    reset()
    window.requestAnimationFrame(() => uploadAreaRef.current?.querySelector('button')?.focus())
  }

  return (
    <PageContainer>
      <PageTitle eyebrow="AUDIO INGEST" title="上传音频" description="上传音频文件，完成元数据提取后即可创建分析或转写任务。" />

      <section className="audio-upload-guide" aria-label="使用说明">
        <div><FileProtectOutlined /><span><strong>格式与大小</strong><small>MP3 / WAV / M4A / MP4，最大 500MB</small></span></div>
        <div><CloudUploadOutlined /><span><strong>安全上传</strong><small>沿用后端统一上传与响应协议</small></span></div>
        <div><ThunderboltOutlined /><span><strong>继续处理</strong><small>上传成功后创建分析或转写任务</small></span></div>
      </section>

      <div className="audio-upload-workspace" ref={uploadAreaRef}>
        {!result && !file && <AudioUploadDropzone disabled={status === 'uploading'} onSelect={selectFile} />}

        {error && status === 'error' && !file && <Alert type="error" showIcon message={error} description="请重新选择符合要求的文件。" />}

        {file && !result && (
          <>
            <SelectedAudioFileCard file={file} disabled={status === 'uploading'} onRemove={reset} onReselect={returnToDropzone} />
            {(status === 'uploading' || (status === 'error' && error)) && <UploadProgressCard progress={progress} error={error} />}
            <div className="audio-upload-submit">
              <div><CheckCircleOutlined /><span>文件将在提交后上传，当前不支持断点续传。</span></div>
              <Button type="primary" size="large" icon={<CloudUploadOutlined />} loading={status === 'uploading'} disabled={status === 'uploading'} onClick={upload}>
                {status === 'error' ? '重新上传' : '开始上传'}
              </Button>
            </div>
          </>
        )}

        {result && <UploadedAudioResultCard result={result} onContinue={returnToDropzone} />}
      </div>

      <section className="audio-upload-flow">
        <div className="workbench-section__heading"><div><span>UPLOAD FLOW</span><h3>上传与分析流程</h3></div></div>
        <ol>
          {['选择本地音频', '上传并校验文件', '提取音频元数据', '创建后续处理任务'].map((label, index) => <li key={label}><b>{index + 1}</b><span>{label}</span></li>)}
        </ol>
      </section>
    </PageContainer>
  )
}
