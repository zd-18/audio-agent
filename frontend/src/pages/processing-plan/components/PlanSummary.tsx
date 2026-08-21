import { BulbOutlined, CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import type { ProcessingPlan } from '../../../types/processingPlan'
import { getPlanStatusLabel } from '../../../utils/processingPlanDisplay'

interface PlanSummaryProps {
  plan: ProcessingPlan
}

export default function PlanSummary({ plan }: PlanSummaryProps) {
  const issueCount = new Set(plan.steps
    .map((step) => step.sourceIssueId)
    .filter((issueId): issueId is string => Boolean(issueId))).size
  const issueReasons = Array.from(new Set(plan.steps
    .map((step) => step.reason?.trim())
    .filter((reason): reason is string => Boolean(reason))))
    .slice(0, 2)

  return (
    <section className="processing-plan-summary processing-plan-reveal" aria-labelledby="processing-plan-summary-title">
      <div className="processing-plan-section-heading">
        <div>
          <h2 id="processing-plan-summary-title">方案摘要</h2>
        </div>
        <span className={`processing-plan-status processing-plan-status--${plan.planStatus.toLowerCase()}`}>
          <CheckCircleOutlined aria-hidden="true" />
          {getPlanStatusLabel(plan.planStatus)}
        </span>
      </div>

      <div className="processing-plan-summary__focus">
        <section>
          <span><ExclamationCircleOutlined /> 检测问题</span>
          <p>{issueReasons.length > 0
            ? issueReasons.join('；')
            : issueCount > 0
              ? `检测到 ${issueCount} 个需要关注的音频问题。`
              : '当前方案未关联明确的音频问题。'}</p>
        </section>
        <section>
          <span><BulbOutlined /> 处理建议</span>
          <p>{plan.summary || `已生成 ${plan.stepCount} 个建议步骤，请按顺序查看并确认。`}</p>
        </section>
      </div>
    </section>
  )
}
