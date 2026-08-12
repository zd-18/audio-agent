import { CheckCircleOutlined, CheckOutlined, ClockCircleOutlined, CloseCircleOutlined, SoundOutlined } from '@ant-design/icons'
import type { ProcessingStepConfirmation } from '../../../types/processingConfirmation'
import type { ProcessingStep } from '../../../types/processingPlan'
import { formatTimestamp } from '../../../utils/audioTime'
import {
  getOperationLabel,
  getPriorityLabel,
  hasSegmentRange,
  isWholeAudioOperation,
} from '../../../utils/processingPlanDisplay'

interface ProcessingStepListProps {
  steps: ProcessingStep[]
  confirmationSteps?: ProcessingStepConfirmation[]
  selectedStepId?: string
  onSelect: (step: ProcessingStep) => void
}

export default function ProcessingStepList({
  steps,
  confirmationSteps = [],
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
          const confirmationStep = confirmationSteps.find((item) => item.sourceStepId === step.stepId)
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
                <strong>{getOperationLabel(step.operationType, step.title)}</strong>
                <small>
                  {hasSegmentRange(step)
                    ? `${formatTimestamp(step.startMs)} – ${formatTimestamp(step.endMs)}`
                    : isWholeAudioOperation(step.operationType)
                      ? '作用于整段音频'
                      : '未提供片段范围'}
                </small>
              </span>
              <span className={`processing-step-list__priority is-${step.priority.toLowerCase()}`}>
                {getPriorityLabel(step.priority)}
              </span>
              {confirmationStep ? (
                <span className={`processing-step-list__decision is-${confirmationStep.decision.toLowerCase()}`}>
                  {confirmationStep.decision === 'ACCEPTED'
                    ? <CheckCircleOutlined aria-hidden="true" />
                    : confirmationStep.decision === 'REJECTED'
                      ? <CloseCircleOutlined aria-hidden="true" />
                      : <ClockCircleOutlined aria-hidden="true" />}
                  {confirmationStep.decision === 'ACCEPTED'
                    ? '已接受'
                    : confirmationStep.decision === 'REJECTED' ? '暂不处理' : '待决定'}
                </span>
              ) : step.requiresConfirmation
                ? <CheckOutlined className="processing-step-list__confirmation" aria-label="需要试听确认" />
                : <SoundOutlined className="processing-step-list__confirmation is-muted" aria-hidden="true" />}
            </button>
          )
        })}
      </div>
    </nav>
  )
}
