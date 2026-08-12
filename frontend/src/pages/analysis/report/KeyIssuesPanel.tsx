import { AimOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import type { KeyIssue } from '../../../types/analysisReport'
import { formatTimestamp } from '../../../utils/audioTime'
import { getIssueClassName, getIssueTypeLabel, getSeverityLabel } from '../reportMappings'

interface KeyIssuesPanelProps {
  issues: KeyIssue[]
  onLocate: (issue: KeyIssue) => void
  onPreview: (issue: KeyIssue) => void
}

export default function KeyIssuesPanel({ issues, onLocate, onPreview }: KeyIssuesPanelProps) {
  if (issues.length === 0) return null

  return (
    <section className="report-key-issues report-reveal-section" aria-labelledby="key-issues-title">
      <div className="report-section-heading">
        <div><span className="report-section-kicker">KEY ISSUES</span><h2 id="key-issues-title">重点问题</h2></div>
      </div>
      <div className="report-key-issues__list">
        {issues.map((issue, index) => (
          <article key={issue.issueId || `${issue.issueType}-${index}`} className={`report-key-issue report-issue--${getIssueClassName(issue.issueType)}`}>
            <span className="report-key-issue__index">{String(index + 1).padStart(2, '0')}</span>
            <div>
              <span>{getIssueTypeLabel(issue.issueType)} · {getSeverityLabel(issue.severity)}</span>
              <h3>{issue.title || getIssueTypeLabel(issue.issueType)}</h3>
              <p>{issue.description || '当前重点问题未提供额外说明。'}</p>
            </div>
            <div className="report-key-issue__action">
              <span>{formatTimestamp(issue.startMs)} – {formatTimestamp(issue.endMs)}</span>
              <span className="report-key-issue__buttons">
                <Button type="link" icon={<AimOutlined />} onClick={() => onLocate(issue)}>定位到此处</Button>
                <Button type="link" icon={<PlayCircleOutlined />} onClick={() => onPreview(issue)}>试听此片段</Button>
              </span>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
