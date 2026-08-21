import { CheckOutlined, SoundOutlined } from '@ant-design/icons'
import type { ProcessingStep } from '../../../types/processingPlan'
import {
  getProcessingPurpose,
  getProcessingTypeLabel,
  getPriorityLabel,
} from '../../../utils/processingPlanDisplay'

interface ProcessingStepListProps {
  steps: ProcessingStep[]
  selectedStepId?: string
  onSelect: (step: ProcessingStep) => void
}

export default function ProcessingStepList({
  steps,
  selectedStepId,
  onSelect,
}: ProcessingStepListProps) {
  return (
    <nav className="processing-step-list" aria-label="处理步骤导航">
      <div className="processing-step-list__heading">
        <span>建议步骤</span>
        <small>共 {steps.length} 项</small>
      </div>
      <div className="processing-step-list__items">
        {steps.map((step) => {
          const selected = step.stepId === selectedStepId
          const shortDescription = getProcessingPurpose(step)
          const stepName = getProcessingTypeLabel(step.operationType)
          return (
            <button
              key={step.stepId}
              type="button"
              className={`processing-step-list__item${selected ? ' is-selected' : ''}`}
              aria-current={selected ? 'step' : undefined}
              onClick={() => onSelect(step)}
            >
              <span className="processing-step-list__order">{String(step.stepOrder).padStart(2, '0')}</span>
              <span className="processing-step-list__body">
                <strong>{stepName}</strong>
                <small>{shortDescription}</small>
              </span>
              <span className={`processing-step-list__priority is-${step.priority.toLowerCase()}`}>
                {getPriorityLabel(step.priority)}
              </span>
              {step.requiresConfirmation
                ? <CheckOutlined className="processing-step-list__confirmation" aria-label="需要试听确认" />
                : <SoundOutlined className="processing-step-list__confirmation is-muted" aria-hidden="true" />}
            </button>
          )
        })}
      </div>
    </nav>
  )
}
