import { CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import type { ProcessingPlan } from '../../../types/processingPlan'
import { formatDuration } from '../../../utils/audioTime'
import { getPlanStatusLabel } from '../../../utils/processingPlanDisplay'

interface PlanSummaryProps {
  plan: ProcessingPlan
}

export default function PlanSummary({ plan }: PlanSummaryProps) {
  const confirmationCount = plan.steps.filter((step) => step.requiresConfirmation).length
  const highPriorityCount = plan.steps.filter((step) => step.priority === 'HIGH').length

  return (
    <section className="processing-plan-summary processing-plan-reveal" aria-labelledby="processing-plan-summary-title">
      <div className="processing-plan-section-heading">
        <div>
          <span>PLAN OVERVIEW</span>
          <h2 id="processing-plan-summary-title">方案摘要</h2>
        </div>
        <span className={`processing-plan-status processing-plan-status--${plan.planStatus.toLowerCase()}`}>
          <CheckCircleOutlined aria-hidden="true" />
          {getPlanStatusLabel(plan.planStatus)}
        </span>
      </div>

      <p className="processing-plan-summary__copy">
        {plan.summary || '系统已根据当前分析结果整理处理建议，请逐项试听并确认。'}
      </p>

      <dl className="processing-plan-summary__metrics">
        <div>
          <dt>建议步骤</dt>
          <dd>{plan.stepCount}</dd>
          <small>后端方案记录</small>
        </div>
        <div>
          <dt><ExclamationCircleOutlined /> 优先步骤</dt>
          <dd>{highPriorityCount}</dd>
          <small>建议优先查看</small>
        </div>
        <div>
          <dt><CheckCircleOutlined /> 需要确认</dt>
          <dd>{confirmationCount}</dd>
          <small>建议试听后决定</small>
        </div>
        <div>
          <dt><ClockCircleOutlined /> 预计处理后时长</dt>
          <dd>{formatDuration(plan.estimatedOutputDurationMs)}</dd>
          <small>仅按建议缩短的静音片段估算</small>
        </div>
      </dl>
      <p className="processing-plan-summary__note">
        预计时长仅根据建议缩短的静音片段估算，实际结果以最终处理为准。
      </p>
    </section>
  )
}
