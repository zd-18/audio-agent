import {
  BulbOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  LoadingOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App as AntdApp,
  Button,
  Checkbox,
  Modal,
  Progress,
  Tabs,
  Tag,
} from 'antd'
import { useMemo, useState } from 'react'
import { useContentAnalysis } from '../../hooks/useContentAnalysis'
import type {
  ContentAnalysisChapter,
  ContentAnalysisKeyPoint,
  ContentAnalysisSpeechIssue,
  ContentAnalysisType,
  TimePrecision,
} from '../../types/contentAnalysis'
import {
  clampContentAnalysisProgress,
  getContentAnalysisProgressText,
  speechIssueSeverityLabels,
  speechIssueTypeLabels,
} from '../../utils/contentAnalysis'
import { formatDuration } from '../../utils/formatters'
import './content-analysis.css'

const ALL_TYPES: ContentAnalysisType[] = [
  'SUMMARY',
  'KEY_POINTS',
  'CHAPTERS',
  'SPEECH_ISSUES',
]

const TYPE_OPTIONS = [
  { label: '内容摘要', value: 'SUMMARY' },
  { label: '关键观点', value: 'KEY_POINTS' },
  { label: '章节划分', value: 'CHAPTERS' },
  { label: '表达问题', value: 'SPEECH_ISSUES' },
]

interface ContentAnalysisSectionProps {
  transcriptId: string
  onLocateSegment: (segmentOrder: number) => void
}

function rangeLabel(startMs?: number | null, endMs?: number | null) {
  if (typeof startMs !== 'number' || typeof endMs !== 'number') return '时间范围未知'
  return `${formatDuration(startMs)} - ${formatDuration(endMs)}`
}

function precisionLabel(precision?: TimePrecision) {
  return precision === 'SEGMENT' ? '片段级定位' : '文字稿级定位'
}

