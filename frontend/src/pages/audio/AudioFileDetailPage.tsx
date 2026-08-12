import { ArrowLeftOutlined, CloudUploadOutlined, DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Descriptions, Skeleton } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { downloadAudioFile } from '../../api/audioFiles'
import { isValidResourceId } from '../../api/http'
import CreateAnalysisTaskButton from '../../components/analysis/CreateAnalysisTaskButton'
import CreateTranscriptionButton from '../../components/transcription/CreateTranscriptionButton'
import TranscriptionStatusBadge from '../../components/transcription/TranscriptionStatusBadge'
import AudioFileStatusBadge from '../../components/workbench/AudioFileStatusBadge'
import CopyableValue from '../../components/workbench/CopyableValue'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioFileDetail } from '../../hooks/useAudioFileDetail'
import { useTranscriptionTaskList } from '../../hooks/useTranscriptionTaskList'
import { formatBytes, formatDateTime, formatDuration } from '../../utils/formatters'

export default function AudioFileDetailPage() {
  const { audioFileId } = useParams()
  const validId = isValidResourceId(audioFileId) ? audioFileId : undefined
  const { data, loading, error, refresh } = useAudioFileDetail(validId)
  const transcriptionQuery = useMemo(() => ({ current: 1, size: 1, audioFileId: validId }), [validId])
  const { data: transcriptionTasks } = useTranscriptionTaskList(transcriptionQuery)
  const latestTranscription = transcriptionTasks.records[0]
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const downloadControllerRef = useRef<AbortController | null>(null)
  const downloadInFlightRef = useRef(false)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      downloadControllerRef.current?.abort()
    }
  }, [])

  const download = async () => {
    if (!data || downloadInFlightRef.current) return
    downloadInFlightRef.current = true
    const controller = new AbortController()
    downloadControllerRef.current = controller
    setDownloading(true)
    setDownloadError(null)
    try {
      await downloadAudioFile(data.fileId, data.originalName || `audio-${data.fileId}`, controller.signal)
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        if (mountedRef.current) setDownloadError(requestError instanceof Error ? requestError.message : '文件下载失败')
      }
    } finally {
      if (downloadControllerRef.current === controller) downloadControllerRef.current = null
      downloadInFlightRef.current = false
      if (mountedRef.current) setDownloading(false)
    }
  }

  if (!validId) {
    return <PageContainer><PageTitle eyebrow="INVALID RESOURCE" title="文件 ID 无效" description="URL 中的 audioFileId 为空或不是有效的正整数。" /><Alert type="error" showIcon message="无法查询文件" description="请返回音频文件页并输入上传接口返回的真实 audioFileId。" action={<Link to="/audio/files"><Button>返回查询</Button></Link>} /></PageContainer>
  }

  return (
    <PageContainer>
      <PageTitle
        eyebrow="AUDIO FILE DETAIL"
        title={data?.originalName || '音频文件详情'}
        description={`audioFileId: ${validId}`}
        actions={data ? <><CreateAnalysisTaskButton audioFileId={data.fileId} fileName={data.originalName} /><CreateTranscriptionButton audioFileId={data.fileId} taskId={latestTranscription?.taskId} status={latestTranscription?.status} /><Button icon={<DownloadOutlined />} loading={downloading} onClick={download}>下载文件</Button></> : <Button icon={<ReloadOutlined />} onClick={refresh}>重新查询</Button>}
      />

      {loading && !data && <section className="workbench-panel"><Skeleton active paragraph={{ rows: 8 }} /></section>}
      {error && <Alert className="resource-detail-alert" type="error" showIcon message="文件查询失败" description={error} action={<Button onClick={refresh}>重试</Button>} />}
      {downloadError && <Alert className="resource-detail-alert" type="error" showIcon message="下载失败" description={downloadError} closable onClose={() => setDownloadError(null)} />}

      {data && (
        <>
          <section className="workbench-panel resource-detail-card">
            <div className="workbench-panel__heading"><div><span>FILE INFORMATION</span><h3>文件信息</h3></div><AudioFileStatusBadge status={data.fileStatus} /></div>
            <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} items={[
              { key: 'id', label: 'audioFileId', children: <CopyableValue value={data.fileId} mono /> },
              { key: 'name', label: '原始文件名', children: data.originalName || '—' },
              { key: 'role', label: '文件角色', children: data.fileRole || '—' },
              { key: 'size', label: '文件大小', children: formatBytes(data.sizeBytes) },
              { key: 'type', label: 'MIME 类型', children: data.mimeType || '—' },
              { key: 'extension', label: '扩展名', children: data.extension?.toUpperCase() || '—' },
              { key: 'duration', label: '音频时长', children: formatDuration(data.durationMs) },
              { key: 'status', label: '文件状态', children: <AudioFileStatusBadge status={data.fileStatus} /> },
              { key: 'transcription', label: '转写状态', children: <TranscriptionStatusBadge status={latestTranscription?.status} /> },
              { key: 'created', label: '上传时间', children: formatDateTime(data.createdAt) },
              { key: 'sha', label: 'SHA-256', span: 3, children: <CopyableValue value={data.sha256} mono /> },
            ]} />
          </section>
          <div className="resource-detail-actions">
            <Link to="/audio/upload"><Button icon={<ArrowLeftOutlined />}>返回上传页面</Button></Link>
            <Link to="/audio/upload"><Button type="text" icon={<CloudUploadOutlined />}>继续上传</Button></Link>
          </div>
        </>
      )}
    </PageContainer>
  )
}
