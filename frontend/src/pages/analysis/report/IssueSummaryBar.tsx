import type { IssueSummary } from '../../../types/analysisReport'
import { formatDuration } from '../../../utils/audioTime'

export default function IssueSummaryBar({ summary }: { summary: IssueSummary }) {
  const items = [
    { key: 'total', label: '问题总数', value: summary.totalIssueCount ?? '—', accent: true },
    { key: 'silence', label: '长静音', value: summary.silenceCount ?? '—' },
    { key: 'drop', label: '音量偏低', value: summary.volumeDropCount ?? '—' },
    { key: 'spike', label: '音量突升', value: summary.volumeSpikeCount ?? '—' },
    { key: 'noise', label: '疑似背景噪声', value: summary.noiseRiskCount ?? '—' },
    { key: 'duration', label: '问题总时长', value: formatDuration(summary.totalIssueDurationMs), duration: true },
  ]

  return (
    <section className="report-issue-summary" aria-labelledby="issue-summary-title">
      <div className="report-section-heading report-section-heading--inline">
        <div><span className="report-section-kicker">ISSUE SUMMARY</span><h2 id="issue-summary-title">问题统计</h2></div>
      </div>
      <dl>
        {items.map((item) => (
          <div key={item.key} className={`${item.accent ? 'is-accent ' : ''}${item.duration ? 'is-duration' : ''}`}>
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