export default function ContentAnalysisSection({
  transcriptId,
  onLocateSegment,
}: ContentAnalysisSectionProps) {
  const { message } = AntdApp.useApp()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [selectedTypes, setSelectedTypes] = useState<ContentAnalysisType[]>(ALL_TYPES)
  const analysis = useContentAnalysis(transcriptId)
  const progress = clampContentAnalysisProgress(analysis.task?.progressPercent)
  const active = analysis.task?.status === 'PENDING' || analysis.task?.status === 'RUNNING'

  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      void message.success('内容已复制')
    } catch {
      void message.error('复制失败，请手动选择文字')
    }
  }

  const start = async () => {
    const next = await analysis.start(selectedTypes)
    if (!next) return
    setConfirmOpen(false)
    void message.success('智能分析任务已创建')
  }

  const locate = (orders?: number[]) => {
    const first = orders?.[0]
    if (typeof first === 'number') onLocateSegment(first)
  }

  const tabs = useMemo(() => {
    if (!analysis.result) return []
    const result = analysis.result
    return [
      {
        key: 'summary',
        label: '内容摘要',
        children: (
          <div className="content-analysis-summary">
            <article>
              <div className="content-analysis-card__heading">
                <h4>一句话摘要</h4>
                <Button
                  type="text"
                  icon={<CopyOutlined />}
                  aria-label="复制一句话摘要"
                  onClick={() => { void copy(result.summary.oneSentence) }}
                />
              </div>
              <p>{result.summary.oneSentence || '暂无摘要'}</p>
            </article>
            <article>
              <div className="content-analysis-card__heading">
                <h4>详细摘要</h4>
                <Button
                  type="text"
                  icon={<CopyOutlined />}
                  aria-label="复制详细摘要"
                  onClick={() => { void copy(result.summary.detailed) }}
                />
              </div>
              <div className="content-analysis-scroll-text">
                {result.summary.detailed || '暂无详细摘要'}
              </div>
            </article>
            <div className="content-analysis-topics">
              {result.summary.topics.map((topic) => <Tag key={topic}>{topic}</Tag>)}
            </div>
          </div>
        ),
      },
      {
        key: 'points',
        label: '关键观点与章节',
        children: (
          <div className="content-analysis-two-column">
            <div>
              <h4 className="content-analysis-column-title">关键观点</h4>
              {result.keyPoints.map((point: ContentAnalysisKeyPoint) => (
                <article className="content-analysis-card" key={`point-${point.order}`}>
                  <div className="content-analysis-card__heading">
                    <h4>{point.order}. {point.title}</h4>
                    <div className="content-analysis-card__actions">
                      <Button
                        type="text"
                        icon={<CopyOutlined />}
                        aria-label={`复制观点 ${point.order} 的原文依据`}
                        onClick={() => { void copy(point.evidenceQuote) }}
                      />
                      <Button
                        type="text"
                        icon={<SearchOutlined />}
                        disabled={!point.sourceSegmentOrders?.length}
                        onClick={() => locate(point.sourceSegmentOrders)}
                      >
                        定位原文
                      </Button>
                    </div>
                  </div>
                  <p>{point.description}</p>
                  <blockquote>{point.evidenceQuote}</blockquote>
                  <div className="content-analysis-meta">
                    <span>{rangeLabel(point.startMs, point.endMs)}</span>
                    <Tag>{precisionLabel(point.timePrecision)}</Tag>
                  </div>
                </article>
              ))}
              {result.keyPoints.length === 0 && <div className="content-analysis-empty">当前分析未提取关键观点</div>}
            </div>
            <div>
              <h4 className="content-analysis-column-title">章节</h4>
              {result.chapters.map((chapter: ContentAnalysisChapter) => (
                <article className="content-analysis-card" key={`chapter-${chapter.order}`}>
                  <h4>{chapter.order}. {chapter.title}</h4>
                  <p>{chapter.summary}</p>
                  <div className="content-analysis-meta">
                    <span>{rangeLabel(chapter.startMs, chapter.endMs)}</span>
                    <Tag>{precisionLabel(chapter.timePrecision)}</Tag>
                  </div>
                </article>
              ))}
              {result.chapters.length === 0 && <div className="content-analysis-empty">当前分析未生成章节</div>}
            </div>
          </div>
        ),
      },
      {
        key: 'issues',
        label: '表达问题',
        children: result.speechIssues.length === 0
          ? <div className="content-analysis-empty is-success"><CheckCircleOutlined />暂未发现明显表达问题</div>
          : (
            <div className="content-analysis-issues">
              {result.speechIssues.map((issue: ContentAnalysisSpeechIssue) => (
                <article className="content-analysis-card" key={`issue-${issue.order}`}>
                  <div className="content-analysis-card__heading">
                    <div>
                      <Tag color="purple">{speechIssueTypeLabels[issue.type]}</Tag>
                      <Tag color={issue.severity === 'HIGH' ? 'red' : issue.severity === 'MEDIUM' ? 'orange' : 'blue'}>
                        {speechIssueSeverityLabels[issue.severity]}
                      </Tag>
                    </div>
                    <div className="content-analysis-card__actions">
                      <Button
                        type="text"
                        icon={<CopyOutlined />}
                        aria-label={`复制表达问题 ${issue.order} 的原文依据`}
                        onClick={() => { void copy(issue.evidenceQuote) }}
                      />
                      <Button
                        type="text"
                        icon={<SearchOutlined />}
                        disabled={!issue.sourceSegmentOrders?.length}
                        onClick={() => locate(issue.sourceSegmentOrders)}
                      >
                        定位原文
                      </Button>
                    </div>
                  </div>
                  <h4>{issue.order}. {issue.description}</h4>
                  <blockquote>{issue.evidenceQuote}</blockquote>
                  <p><strong>修改建议：</strong>{issue.suggestion}</p>
                  <div className="content-analysis-meta">
                    <span>{rangeLabel(issue.startMs, issue.endMs)}</span>
                    <Tag>{precisionLabel(issue.timePrecision)}</Tag>
                  </div>
                </article>
              ))}
            </div>
          ),
      },
    ]
  }, [analysis.result])

  return (
    <section className="content-analysis" aria-label="智能分析">
      <div className="content-analysis-toolbar">
        <p className="content-analysis-intro">
          基于当前文字稿生成摘要、关键观点、章节和表达建议。
        </p>
        {analysis.result && (
        <Button
          icon={<BulbOutlined />}
          loading={analysis.creating}
          disabled={analysis.creating || active || analysis.task?.status === 'FAILED'}
          onClick={() => setConfirmOpen(true)}
        >
          重新分析
        </Button>
        )}
      </div>

      {analysis.error && (
        <Alert
          className="content-analysis-alert"
          type="error"
          showIcon
          message="智能分析请求未完成"
          description={analysis.error}
          closable
        />
      )}

      {analysis.timedOut && (
        <Alert
          className="content-analysis-alert"
          type="warning"
          showIcon
          message="自动查询已暂停"
          description="已查询 15 分钟，后台任务可能仍在运行。"
          action={<Button onClick={analysis.refresh}>继续查询</Button>}
        />
      )}

      {analysis.task?.status === 'FAILED' && (
        <Alert
          className="content-analysis-alert"
          type="error"
          showIcon
          message="本次智能分析未完成"
          description={analysis.task.failureMessage || '智能分析暂时失败，请稍后重试。'}
          action={(
            <Button
              icon={<ReloadOutlined />}
              loading={analysis.retrying}
              onClick={() => { void analysis.retry() }}
            >
              重试
            </Button>
          )}
        />
      )}

      {(active || (analysis.loading && !analysis.result && analysis.task)) && (
        <div className="content-analysis-progress" aria-live="polite">
          <div>
            <LoadingOutlined spin />
            <span>{getContentAnalysisProgressText(progress, analysis.task?.status)}</span>
            <strong>{progress}%</strong>
          </div>
          <Progress percent={progress} showInfo={false} status="active" />
          <small>页面每 2 秒自动刷新；离开页面后分析仍会继续。</small>
        </div>
      )}

      {analysis.result && (
        <div className="content-analysis-result">
          <Tabs items={tabs} />
        </div>
      )}

      {!analysis.task && !analysis.loading && (
        <div className="content-analysis-empty content-analysis-empty--prompt">
          <BulbOutlined />
          <div>
            <strong>尚未生成智能分析</strong>
            <span>生成摘要、关键观点、章节和表达建议。</span>
          </div>
          <Button type="primary" icon={<BulbOutlined />} aria-label="生成智能分析" onClick={() => setConfirmOpen(true)}>
            生成智能分析
          </Button>
        </div>
      )}

      <Modal
        title="开始智能分析"
        open={confirmOpen}
        confirmLoading={analysis.creating}
        okText="开始分析"
        cancelText="取消"
        okButtonProps={{ disabled: selectedTypes.length === 0 }}
        onOk={() => { void start() }}
        onCancel={() => {
          if (!analysis.creating) setConfirmOpen(false)
        }}
      >
        <Alert
          type="info"
          showIcon
          message="将文字稿内容发送至外部 AI 模型进行分析"
          description="不会上传原始音频，也不会发送账号 Token 或存储配置。"
        />
        <fieldset className="content-analysis-options">
          <legend>选择分析能力</legend>
          <Checkbox.Group
            options={TYPE_OPTIONS}
            value={selectedTypes}
            onChange={(values) => setSelectedTypes(values as ContentAnalysisType[])}
          />
        </fieldset>
      </Modal>
    </section>
  )
}
