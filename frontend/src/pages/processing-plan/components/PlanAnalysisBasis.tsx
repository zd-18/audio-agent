import { BulbOutlined, RadarChartOutlined } from '@ant-design/icons'
import type { ProcessingPlan } from '../../../types/processingPlan'
import { hasSegmentRange } from '../../../utils/processingPlanDisplay'

interface PlanAnalysisBasisProps {
  plan: ProcessingPlan
}

export default function PlanAnalysisBasis({ plan }: PlanAnalysisBasisProps) {
  const issueCount = new Set(plan.steps
    .map((step) => step.sourceIssueId)
    .filter((issueId): issueId is string => Boolean(issueId))).size
  const segmentCount = plan.steps.filter(hasSegmentRange).length
  const highPriorityCount = plan.steps.filter((step) => step.priority === 'HIGH').length
  const metrics = [
    issueCount > 0 ? { label: '关联问题', value: issueCount } : null,
    segmentCount > 0 ? { label: '问题片段', value: segmentCount } : null,
    highPriorityCount > 0 ? { label: '优先处理', value: highPriorityCount } : null,
  ].filter((metric): metric is { label: string; value: number } => Boolean(metric))
  const sources = [
    issueCount > 0 ? '音频问题检测结果' : null,
    segmentCount > 0 ? '问题片段时间范围' : null,
    plan.steps.some((step) => Boolean(step.reason?.trim())) ? '步骤分析原因' : null,
    plan.steps.some((step) => typeof step.parameters.confidence === 'number' && step.parameters.confidence > 0)
      ? '模型置信度'
      : null,
  ].filter((source): source is string => Boolean(source))
  const detectionResult = Array.from(new Set(plan.steps
    .map((step) => step.reason?.trim())
    .filter((reason): reason is string => Boolean(reason))))
    .slice(0, 2)
    .join('；')
    || plan.summary?.trim()
    || '当前未返回可量化的问题指标，已根据现有分析结果生成建议。'

  return (
    <section className="processing-plan-analysis-basis processing-plan-reveal" aria-labelledby="processing-plan-analysis-basis-title">
      <div className="processing-plan-analysis-basis__heading">
        <span><RadarChartOutlined aria-hidden="true" /></span>
        <div>
          <h2 id="processing-plan-analysis-basis-title">AI 分析依据</h2>
          <p>根据当前检测结果生成处理建议。</p>
        </div>
      </div>
      {metrics.length > 0 ? (
        <dl className="processing-plan-analysis-basis__metrics">
          {metrics.map((metric) => (
            <div key={metric.label}><dt>{metric.label}</dt><dd>{metric.value}</dd></div>
          ))}
        </dl>
      ) : (
        <div className="processing-plan-analysis-basis__result">
          <strong>检测结果</strong>
          <p>{detectionResult}</p>
        </div>
      )}
      <div className="processing-plan-analysis-basis__sources">
        <strong><BulbOutlined aria-hidden="true" /> 建议来源</strong>
        <div>
          {(sources.length > 0 ? sources : ['当前处理方案的结构化分析结果'])
            .map((source) => <span key={source}>{source}</span>)}
        </div>
      </div>
    </section>
  )
}
