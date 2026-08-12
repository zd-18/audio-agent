import type { ProcessingStep } from '../../../types/processingPlan'
import { getParameterDisplayItems } from '../../../utils/processingPlanDisplay'

export default function StepParameters({ step }: { step: ProcessingStep }) {
  const items = getParameterDisplayItems(step)

  return (
    <dl className="processing-step-parameters">
      {items.map((item) => (
        <div key={`${item.label}-${item.value}`}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}
