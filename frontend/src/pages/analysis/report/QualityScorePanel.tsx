import { Progress } from 'antd'
import type { AudioAnalysisReport, QualityGrade } from '../../../types/analysisReport'
import { formatDuration } from '../../../utils/audioTime'

const GRADE_COLORS: Record<QualityGrade, string> = {
  EXCELLENT: '#42dcc7',
  GOOD: '#5b8cff',
  FAIR: '#f3b969',
  POOR: '#ff6b81',
}

export default function QualityScorePanel({ report }: { report: AudioAnalysisReport }) {
  const issueCount = report.issueSummary.totalIssueCount

  return (
    <section className={`report-quality report-quality--${report.qualityGrade.toLowerCase()}`} aria-labelledby="report-quality-title">
      <div className="report-quality__score" aria-label={`音频质量评分 ${report.qualityScore} 分`}>
        <Progress
          type="dashboard"
          percent={report.qualityScore}
          size={196}
          gapDegree={64}
          strokeWidth={8}
          strokeColor={GRADE_COLORS[report.qualityGrade]}
          trailColor="rgba(255,255,255,.065)"
          format={() => (
            <span className="report-quality__score-value">
              <strong>{report.qualityScore}</strong>
              <small>/ 100</small>
            </span>
          )}
        />
      </div>

      <div className="report-quality__content">
        <span className="report-section-kicker">QUALITY OVERVIEW</span>
        <div className="report-quality__grade-row">
          <h2 id="report-quality-title">{report.qualityGradeText}</h2>
          <span className="report-quality__grade">{report.qualityGrade}</span>
        </div>
        <p>{report.summary?.trim() || '当前报告未提供整体结论。'}</p>
        <div className="report-quality__facts" aria-label="报告摘要数据">
          <span><strong>{issueCount ?? '—'}</strong> 个问题片段</span>
          <span><strong>{formatDuration(report.audioOverview.durationMs)}</strong> 音频总时长</span>
        </div>
      </div>
    </section>
  )
}
