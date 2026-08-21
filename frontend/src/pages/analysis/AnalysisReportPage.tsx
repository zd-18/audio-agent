import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Tooltip, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { downloadAudioFile } from '../../api/audioFiles'
import { isValidResourceId } from '../../api/http'
import ProcessingPlanAccessButton from '../../components/analysis/ProcessingPlanAccessButton'
import ReportAudioPlayer from '../../components/audio/ReportAudioPlayer'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAnalysisReport } from '../../hooks/useAnalysisReport'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { Recommendation, ReportTimelineItem } from '../../types/analysisReport'
import { formatDateTime } from '../../utils/formatters'
import './analysis-report.css'
import AnalysisReportSkeleton from './report/AnalysisReportSkeleton'
import AudioOverviewPanel from './report/AudioOverviewPanel'
import IssueSummaryBar from './report/IssueSummaryBar'
import IssueTimeline from './report/IssueTimeline'
import KeyIssuesPanel from './report/KeyIssuesPanel'
import LoudnessOverviewPanel from './report/LoudnessOverviewPanel'
import QualityScorePanel from './report/QualityScorePanel'
import RecommendationList from './report/RecommendationList'
import ReportErrorState from './report/ReportErrorState'

function compactId(value: string) {
  return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value
}

function scrollToElement(elementId: string) {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document.getElementById(elementId)?.scrollIntoView({
    behavior: reduceMotion ? 'auto' : 'smooth',
    block: 'center',
  })
}

function hasPlayableStart(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
}

