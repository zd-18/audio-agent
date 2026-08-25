import {
  ArrowLeftOutlined,
  CopyOutlined,
  DownloadOutlined,
  EditOutlined,
  FileTextOutlined,
  LoadingOutlined,
  MessageOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Alert, App as AntdApp, Button, Dropdown, Input, Modal, Progress, Select, Skeleton, Space, Tabs, Tooltip } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { downloadAudioFile } from '../../api/audioFiles'
import { isValidResourceId } from '../../api/http'
import { downloadTranscript, updateTranscriptSegment } from '../../api/transcriptions'
import type { TranscriptExportFormat } from '../../api/transcriptions'
import ReportAudioPlayer from '../../components/audio/ReportAudioPlayer'
import ContentAnalysisSection from '../../components/content-analysis/ContentAnalysisSection'
import TranscriptParagraphList from '../../components/transcription/TranscriptParagraphList'
import TranscriptSentenceList from '../../components/transcription/TranscriptSentenceList'
import TranscriptViewSwitcher from '../../components/transcription/TranscriptViewSwitcher'
import type { TranscriptViewMode } from '../../components/transcription/TranscriptViewSwitcher'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import { useCreateTranscription } from '../../hooks/useCreateTranscription'
import { useTranscript } from '../../hooks/useTranscript'
import { useTranscriptionTaskPolling } from '../../hooks/useTranscriptionTaskPolling'
import type { TranscriptSegment } from '../../types/transcription'
import { formatDateTime, formatDuration } from '../../utils/formatters'
import {
  createTranscriptParagraphs,
  findParagraphBySegmentOrder,
  getTranscriptSegmentOrder,
} from '../../utils/transcriptParagraphs'
import type { TranscriptParagraph } from '../../utils/transcriptParagraphs'
import {
  clampTranscriptionProgress,
  getTranscriptionProgressText,
} from '../../utils/transcription'
import '../analysis/analysis-report.css'
import './transcription.css'

function languageLabel(value: string) {
  const normalized = value.trim().toLowerCase()
  if (normalized.startsWith('zh')) return '中文'
  if (normalized.startsWith('en')) return '英文'
  if (normalized.startsWith('ja')) return '日文'
  if (normalized.startsWith('ko')) return '韩文'
  return '其他语言'
}

function taskStatusLabel(status: string) {
  if (status === 'SUCCESS') return '已转写'
  if (status === 'RUNNING') return '转写中'
  if (status === 'FAILED') return '转写失败'
  return '等待转写'
}

