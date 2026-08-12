import { AimOutlined, CheckCircleOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import type { Recommendation } from '../../../types/analysisReport'
import { formatTimestamp } from '../../../utils/audioTime'
import { PRIORITY_META } from '../reportMappings'

interface RecommendationListProps {
  recommendations: Recommendation[]
  onLocate: (recommendation: Recommendation) => void
  onPreview: (recommendation: Recommendation) => void
}

export default function RecommendationList({ recommendations, onLocate, onPreview }: RecommendationListProps) {
  return (
    <section className="report-recommendations report-reveal-section" aria-labelledby="recommendations-title">
      <div className="report-section-heading">
        <div>
          <span className="report-section-kicker">NEXT STEPS</span>
          <h2 id="recommendations-title">处理建议</h2>
          <p>按照报告给出的优先级，先处理对整体听感影响最大的片段。</p>
        </div>
      </div>
      {recommendations.length === 0 ? (
        <div className="report-recommendations__empty"><CheckCircleOutlined /><span>当前未生成额外处理建议。</span></div>
      ) : (
        <ol>
          {recommendations.map((recommendation, index) => {
            const priority = recommendation.priority || 'LOW'
            const hasRange = typeof recommendation.startMs === 'number' && typeof recommendation.endMs === 'number'
            return (
              <li key={`${recommendation.issueId || 'general'}-${index}`} className={`report-recommendation report-recommendation--${priority.toLowerCase()}`}>
                <span className="report-recommendation__number">{index + 1}</span>
                <div>
                  <span className="report-priority">{PRIORITY_META[priority].label}</span>
                  <p>{recommendation.message || '当前建议未提供详细说明。'}</p>
                  {hasRange && <small>对应片段：{formatTimestamp(recommendation.startMs)} – {formatTimestamp(recommendation.endMs)}</small>}
                </div>
                {hasRange && (
                  <span className="report-recommendation__actions">
                    <Button type="link" icon={<AimOutlined />} onClick={() => onLocate(recommendation)}>
                      定位到此处
                    </Button>
                    <Button type="link" icon={<PlayCircleOutlined />} onClick={() => onPreview(recommendation)}>
                      试听此片段
                    </Button>
                  </span>
                )}
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}
