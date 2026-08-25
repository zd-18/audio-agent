import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  DownloadOutlined,
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  InboxOutlined,
  MessageOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { Alert, App as AntdApp, Button, Descriptions, Input, Modal, Skeleton, Space, Typography } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { archiveAudioFile, downloadAudioFile, moveAudioFileToRecycleBin, renameAudioFile, restoreArchivedAudioFile } from '../../api/audioFiles'
import { isValidResourceId } from '../../api/http'
import CreateAnalysisTaskButton from '../../components/analysis/CreateAnalysisTaskButton'
import ReportAudioPlayer from '../../components/audio/ReportAudioPlayer'
import CreateTranscriptionButton from '../../components/transcription/CreateTranscriptionButton'
import TranscriptionStatusBadge from '../../components/transcription/TranscriptionStatusBadge'
import AudioFileStatusBadge from '../../components/workbench/AudioFileStatusBadge'
import CopyableValue from '../../components/workbench/CopyableValue'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioFileDetail } from '../../hooks/useAudioFileDetail'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import { useTranscriptionTaskList } from '../../hooks/useTranscriptionTaskList'
import { formatBytes, formatDateTime, formatDuration } from '../../utils/formatters'
import '../analysis/analysis-report.css'
import './audio-file-detail.css'

const CAPABILITY_COPY = {
  diagnosis: '自动检测音量、噪声、长静音等音频质量问题，并给出处理建议。',
  chat: '围绕音频内容提问，支持原文引用、时间定位和点击播放。',
  processing: '用自然语言描述你希望如何修改音频，并生成可确认的处理方案。',
}

function compactResourceId(value: string) {
  if (value.length <= 12) return value
  return `${value.slice(0, 8)}…${value.slice(-4)}`
}