export default function AnalysisReportPage() {
  const { taskId } = useParams()
  const [searchParams] = useSearchParams()
  const validTaskId = isValidResourceId(taskId) ? taskId : undefined
  const requestedIssueId = searchParams.get('issueId')
  const { report, relatedTask, error, loading, refreshing, refresh } = useAnalysisReport(validTaskId)
  const [highlightedIssueId, setHighlightedIssueId] = useState<string>()
  const [timelineOpen, setTimelineOpen] = useState(Boolean(requestedIssueId))
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const downloadControllerRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)
  const player = useAudioPlayback(report?.audioFileId, report?.audioOverview.durationMs)
  const { settings } = useUserSettings()

  useEffect(() => {
    downloadControllerRef.current?.abort()
    downloadControllerRef.current = null
    setHighlightedIssueId(undefined)
    setTimelineOpen(Boolean(requestedIssueId))
    setDownloading(false)
    setDownloadError(null)
  }, [validTaskId])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      downloadControllerRef.current?.abort()
    }
  }, [])

  useEffect(() => {
    if (!report || !requestedIssueId) return undefined
    const issueExists = report.timeline.some((issue) => issue.issueId === requestedIssueId)
    if (!issueExists) return undefined

    setHighlightedIssueId(requestedIssueId)
    setTimelineOpen(true)
    const frame = window.requestAnimationFrame(() => scrollToElement('report-issue-timeline'))
    return () => window.cancelAnimationFrame(frame)
  }, [report, requestedIssueId])

  const seekToIssue = (issue: ReportTimelineItem, play = false) => {
    if (!hasPlayableStart(issue.startMs)) return
    if (issue.issueId) setHighlightedIssueId(issue.issueId)
    scrollToElement('report-audio-player')
    const contextSeconds = play ? (settings?.issueContextSeconds ?? 0) : 0
    const startSeconds = Math.max(0, issue.startMs / 1000 - contextSeconds)
    const totalSeconds = typeof report?.audioOverview.durationMs === 'number'
      ? Math.max(0, report.audioOverview.durationMs / 1000)
      : undefined
    const rawEndSeconds = play && hasPlayableStart(issue.endMs)
      ? issue.endMs / 1000 + contextSeconds
      : undefined
    const endSeconds = rawEndSeconds === undefined
      ? undefined
      : totalSeconds === undefined ? rawEndSeconds : Math.min(totalSeconds, rawEndSeconds)
    void player.seekTo(startSeconds, {
      play,
      endSeconds,
    })
  }

  const seekToRecommendation = (recommendation: Recommendation, play = false) => {
    const issue = report?.timeline.find((item) => (
      recommendation.issueId
        ? item.issueId === recommendation.issueId
        : item.startMs === recommendation.startMs && item.endMs === recommendation.endMs
    ))
    if (issue) {
      seekToIssue(issue, play)
      return
    }
    if (!hasPlayableStart(recommendation.startMs)) return

    if (recommendation.issueId) setHighlightedIssueId(recommendation.issueId)
    scrollToElement('report-audio-player')
    const contextSeconds = play ? (settings?.issueContextSeconds ?? 0) : 0
    const startSeconds = Math.max(0, recommendation.startMs / 1000 - contextSeconds)
    const totalSeconds = typeof report?.audioOverview.durationMs === 'number'
      ? Math.max(0, report.audioOverview.durationMs / 1000)
      : undefined
    const rawEndSeconds = play && hasPlayableStart(recommendation.endMs)
      ? recommendation.endMs / 1000 + contextSeconds
      : undefined
    const endSeconds = rawEndSeconds === undefined
      ? undefined
      : totalSeconds === undefined ? rawEndSeconds : Math.min(totalSeconds, rawEndSeconds)
    void player.seekTo(startSeconds, {
      play,
      endSeconds,
    })
  }

  const download = async () => {
    if (!report || downloading) return
    const controller = new AbortController()
    downloadControllerRef.current?.abort()
    downloadControllerRef.current = controller
    setDownloading(true)
    setDownloadError(null)
    try {
      await downloadAudioFile(
        report.audioFileId,
        report.audioOverview.fileName || `audio-${report.audioFileId}`,
        controller.signal,
      )
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        if (mountedRef.current) {
          setDownloadError(requestError instanceof Error ? requestError.message : '文件下载失败')
        }
      }
    } finally {
      if (downloadControllerRef.current === controller) {
        downloadControllerRef.current = null
        if (mountedRef.current) setDownloading(false)
      }
    }
  }

  if (!validTaskId) {
    return (
      <PageContainer>
        <PageTitle eyebrow="SMART DIAGNOSIS" title="智能诊断结果" />
        <Alert
          type="error"
          showIcon
          message="任务 ID 无效"
          description="URL 中缺少有效的 taskId，请返回分析任务列表重新选择。"
          action={<Link to="/analysis/tasks"><Button>返回分析任务</Button></Link>}
        />
      </PageContainer>
    )
  }

  return (
    <PageContainer>
      <PageTitle
        eyebrow="SMART DIAGNOSIS"
        title="智能诊断结果"
        actions={(
          <>
            <Link to={`/analysis/tasks/${validTaskId}`}><Button icon={<ArrowLeftOutlined />}>返回任务详情</Button></Link>
            <Button icon={<ReloadOutlined />} loading={refreshing} onClick={refresh}>刷新报告</Button>
            <ProcessingPlanAccessButton taskId={validTaskId} status="SUCCESS" type="primary" />
          </>
        )}
      />

      {loading && !report && <AnalysisReportSkeleton />}
      {!loading && error && !report && (
        <ReportErrorState taskId={validTaskId} error={error} relatedTask={relatedTask} onRetry={refresh} />
      )}

      {report && (
        <article className="analysis-report-content">
          <header className="report-document-meta">
            <div>
              <span>分析文件</span>
              <Tooltip title={report.audioOverview.fileName || undefined}>
                <strong>{report.audioOverview.fileName || '未命名音频'}</strong>
              </Tooltip>
            </div>
            <dl>
              <div><dt>报告生成时间</dt><dd>{formatDateTime(report.generatedAt)}</dd></div>
              <div>
                <dt>任务 ID</dt>
                <dd>
                  <Typography.Text copyable={{ text: report.taskId, tooltips: ['复制任务 ID', '已复制'] }}>
                    {compactId(report.taskId)}
                  </Typography.Text>
                </dd>
              </div>
            </dl>
          </header>

          {error && (
            <Alert
              className="report-refresh-alert"
              type="warning"
              showIcon
              message="本次刷新未成功，当前仍显示上一次加载的报告。"
              action={<Button onClick={refresh}>再次刷新</Button>}
            />
          )}

          {downloadError && (
            <Alert
              className="report-refresh-alert"
              type="error"
              showIcon
              closable
              message="音频下载失败"
              description={downloadError}
              onClose={() => setDownloadError(null)}
            />
          )}

          <ReportAudioPlayer
            player={player}
            fallbackFileName={report.audioOverview.fileName}
            downloading={downloading}
            onDownload={() => { void download() }}
          />

          <QualityScorePanel report={report} />
          <IssueSummaryBar summary={report.issueSummary} />
          <KeyIssuesPanel
            issues={report.keyIssues}
            onLocate={(issue) => seekToIssue(issue)}
            onPreview={(issue) => seekToIssue(issue, true)}
          />

          <section className="report-current-metrics report-reveal-section" aria-labelledby="report-current-metrics-title">
            <div className="report-section-heading">
              <div>
                <span className="report-section-kicker">CURRENT METRICS</span>
                <h2 id="report-current-metrics-title">当前指标</h2>
              </div>
            </div>
            <div className="report-overview-grid">
              <AudioOverviewPanel overview={report.audioOverview} />
              <LoudnessOverviewPanel overview={report.loudnessOverview} />
            </div>
          </section>

          <details
            className="report-timeline-disclosure"
            open={timelineOpen}
            onToggle={(event) => setTimelineOpen(event.currentTarget.open)}
          >
            <summary>查看完整问题时间线</summary>
            <IssueTimeline
              issues={report.timeline}
              audioDurationMs={report.audioOverview.durationMs}
              highlightedIssueId={highlightedIssueId}
              currentTimeSeconds={player.currentTimeSeconds}
              onLocateIssue={(issue) => seekToIssue(issue)}
              onPreviewIssue={(issue) => seekToIssue(issue, true)}
            />
          </details>

          <RecommendationList
            recommendations={report.recommendations}
            onLocate={(recommendation) => seekToRecommendation(recommendation)}
            onPreview={(recommendation) => seekToRecommendation(recommendation, true)}
          />
        </article>
      )}
    </PageContainer>
  )
}
