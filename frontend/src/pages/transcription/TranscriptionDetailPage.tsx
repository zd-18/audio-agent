import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  FileTextOutlined,
  LoadingOutlined,
  MessageOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Alert, App as AntdApp, Button, Progress, Skeleton } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { downloadAudioFile } from '../../api/audioFiles'
import { isValidResourceId } from '../../api/http'
import ReportAudioPlayer from '../../components/audio/ReportAudioPlayer'
import ContentAnalysisSection from '../../components/content-analysis/ContentAnalysisSection'
import TranscriptParagraphList from '../../components/transcription/TranscriptParagraphList'
import TranscriptSentenceList from '../../components/transcription/TranscriptSentenceList'
import TranscriptionStatusBadge from '../../components/transcription/TranscriptionStatusBadge'
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

function elapsedLabel(start?: string | null, end?: string | null) {
  if (!start) return '—'
  const startMs = new Date(start).getTime()
  const endMs = end ? new Date(end).getTime() : Date.now()
  return Number.isFinite(startMs) && Number.isFinite(endMs)
    ? formatDuration(Math.max(0, endMs - startMs))
    : '—'
}

function languageLabel(value: string) {
  return value.toLowerCase() === 'zh' ? '中文（zh）' : value
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
  const [viewMode, setViewMode] = useState<TranscriptViewMode>('compact')
  const [expandedGroupKeys, setExpandedGroupKeys] = useState<Set<string>>(() => new Set())
  const [locatedSegmentOrder, setLocatedSegmentOrder] = useState<number | null>(null)
  const progress = clampTranscriptionProgress(task?.progressPercent)
  const progressText = getTranscriptionProgressText(progress, task?.status)
  const segments = transcriptState.transcript?.segments ?? []
  const paragraphs = useMemo(() => createTranscriptParagraphs(segments), [segments])

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
    if (locatedSegmentOrder === null) return
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
  }, [expandedGroupKeys, locatedSegmentOrder, viewMode])

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
        eyebrow="TRANSCRIPT DETAIL"
        title={task?.audioFileName || '音频文字稿'}
        description="逐段查看识别文字，并与源音频时间轴联动。"
        actions={<><Link to="/transcriptions"><Button icon={<ArrowLeftOutlined />}>返回转写列表</Button></Link>{ready && transcriptState.transcript && <Link to={`/transcriptions/${encodeURIComponent(validTaskId)}/agent`}><Button type="primary" icon={<MessageOutlined />}>智能问答</Button></Link>}<Button icon={<ReloadOutlined />} loading={refreshing || (ready && transcriptState.loading)} onClick={refreshPage}>{ready ? '刷新结果' : '刷新状态'}</Button></>}
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
        <>
          <section className="workbench-panel transcription-summary" aria-labelledby="transcription-summary-title">
            <div className="workbench-panel__heading"><div><span>TRANSCRIPTION STATUS</span><h3 id="transcription-summary-title">任务概览</h3></div><TranscriptionStatusBadge status={task.status} /></div>
            <dl>
              <div><dt>音频文件</dt><dd>{task.audioFileName || '未命名音频'}</dd></div>
              <div><dt>语言</dt><dd>{languageLabel(task.language)}</dd></div>
              <div><dt>转写耗时</dt><dd>{elapsedLabel(task.startedAt, task.finishedAt)}</dd></div>
              <div><dt>创建时间</dt><dd>{formatDateTime(task.createdAt)}</dd></div>
            </dl>
            {task.status !== 'FAILED' && (
              <div className="transcription-live-progress" aria-live="polite">
                <div>
                  {task.status === 'SUCCESS' ? <CheckCircleOutlined /> : <LoadingOutlined spin />}
                  <span>{progressText}</span>
                  <strong>{progress}%</strong>
                </div>
                <Progress
                  percent={progress}
                  showInfo={false}
                  status={task.status === 'SUCCESS' ? 'success' : 'active'}
                />
                <small>{task.status === 'SUCCESS' ? '文字稿已经可以查看。' : '页面每 2 秒自动刷新，离开后任务仍会继续。'}</small>
              </div>
            )}
          </section>

          <ReportAudioPlayer
            player={player}
            fallbackFileName={task.audioFileName}
            downloading={downloading}
            onDownload={() => { void download() }}
            sectionId="transcription-audio-player"
            eyebrow="SOURCE AUDIO"
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
            <>
              <section className="workbench-panel transcript-full-text" aria-labelledby="transcript-full-title">
                <div className="workbench-panel__heading">
                  <div><span>FULL TRANSCRIPT</span><h3 id="transcript-full-title">完整文字稿</h3></div>
                  <Button icon={<CopyOutlined />} disabled={!transcriptState.transcript.fullText.trim()} onClick={() => { void copyFullText() }}>复制全文</Button>
                </div>
                <div className={`transcript-full-text__content${transcriptState.transcript.fullText.trim() ? '' : ' is-empty'}`}>
                  {transcriptState.transcript.fullText.trim()
                    ? <p>{transcriptState.transcript.fullText}</p>
                    : <div><FileTextOutlined /><strong>暂无完整文字稿内容</strong><span>识别结果为空，你仍可以查看下方时间片段或刷新结果。</span></div>}
                </div>
                <div className="transcript-facts">
                  <span>音频时长 {formatDuration(transcriptState.transcript.durationMs)}</span>
                  <span>语言 {languageLabel(transcriptState.transcript.language)}</span>
                  <span>{transcriptState.transcript.segmentCount} 个片段</span>
                  {typeof transcriptState.transcript.speakerCount === 'number' && transcriptState.transcript.speakerCount > 0 && <span>{transcriptState.transcript.speakerCount} 位说话人</span>}
                </div>
              </section>

              <ContentAnalysisSection
                transcriptId={transcriptState.transcript.transcriptId}
                onLocateSegment={locateSegment}
              />

              <section className="workbench-panel transcript-segments" aria-labelledby="transcript-segments-title">
                <div className="workbench-panel__heading transcript-segments__heading">
                  <div><span>TIMESTAMPED SEGMENTS</span><h3 id="transcript-segments-title">时间片段</h3></div>
                  <div className="transcript-segments__controls">
                    <small>{viewMode === 'compact' ? '点击段落时间即可播放' : '点击片段即可从对应位置播放'}</small>
                    <TranscriptViewSwitcher value={viewMode} onChange={setViewMode} />
                  </div>
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
              </section>
            </>
          )}
        </>
      )}
    </PageContainer>
  )
}