export default function TranscriptionDetailPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const { taskId } = useParams()
  const validTaskId = isValidResourceId(taskId) ? taskId : undefined
  const {
    task: polledTask,
    loading,
    refreshing,
    error,
    timedOut,
    refresh,
  } = useTranscriptionTaskPolling(validTaskId)
  const task = polledTask?.taskId === validTaskId ? polledTask : null
  const ready = task?.status === 'SUCCESS'
  const transcriptState = useTranscript(validTaskId, ready)
  const recreateState = useCreateTranscription()
  const player = useAudioPlayback(task?.audioFileId, transcriptState.transcript?.durationMs)
  const [downloading, setDownloading] = useState(false)
  const [activeContentTab, setActiveContentTab] = useState('full')
  const [viewMode, setViewMode] = useState<TranscriptViewMode>('compact')
  const [expandedGroupKeys, setExpandedGroupKeys] = useState<Set<string>>(() => new Set())
  const [locatedSegmentOrder, setLocatedSegmentOrder] = useState<number | null>(null)
  const [editingSegmentId, setEditingSegmentId] = useState<string | null>(null)
  const [editingText, setEditingText] = useState('')
  const [editingSpeaker, setEditingSpeaker] = useState('')
  const [savingSegment, setSavingSegment] = useState(false)
  const [exporting, setExporting] = useState(false)
  const progress = clampTranscriptionProgress(task?.progressPercent)
  const progressText = getTranscriptionProgressText(progress, task?.status)
  const segments = transcriptState.transcript?.segments ?? []
  const paragraphs = useMemo(() => createTranscriptParagraphs(segments), [segments])
  const editingSegment = segments.find((segment) => segment.segmentId === editingSegmentId) ?? null

  const activeSegmentIndex = useMemo(() => {
    const currentMs = player.currentTimeSeconds * 1000
    return segments.findIndex(
      (segment) => currentMs >= segment.startMs && currentMs < segment.endMs,
    )
  }, [player.currentTimeSeconds, segments])

  const activeSegmentOrder = activeSegmentIndex >= 0
    ? getTranscriptSegmentOrder(segments[activeSegmentIndex])
    : null
  const highlightedSegmentOrder = locatedSegmentOrder ?? activeSegmentOrder

  useEffect(() => {
    if (
      player.isPlaying
      && locatedSegmentOrder !== null
      && activeSegmentOrder !== null
      && activeSegmentOrder !== locatedSegmentOrder
    ) {
      setLocatedSegmentOrder(null)
    }
  }, [activeSegmentOrder, locatedSegmentOrder, player.isPlaying])

  useEffect(() => {
    if (!player.isPlaying || activeSegmentOrder === null) return
    const paragraph = viewMode === 'compact'
      ? findParagraphBySegmentOrder(paragraphs, activeSegmentOrder)
      : undefined
    const element = paragraph
      ? document.querySelector<HTMLElement>(`[data-paragraph-key="${paragraph.groupKey}"]`)
      : document.querySelector<HTMLElement>(`[data-segment-order="${activeSegmentOrder}"]`)
    if (!element) return
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    element.scrollIntoView({
      block: 'nearest',
      behavior: reduceMotion ? 'auto' : 'smooth',
    })
  }, [activeSegmentOrder, paragraphs, player.isPlaying, viewMode])

  useEffect(() => {
    if (locatedSegmentOrder === null || activeContentTab !== 'segments') return
    const focusLocatedSegment = () => {
      const element = document.querySelector<HTMLButtonElement>(
        `[data-segment-order="${locatedSegmentOrder}"]`,
      )
      if (!element) return
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      element.scrollIntoView({
        block: 'center',
        behavior: reduceMotion ? 'auto' : 'smooth',
      })
      element.focus({ preventScroll: true })
    }
    focusLocatedSegment()
    const timer = window.setTimeout(focusLocatedSegment, 0)
    return () => window.clearTimeout(timer)
  }, [activeContentTab, expandedGroupKeys, locatedSegmentOrder, viewMode])

  const download = async () => {
    if (!task || downloading) return
    setDownloading(true)
    try {
      await downloadAudioFile(task.audioFileId, task.audioFileName || `audio-${task.audioFileId}`)
    } catch (requestError) {
      void message.error(requestError instanceof Error ? requestError.message : '音频下载失败')
    } finally {
      setDownloading(false)
    }
  }

  const recreate = async () => {
    if (!task || recreateState.loading) return
    const next = await recreateState.create(task.audioFileId)
    if (!next) return
    void message.success('已创建新的转写任务')
    navigate(`/transcriptions/${encodeURIComponent(next.taskId)}`)
  }

  const copyFullText = async () => {
    if (!transcriptState.transcript?.fullText) return
    try {
      await navigator.clipboard.writeText(transcriptState.transcript.fullText)
      void message.success('全文已复制')
    } catch {
      void message.error('复制失败，请手动选择文字')
    }
  }

  const exportTranscript = async (format: TranscriptExportFormat) => {
    const transcript = transcriptState.transcript
    if (!transcript || exporting) return
    setExporting(true)
    try {
      await downloadTranscript(transcript.transcriptId, format, task?.audioFileName || 'transcript')
      void message.success(`文字稿已导出为 ${format.toUpperCase()}`)
    } catch (requestError) {
      void message.error(requestError instanceof Error ? requestError.message : '文字稿导出失败')
    } finally {
      setExporting(false)
    }
  }

  const selectEditingSegment = (segmentId: string) => {
    const segment = segments.find((item) => item.segmentId === segmentId)
    if (!segment) return
    setEditingSegmentId(segmentId)
    setEditingText(segment.text)
    setEditingSpeaker(segment.speaker || '')
  }

  const openTranscriptEditor = () => {
    const first = segments[0]
    if (!first) return
    selectEditingSegment(first.segmentId)
  }

  const saveSegment = async () => {
    const transcript = transcriptState.transcript
    if (!transcript || !editingSegment || savingSegment || !editingText.trim()) return
    setSavingSegment(true)
    try {
      await updateTranscriptSegment(transcript.transcriptId, editingSegment.segmentId, {
        text: editingText.trim(),
        speaker: editingSpeaker.trim() || null,
      })
      setEditingSegmentId(null)
      transcriptState.refresh()
      void message.success('文字片段已更新')
    } catch (requestError) {
      void message.error(requestError instanceof Error ? requestError.message : '文字片段保存失败')
    } finally {
      setSavingSegment(false)
    }
  }

  const playSegment = useCallback((segment: TranscriptSegment) => {
    setLocatedSegmentOrder(null)
    void player.seekTo(segment.startMs / 1000, {
      play: true,
      endSeconds: segment.endMs / 1000,
    })
  }, [player.seekTo])

  const playParagraph = useCallback((paragraph: TranscriptParagraph) => {
    setLocatedSegmentOrder(null)
    void player.seekTo(paragraph.startMs / 1000, {
      play: true,
      endSeconds: paragraph.endMs / 1000,
    })
  }, [player.seekTo])

  const toggleParagraph = useCallback((groupKey: string) => {
    setExpandedGroupKeys((current) => {
      const next = new Set(current)
      if (next.has(groupKey)) next.delete(groupKey)
      else next.add(groupKey)
      return next
    })
  }, [])

  const locateSegment = useCallback((segmentOrder: number) => {
    const targetSegment = segments.find(
      (segment) => getTranscriptSegmentOrder(segment) === segmentOrder,
    )
    if (!targetSegment) {
      void message.warning(`未在当前文字稿中找到片段 #${segmentOrder}，请刷新结果后重试`)
      return
    }

    if (viewMode === 'compact') {
      const paragraph = findParagraphBySegmentOrder(paragraphs, segmentOrder)
      if (!paragraph) {
        void message.warning(`片段 #${segmentOrder} 暂无可展示的文字内容`)
        return
      }
      setExpandedGroupKeys((current) => {
        if (current.has(paragraph.groupKey)) return current
        const next = new Set(current)
        next.add(paragraph.groupKey)
        return next
      })
    }

    setActiveContentTab('segments')
    setLocatedSegmentOrder(segmentOrder)
    void player.seekTo(targetSegment.startMs / 1000)
  }, [message, paragraphs, player.seekTo, segments, viewMode])

  const refreshPage = () => {
    refresh()
    if (ready) transcriptState.refresh()
  }

  if (!validTaskId) {
    return <PageContainer><Alert type="error" showIcon message="转写任务地址无效" description="请从转写任务列表重新进入。" action={<Link to="/transcriptions"><Button>返回列表</Button></Link>} /></PageContainer>
  }

  return (
    <PageContainer>
      <PageTitle
        title={task?.audioFileName || '音频文字稿'}
        actions={<><Link to="/transcriptions"><Button icon={<ArrowLeftOutlined />}>返回转写列表</Button></Link><Tooltip title={ready ? '刷新文字稿结果' : '刷新任务状态'}><Button type="text" icon={<ReloadOutlined />} aria-label={ready ? '刷新结果' : '刷新状态'} loading={refreshing || (ready && transcriptState.loading)} onClick={refreshPage} /></Tooltip>{ready && transcriptState.transcript && <Link to={`/transcriptions/${encodeURIComponent(validTaskId)}/agent`}><Button type="primary" icon={<MessageOutlined />}>智能问答</Button></Link>}</>}
      />

      {loading && !task && <section className="workbench-panel"><Skeleton active paragraph={{ rows: 8 }} /></section>}
      {error && (
        <Alert
          className="resource-detail-alert"
          type="error"
          showIcon
          message={task ? '网络连接暂时中断' : '转写任务加载失败'}
          description={task ? `${error}。页面会在轮询时限内自动重试。` : error}
          action={<Button onClick={refresh}>立即重试</Button>}
        />
      )}
      {timedOut && (
        <Alert
          className="resource-detail-alert"
          type="warning"
          showIcon
          message="本次自动查询已暂停"
          description="已持续查询 15 分钟，任务可能仍在后台处理。你可以稍后返回，或立即重新查询状态。"
          action={<Button onClick={refresh}>重新查询</Button>}
        />
      )}

      {task && (
        <div className="transcription-detail">
          <div className="transcription-file-meta" aria-label="文件基本信息">
            <strong className={`is-${task.status.toLowerCase()}`}>{taskStatusLabel(task.status)}</strong>
            <span aria-hidden="true">·</span>
            <span>{transcriptState.transcript ? formatDuration(transcriptState.transcript.durationMs) : '时长待生成'}</span>
            <span aria-hidden="true">·</span>
            <span>{languageLabel(task.language)}</span>
            <span aria-hidden="true">·</span>
            <time dateTime={task.createdAt}>{formatDateTime(task.createdAt)}</time>
          </div>

          {task.status !== 'SUCCESS' && task.status !== 'FAILED' && (
              <div className="transcription-live-progress" aria-live="polite">
                <div>
                  <LoadingOutlined spin />
                  <span>{progressText}</span>
                  <strong>{progress}%</strong>
                </div>
                <Progress
                  percent={progress}
                  showInfo={false}
                  status="active"
                />
                <small>页面每 2 秒自动刷新，离开后任务仍会继续。</small>
              </div>
          )}

          <ReportAudioPlayer
            player={player}
            fallbackFileName={task.audioFileName}
            downloading={downloading}
            onDownload={() => { void download() }}
            sectionId="transcription-audio-player"
            compact
          />

          {task.status === 'FAILED' && (
            <Alert
              className="transcription-failed"
              type="error"
              showIcon
              message="本次转写未完成"
              description={task.failureMessage || '语音识别暂时失败，请稍后重试。'}
              action={<Button type="primary" icon={<ReloadOutlined />} loading={recreateState.loading} disabled={recreateState.loading} onClick={() => { void recreate() }}>重新创建转写任务</Button>}
            />
          )}
          {recreateState.error && <Alert className="resource-detail-alert" type="error" showIcon message="重新创建转写任务失败" description={recreateState.error} closable onClose={recreateState.clearError} />}

          {ready && transcriptState.loading && !transcriptState.transcript && <section className="workbench-panel"><Skeleton active paragraph={{ rows: 10 }} /></section>}
          {ready && transcriptState.error && <Alert className="resource-detail-alert" type="error" showIcon message="文字稿加载失败" description={transcriptState.error} action={<Button onClick={transcriptState.refresh}>重试</Button>} />}

          {transcriptState.transcript && (
            <section className="transcript-workspace" aria-label="文字稿内容">
              <Tabs
                activeKey={activeContentTab}
                onChange={setActiveContentTab}
                items={[
                  {
                    key: 'full',
                    label: '完整文字稿',
                    children: (
                      <div className="transcript-tab-panel transcript-full-text" aria-label="完整文字稿内容">
                        <div className="transcript-tab-heading">
                          <Space wrap>
                            <Button icon={<CopyOutlined />} disabled={!transcriptState.transcript.fullText.trim()} onClick={() => { void copyFullText() }}>复制全文</Button>
                            <Dropdown
                              menu={{
                                items: [
                                  { key: 'txt', label: 'TXT 文本' },
                                  { key: 'srt', label: 'SRT 字幕' },
                                  { key: 'vtt', label: 'VTT 字幕' },
                                ],
                                onClick: ({ key }) => { void exportTranscript(key as TranscriptExportFormat) },
                              }}
                              disabled={exporting}
                            >
                              <Button icon={<DownloadOutlined />} loading={exporting}>导出</Button>
                            </Dropdown>
                          </Space>
                        </div>
                        <div className={`transcript-full-text__content${transcriptState.transcript.fullText.trim() ? '' : ' is-empty'}`}>
                          {transcriptState.transcript.fullText.trim()
                            ? <p>{transcriptState.transcript.fullText}</p>
                            : <div><FileTextOutlined /><strong>暂无完整文字稿内容</strong><span>识别结果为空，可以切换到时间片段或刷新结果。</span></div>}
                        </div>
                      </div>
                    ),
                  },
                  {
                    key: 'segments',
                    label: '时间片段',
                    children: (
                      <div className="transcript-tab-panel transcript-segments" aria-label="时间片段内容">
                        <div className="transcript-tab-heading transcript-segments__heading">
                          <small>{viewMode === 'compact' ? '点击段落时间即可播放' : '点击片段即可从对应位置播放'}</small>
                          <Space wrap>
                            <Button icon={<EditOutlined />} disabled={segments.length === 0} onClick={openTranscriptEditor}>编辑片段</Button>
                            <TranscriptViewSwitcher value={viewMode} onChange={setViewMode} />
                          </Space>
                        </div>
                        {viewMode === 'compact' ? (
                          <TranscriptParagraphList
                            paragraphs={paragraphs}
                            expandedGroupKeys={expandedGroupKeys}
                            highlightedSegmentOrder={highlightedSegmentOrder}
                            loading={transcriptState.loading}
                            onPlayParagraph={playParagraph}
                            onPlaySegment={playSegment}
                            onToggleParagraph={toggleParagraph}
                          />
                        ) : (
                          <TranscriptSentenceList
                            segments={segments}
                            highlightedSegmentOrder={highlightedSegmentOrder}
                            loading={transcriptState.loading}
                            onPlaySegment={playSegment}
                          />
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'analysis',
                    label: '智能分析',
                    children: (
                      <ContentAnalysisSection
                        transcriptId={transcriptState.transcript.transcriptId}
                        onLocateSegment={locateSegment}
                      />
                    ),
                  },
                ]}
              />
            </section>
          )}

          <Modal
            title="编辑文字片段"
            open={Boolean(editingSegmentId)}
            okText="保存修改"
            cancelText="取消"
            confirmLoading={savingSegment}
            okButtonProps={{ disabled: !editingText.trim() }}
            onOk={() => { void saveSegment() }}
            onCancel={() => { if (!savingSegment) setEditingSegmentId(null) }}
            destroyOnHidden
          >
            <div className="transcript-editor-form">
              <label htmlFor="transcript-editor-segment">文字片段</label>
              <Select
                id="transcript-editor-segment"
                value={editingSegmentId || undefined}
                onChange={selectEditingSegment}
                options={segments.map((segment) => ({
                  value: segment.segmentId,
                  label: `#${getTranscriptSegmentOrder(segment)} · ${formatDuration(segment.startMs)} · ${segment.text.slice(0, 36)}`,
                }))}
                showSearch
                optionFilterProp="label"
              />
              <label htmlFor="transcript-editor-speaker">说话人（可选）</label>
              <Input id="transcript-editor-speaker" value={editingSpeaker} maxLength={64} placeholder="例如：主持人、嘉宾 A" onChange={(event) => setEditingSpeaker(event.target.value)} />
              <label htmlFor="transcript-editor-text">片段文字</label>
              <Input.TextArea id="transcript-editor-text" value={editingText} maxLength={5000} autoSize={{ minRows: 5, maxRows: 12 }} showCount onChange={(event) => setEditingText(event.target.value)} />
            </div>
          </Modal>
        </div>
      )}
    </PageContainer>
  )
}
