import { AimOutlined, FilterOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { Button, Pagination, Select, Tooltip } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import type { ReportIssueType, ReportTimelineItem } from '../../../types/analysisReport'
import { formatDuration, formatTimestamp } from '../../../utils/audioTime'
import {
  getIssueClassName,
  getIssueTypeLabel,
  getSeverityLabel,
  ISSUE_TYPE_META,
} from '../reportMappings'

const PAGE_SIZE = 8
const FILTER_OPTIONS: Array<{ value: 'ALL' | ReportIssueType; label: string }> = [
  { value: 'ALL', label: '全部问题' },
  ...Object.entries(ISSUE_TYPE_META).map(([value, meta]) => ({
    value: value as ReportIssueType,
    label: meta.label,
  })),
]

interface PositionedIssue {
  issue: ReportTimelineItem
  key: string
  lane: number
  left: number
  width: number
}

interface IssueTimelineProps {
  issues: ReportTimelineItem[]
  audioDurationMs: number | null
  highlightedIssueId?: string
  currentTimeSeconds?: number
  onLocateIssue?: (issue: ReportTimelineItem) => void
  onPreviewIssue?: (issue: ReportTimelineItem) => void
}

function issueKey(issue: ReportTimelineItem, index: number) {
  return issue.issueId || `${issue.issueType || 'issue'}-${issue.startMs ?? 'x'}-${issue.endMs ?? 'x'}-${index}`
}

function buildPositionedIssues(issues: ReportTimelineItem[], totalDuration: number) {
  const laneEnds: number[] = []

  return issues.flatMap<PositionedIssue>((issue, index) => {
    if (typeof issue.startMs !== 'number' || !Number.isFinite(issue.startMs)) return []

    const start = Math.min(totalDuration, Math.max(0, issue.startMs))
    const candidateEnd = typeof issue.endMs === 'number' && Number.isFinite(issue.endMs)
      ? issue.endMs
      : start + (typeof issue.durationMs === 'number' && Number.isFinite(issue.durationMs) ? issue.durationMs : 0)
    const end = Math.min(totalDuration, Math.max(start, candidateEnd))
    let lane = laneEnds.findIndex((laneEnd) => laneEnd <= start)
    if (lane < 0) {
      lane = laneEnds.length
      laneEnds.push(end)
    } else {
      laneEnds[lane] = end
    }

    return [{
      issue,
      key: issueKey(issue, index),
      lane,
      left: (start / totalDuration) * 100,
      width: ((end - start) / totalDuration) * 100,
    }]
  })
}

function TimelineTooltip({ issue }: { issue: ReportTimelineItem }) {
  return (
    <div className="report-timeline-tooltip">
      <strong>{issue.title || getIssueTypeLabel(issue.issueType)}</strong>
      <span>开始：{formatTimestamp(issue.startMs)}</span>
      <span>结束：{formatTimestamp(issue.endMs)}</span>
      <span>持续：{formatDuration(issue.durationMs)}</span>
      <span>严重程度：{getSeverityLabel(issue.severity)}</span>
    </div>
  )
}

export default function IssueTimeline({
  issues,
  audioDurationMs,
  highlightedIssueId,
  currentTimeSeconds = 0,
  onLocateIssue,
  onPreviewIssue,
}: IssueTimelineProps) {
  const [filter, setFilter] = useState<'ALL' | ReportIssueType>('ALL')
  const [page, setPage] = useState(1)
  const [selectedKey, setSelectedKey] = useState<string>()

  const orderedIssues = useMemo(() => [...issues].sort((first, second) => (
    (first.startMs ?? Number.MAX_SAFE_INTEGER) - (second.startMs ?? Number.MAX_SAFE_INTEGER)
  )), [issues])
  const totalDuration = typeof audioDurationMs === 'number' && Number.isFinite(audioDurationMs) && audioDurationMs > 0
    ? audioDurationMs
    : null
  const positionedIssues = useMemo(
    () => totalDuration ? buildPositionedIssues(orderedIssues, totalDuration) : [],
    [orderedIssues, totalDuration],
  )
  const laneCount = Math.max(1, positionedIssues.reduce((maximum, item) => Math.max(maximum, item.lane + 1), 0))

  useEffect(() => {
    if (!highlightedIssueId) return
    const target = positionedIssues.find((item) => item.issue.issueId === highlightedIssueId)
    if (target) setSelectedKey(target.key)
  }, [highlightedIssueId, positionedIssues])

  useEffect(() => {
    setPage(1)
  }, [filter, issues])

  const filteredIssues = filter === 'ALL'
    ? orderedIssues
    : orderedIssues.filter((issue) => issue.issueType === filter)
  const pageRecords = filteredIssues.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
  const selected = positionedIssues.find((item) => item.key === selectedKey)?.issue
  const playheadPercent = totalDuration
    ? Math.min(100, Math.max(0, (currentTimeSeconds * 1000 / totalDuration) * 100))
    : null

  const selectIssue = (issue: ReportTimelineItem, key?: string, preview = false) => {
    const target = key
      ? positionedIssues.find((item) => item.key === key)
      : positionedIssues.find((item) => (
        item.issue === issue
        || (Boolean(issue.issueId) && item.issue.issueId === issue.issueId)
        || (
          !issue.issueId
          && item.issue.issueType === issue.issueType
          && item.issue.startMs === issue.startMs
          && item.issue.endMs === issue.endMs
        )
      ))
    setSelectedKey(target?.key || key)
    if (preview) onPreviewIssue?.(issue)
    else onLocateIssue?.(issue)
  }

  return (
    <section id="report-issue-timeline" className="report-timeline report-reveal-section" aria-labelledby="issue-timeline-title">
      <div className="report-section-heading report-timeline__heading">
        <div>
          <span className="report-section-kicker">ISSUE TIMELINE</span>
          <h2 id="issue-timeline-title">问题时间线</h2>
          <p>时间线宽度代表完整音频时长，点击片段可查看问题详情。</p>
        </div>
        <div className="report-timeline__legend" aria-label="问题类型图例">
          {(Object.keys(ISSUE_TYPE_META) as ReportIssueType[]).map((type) => (
            <span key={type} className={`report-timeline__legend-item report-issue--${getIssueClassName(type)}`}>
              <i aria-hidden="true" />{ISSUE_TYPE_META[type].label}
            </span>
          ))}
        </div>
      </div>

      {orderedIssues.length === 0 ? (
        <div className="report-timeline__empty"><AimOutlined /><strong>未发现明显的问题片段。</strong><span>本次报告没有返回需要定位的时间段。</span></div>
      ) : (
        <>
          {totalDuration ? (
            <div className="report-timeline__viewport" tabIndex={0} aria-label="可横向滚动的问题时间线">
              <div className="report-timeline__canvas">
                <div className="report-timeline__axis" aria-hidden="true">
                  {[0, 0.25, 0.5, 0.75, 1].map((ratio) => (
                    <span key={ratio} style={{ left: `${ratio * 100}%` }}>{formatDuration(totalDuration * ratio)}</span>
                  ))}
                </div>
                {playheadPercent !== null && (
                  <span
                    className="report-timeline__playhead"
                    style={{ left: `${playheadPercent}%` }}
                    aria-hidden="true"
                  />
                )}
                <div className="report-timeline__lanes" style={{ '--report-lane-count': laneCount } as CSSProperties}>
                  {Array.from({ length: laneCount }, (_, lane) => (
                    <div key={lane} className="report-timeline__lane">
                      {positionedIssues.filter((item) => item.lane === lane).map((item) => {
                        const className = getIssueClassName(item.issue.issueType)
                        const isSelected = item.key === selectedKey
                        return (
                          <Tooltip key={item.key} title={<TimelineTooltip issue={item.issue} />} placement="top">
                            <button
                              type="button"
                              className={`report-timeline__segment report-issue--${className}${isSelected ? ' is-selected' : ''}`}
                              style={{
                                left: `${item.left}%`,
                                width: `${item.width}%`,
                                maxWidth: `calc(100% - ${item.left}%)`,
                              }}
                              aria-label={`${item.issue.title || getIssueTypeLabel(item.issue.issueType)}，${formatTimestamp(item.issue.startMs)} 至 ${formatTimestamp(item.issue.endMs)}，${getSeverityLabel(item.issue.severity)}`}
                              aria-pressed={isSelected}
                              onClick={() => selectIssue(item.issue, item.key)}
                            >
                              <span>{item.issue.issueType ? ISSUE_TYPE_META[item.issue.issueType].shortLabel : '问题'}</span>
                            </button>
                          </Tooltip>
                        )
                      })}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <div className="report-inline-empty"><span>音频总时长缺失，暂时无法绘制时间线位置。</span></div>
          )}

          {selected && (
            <article className={`report-selected-issue report-issue--${getIssueClassName(selected.issueType)}`} aria-live="polite">
              <div>
                <span className="report-selected-issue__type">{getIssueTypeLabel(selected.issueType)}</span>
                <h3>{selected.title || getIssueTypeLabel(selected.issueType)}</h3>
                <p>{selected.description || '当前问题片段未提供额外说明。'}</p>
              </div>
              <dl>
                <div><dt>时间范围</dt><dd>{formatTimestamp(selected.startMs)} – {formatTimestamp(selected.endMs)}</dd></div>
                <div><dt>持续时长</dt><dd>{formatDuration(selected.durationMs)}</dd></div>
                <div><dt>严重程度</dt><dd>{getSeverityLabel(selected.severity)}</dd></div>
              </dl>
              <div className="report-selected-issue__actions">
                <Button icon={<AimOutlined />} onClick={() => selectIssue(selected)}>定位到此处</Button>
                <Button icon={<PlayCircleOutlined />} onClick={() => selectIssue(selected, undefined, true)}>试听此片段</Button>
              </div>
            </article>
          )}

          <div className="report-issue-records">
            <div className="report-issue-records__toolbar">
              <div><h3>问题记录</h3><span>按发生时间升序</span></div>
              <Select
                aria-label="按问题类型筛选"
                value={filter}
                options={FILTER_OPTIONS}
                prefix={<FilterOutlined />}
                onChange={setFilter}
              />
            </div>
            <div className="report-issue-records__list">
              {pageRecords.map((issue, index) => {
                const key = issueKey(issue, orderedIssues.indexOf(issue))
                const isSelected = key === selectedKey
                return (
                  <article
                    key={key}
                    className={`report-issue-record report-issue--${getIssueClassName(issue.issueType)}${isSelected ? ' is-selected' : ''}`}
                  >
                    <button
                      type="button"
                      className="report-issue-record__main"
                      aria-pressed={isSelected}
                      onClick={() => selectIssue(issue, key)}
                    >
                      <span className="report-issue-record__marker" aria-hidden="true" />
                      <span className="report-issue-record__body">
                        <strong>{issue.title || getIssueTypeLabel(issue.issueType)}</strong>
                        <small>{issue.description || '当前问题片段未提供额外说明。'}</small>
                      </span>
                      <span className="report-issue-record__time">
                        <strong>{formatTimestamp(issue.startMs)} – {formatTimestamp(issue.endMs)}</strong>
                        <small>持续 {formatDuration(issue.durationMs)}</small>
                      </span>
                      <span className={`report-severity report-severity--${issue.severity?.toLowerCase() || 'unknown'}`}>
                        {getSeverityLabel(issue.severity)}
                      </span>
                    </button>
                    <span className="report-issue-record__actions">
                      <Button type="text" icon={<AimOutlined />} onClick={() => selectIssue(issue, key)}>定位</Button>
                      <Button type="text" icon={<PlayCircleOutlined />} onClick={() => selectIssue(issue, key, true)}>试听</Button>
                    </span>
                  </article>
                )
              })}
              {pageRecords.length === 0 && <div className="report-inline-empty"><span>当前筛选条件下没有问题记录。</span></div>}
            </div>
            {filteredIssues.length > PAGE_SIZE && (
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={filteredIssues.length}
                showSizeChanger={false}
                showLessItems
                onChange={setPage}
              />
            )}
          </div>
        </>
      )}
    </section>
  )
}
