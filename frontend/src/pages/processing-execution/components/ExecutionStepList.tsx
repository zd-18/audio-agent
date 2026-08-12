import {
  CheckOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  LoadingOutlined,
  MinusOutlined,
} from '@ant-design/icons'
import type { ProcessingExecutionStep } from '../../../types/processingExecution'
import { formatTimestamp } from '../../../utils/audioTime'
import {
  getSkipReason,
  OPERATION_LABELS,
  STEP_STATUS_META,
} from '../../../utils/processingExecutionDisplay'

function StepIcon({ status }: { status: ProcessingExecutionStep['executionStatus'] }) {
  if (status === 'SUCCESS') return <CheckOutlined />
  if (status === 'FAILED') return <CloseOutlined />
  if (status === 'SKIPPED') return <MinusOutlined />
  if (status === 'PROCESSING') return <LoadingOutlined spin />
  return <ClockCircleOutlined />
}

function segmentLabel(step: ProcessingExecutionStep) {
  if (step.startMs === null && step.endMs === null) return '作用于整段音频'
  return `${formatTimestamp(step.startMs)} – ${formatTimestamp(step.endMs)}`
}

export default function ExecutionStepList({ steps }: { steps: ProcessingExecutionStep[] }) {
  const ordered = [...steps].sort((first, second) => first.stepOrder - second.stepOrder)
  return (
    <section className="processing-execution-steps" aria-labelledby="processing-execution-steps-title">
      <div className="processing-execution-section-heading">
        <div>
          <span>CONFIRMED STEPS</span>
          <h2 id="processing-execution-steps-title">执行步骤</h2>
          <p>这里显示已确认步骤的真实执行状态；检查类建议会明确标记为跳过。</p>
        </div>
        <strong>{ordered.length}</strong>
      </div>

      {ordered.length === 0 ? (
        <div className="processing-execution-steps__empty">当前任务没有可显示的执行步骤。</div>
      ) : (
        <ol className="processing-execution-step-list">
          {ordered.map((step) => {
            const meta = STEP_STATUS_META[step.executionStatus]
            return (
              <li
                key={step.executionStepId}
                className={`processing-execution-step is-${meta.tone}`}
                aria-current={step.executionStatus === 'PROCESSING' ? 'step' : undefined}
              >
                <div className="processing-execution-step__rail">
                  <span><StepIcon status={step.executionStatus} /></span>
                </div>
                <div className="processing-execution-step__body">
                  <div className="processing-execution-step__heading">
                    <div>
                      <span>步骤 {step.stepOrder}</span>
                      <h3>{OPERATION_LABELS[step.operationType]}</h3>
                    </div>
                    <span className={`processing-execution-step__status is-${meta.tone}`}>
                      {meta.label}
                    </span>
                  </div>
                  <p>{segmentLabel(step)}</p>
                  {step.executionStatus === 'SKIPPED' && (
                    <div className="processing-execution-step__note is-skipped">{getSkipReason(step.skipReason)}</div>
                  )}
                  {step.executionStatus === 'FAILED' && (
                    <div className="processing-execution-step__note is-failed">该步骤未能完成，请查看任务失败原因。</div>
                  )}
                </div>
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}