export default function AudioFileDetailPage() {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const { audioFileId } = useParams()
  const validId = isValidResourceId(audioFileId) ? audioFileId : undefined
  const { data, loading, error, refresh } = useAudioFileDetail(validId)
  const player = useAudioPlayback(data?.fileId, data?.durationMs)
  const transcriptionQuery = useMemo(() => ({ current: 1, size: 1, audioFileId: validId }), [validId])
  const { data: transcriptionTasks } = useTranscriptionTaskList(transcriptionQuery)
  const latestTranscription = transcriptionTasks.records[0]
  const hasTranscript = latestTranscription?.status === 'SUCCESS'
  const [downloading, setDownloading] = useState(false)
  const [renameOpen, setRenameOpen] = useState(false)
  const [renameValue, setRenameValue] = useState('')
  const [mutating, setMutating] = useState(false)
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

  const openRename = () => {
    if (!data) return
    setRenameValue(data.originalName || '')
    setRenameOpen(true)
  }

  const saveRename = async () => {
    if (!data || mutating || !renameValue.trim()) return
    setMutating(true)
    try {
      await renameAudioFile(data.fileId, renameValue.trim())
      setRenameOpen(false)
      refresh()
      void message.success('文件名已更新')
    } catch (requestError) {
      void message.error(requestError instanceof Error ? requestError.message : '文件重命名失败')
    } finally {
      setMutating(false)
    }
  }

  const toggleArchive = () => {
    if (!data || mutating) return
    const archived = data.fileStatus === 'ARCHIVED'
    modal.confirm({
      title: archived ? '恢复归档文件？' : '归档这个文件？',
      content: archived
        ? '恢复后可以继续播放、分析和处理。'
        : '归档后文件仍会保留，但暂停播放、分析和处理能力。',
      okText: archived ? '恢复文件' : '确认归档',
      cancelText: '取消',
      onOk: async () => {
        setMutating(true)
        try {
          if (archived) await restoreArchivedAudioFile(data.fileId)
          else await archiveAudioFile(data.fileId)
          refresh()
          void message.success(archived ? '文件已恢复' : '文件已归档')
        } catch (requestError) {
          void message.error(requestError instanceof Error ? requestError.message : '文件状态更新失败')
          throw requestError
        } finally {
          setMutating(false)
        }
      },
    })
  }

  const moveToTrash = () => {
    if (!data || mutating) return
    modal.confirm({
      title: '移入回收站？',
      content: '文件会暂停播放、分析和处理，之后可从音频文件页的回收站恢复。',
      okText: '移入回收站',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setMutating(true)
        try {
          await moveAudioFileToRecycleBin(data.fileId)
          void message.success('文件已移入回收站')
          navigate('/audio/files?scope=trash', { replace: true })
        } catch (requestError) {
          void message.error(requestError instanceof Error ? requestError.message : '文件删除失败')
          throw requestError
        } finally {
          setMutating(false)
        }
      },
    })
  }

  if (!validId) {
    return (
      <PageContainer>
        <PageTitle eyebrow="INVALID RESOURCE" title="文件 ID 无效" />
        <Alert type="error" showIcon message="无法查询文件" description="请返回音频文件页并输入上传接口返回的真实 audioFileId。" action={<Link to="/audio/files"><Button>返回查询</Button></Link>} />
      </PageContainer>
    )
  }

  const formatLabel = [data?.mimeType, data?.extension?.toUpperCase()].filter(Boolean).join(' · ') || '—'

  return (
    <PageContainer>
      <div className="audio-file-detail-page">
        <PageTitle
          eyebrow="AUDIO FILE DETAIL"
          title="音频文件详情"
          actions={<Space wrap><Button icon={<EditOutlined />} disabled={!data || data.fileStatus === 'PROCESSING'} onClick={openRename}>重命名</Button><Button icon={<InboxOutlined />} loading={mutating} disabled={!data || ['PROCESSING', 'UPLOADING'].includes(data.fileStatus || '')} onClick={toggleArchive}>{data?.fileStatus === 'ARCHIVED' ? '恢复归档' : '归档'}</Button><Button danger icon={<DeleteOutlined />} disabled={!data || ['PROCESSING', 'UPLOADING'].includes(data.fileStatus || '') || mutating} onClick={moveToTrash}>移入回收站</Button><Link to="/audio/files"><Button icon={<ArrowLeftOutlined />}>返回文件列表</Button></Link></Space>}
        />

        {loading && !data && <section className="workbench-panel"><Skeleton active paragraph={{ rows: 8 }} /></section>}
        {error && <Alert className="resource-detail-alert" type="error" showIcon message="文件查询失败" description={error} action={<Button onClick={refresh}>重试</Button>} />}
        {downloadError && <Alert className="resource-detail-alert" type="error" showIcon message="下载失败" description={downloadError} closable onClose={() => setDownloadError(null)} />}

        {data && (
          <div className="audio-file-detail__content">
            <section className="audio-file-detail__identity" aria-labelledby="audio-file-name">
              <div>
                <span className="audio-file-detail__eyebrow">CURRENT AUDIO</span>
                <h2 id="audio-file-name" title={data.originalName}>{data.originalName || '未命名音频'}</h2>
                <div className="audio-file-detail__identity-meta">
                  <AudioFileStatusBadge status={data.fileStatus} />
                  <Typography.Text
                    className="audio-file-detail__compact-id"
                    copyable={{ text: data.fileId, tooltips: ['复制文件编号', '已复制'] }}
                    title={data.fileId}
                  >
                    文件编号：{compactResourceId(data.fileId)}
                  </Typography.Text>
                </div>
              </div>
            </section>

            <ReportAudioPlayer
              player={player}
              fallbackFileName={data.originalName}
              downloading={downloading}
              onDownload={() => { void download() }}
              sectionId="audio-file-detail-player"
              eyebrow="AUDIO PREVIEW"
            />

            <section className="workbench-panel audio-file-detail__capabilities" aria-labelledby="audio-file-next-action">
              <div className="audio-file-detail__section-heading">
                <div>
                  <span>AI CAPABILITIES</span>
                  <h2 id="audio-file-next-action">接下来你想做什么？</h2>
                </div>
                <p>选择一项能力继续，原始音频不会被直接修改。</p>
              </div>

              <div className="audio-file-detail__capability-grid">
                <article className="audio-file-detail__capability-card audio-file-detail__capability-card--diagnosis">
                  <span className="audio-file-detail__capability-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
                  <div>
                    <h3>智能诊断</h3>
                    <p>{CAPABILITY_COPY.diagnosis}</p>
                  </div>
                  <div className="audio-file-detail__capability-action">
                    <CreateAnalysisTaskButton audioFileId={data.fileId} fileName={data.originalName} block buttonType="default" label="智能诊断" />
                  </div>
                </article>

                <article className="audio-file-detail__capability-card audio-file-detail__capability-card--chat">
                  <span className="audio-file-detail__capability-icon" aria-hidden="true"><MessageOutlined /></span>
                  <div>
                    <h3>智能问答</h3>
                    <p>{CAPABILITY_COPY.chat}</p>
                  </div>
                  <div className="audio-file-detail__capability-action">
                    <CreateTranscriptionButton
                      audioFileId={data.fileId}
                      taskId={latestTranscription?.taskId}
                      status={latestTranscription?.status}
                      block
                      buttonType="default"
                      label="智能问答"
                    />
                  </div>
                </article>

                <article className="audio-file-detail__capability-card audio-file-detail__capability-card--processing">
                  <span className="audio-file-detail__capability-icon" aria-hidden="true"><ToolOutlined /></span>
                  <div>
                    <h3>智能处理</h3>
                    <p>{CAPABILITY_COPY.processing}</p>
                  </div>
                  <div className="audio-file-detail__capability-action">
                    <Link to={`/audio/files/${encodeURIComponent(data.fileId)}/agent`}>
                      <Button block icon={<ToolOutlined />}>智能处理</Button>
                    </Link>
                  </div>
                </article>
              </div>
            </section>

            <section className="workbench-panel resource-detail-card audio-file-detail__information" aria-labelledby="audio-file-information">
              <div className="workbench-panel__heading">
                <div><span>FILE INFORMATION</span><h3 id="audio-file-information">文件信息</h3></div>
              </div>
              <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} items={[
                { key: 'name', label: '原始文件名', children: data.originalName || '—' },
                { key: 'size', label: '文件大小', children: formatBytes(data.sizeBytes) },
                { key: 'duration', label: '音频时长', children: formatDuration(data.durationMs) },
                { key: 'format', label: 'MIME / 格式', children: formatLabel },
                { key: 'created', label: '上传时间', children: formatDateTime(data.createdAt) },
                { key: 'status', label: '文件状态', children: <AudioFileStatusBadge status={data.fileStatus} /> },
                { key: 'transcription', label: '转写状态', children: <TranscriptionStatusBadge status={latestTranscription?.status} /> },
              ]} />

              <details className="audio-file-detail__technical">
                <summary>技术信息</summary>
                <Descriptions column={1} size="small" items={[
                  { key: 'id', label: '完整文件编号', children: <CopyableValue value={data.fileId} mono /> },
                  { key: 'sha', label: 'SHA-256', children: <CopyableValue value={data.sha256} mono /> },
                  { key: 'role', label: '文件角色', children: data.fileRole || '—' },
                ]} />
              </details>
            </section>

            <section className="workbench-panel audio-file-detail__other-actions" aria-labelledby="audio-file-other-actions">
              <div>
                <span>OTHER ACTIONS</span>
                <h3 id="audio-file-other-actions">其他操作</h3>
              </div>
              <div className="audio-file-detail__other-action-buttons">
                {hasTranscript && latestTranscription?.taskId && (
                  <Link to={`/transcriptions/${encodeURIComponent(latestTranscription.taskId)}`}>
                    <Button icon={<FileTextOutlined />}>查看文字稿</Button>
                  </Link>
                )}
                <Button icon={<DownloadOutlined />} loading={downloading} onClick={() => { void download() }}>下载文件</Button>
                <Link to="/audio/upload"><Button type="text" icon={<CloudUploadOutlined />}>继续上传</Button></Link>
              </div>
            </section>
          </div>
        )}

        {!data && !loading && !error && (
          <Button icon={<ReloadOutlined />} onClick={refresh}>重新查询</Button>
        )}

        <Modal
          title="重命名文件"
          open={renameOpen}
          okText="保存"
          cancelText="取消"
          confirmLoading={mutating}
          okButtonProps={{ disabled: !renameValue.trim() }}
          onOk={() => { void saveRename() }}
          onCancel={() => { if (!mutating) setRenameOpen(false) }}
          destroyOnHidden
        >
          <Input value={renameValue} maxLength={255} showCount autoFocus onPressEnter={() => { void saveRename() }} onChange={(event) => setRenameValue(event.target.value)} />
          <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>保留原文件扩展名，只修改便于识别的文件名称。</Typography.Paragraph>
        </Modal>
      </div>
    </PageContainer>
  )
}
