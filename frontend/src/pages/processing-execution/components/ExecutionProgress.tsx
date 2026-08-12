import { LoadingOutlined } from '@ant-design/icons'
import type { ProcessingExecution } from '../../../types/processingExecution'
import {
  EXECUTION_STATUS_META,
  getExecutionProgress,
  getStageLabel,
  OPERATION_LABELS,
} from '../../../utils/processingExecutionDisplay'

const STAGE_GROUPS = [
  { label: '准备', stages: ['PREPARING'] },
  { label: '处理', stages: ['LOCAL_PROCESSING', 'TRIMMING', 'LOUDNESS_NORMALIZING', 'PEAK_LIMITING'] },
  { label: '保存', stages: ['UPLOADING', 'METADATA_EXTRACTING'] },
  { label: '完成', stages: ['COMPLETED'] },
] as const

export default function ExecutionProgress({ execution }: { execution: ProcessingExecution }) {
  const progress = getExecutionProgress(execution.executionStatus, execution.progressPercent)
  const activeStep = execution.steps.find((step) => step.executionStatus === 'PROCESSING')
  const status = EXECUTION_STATUS_META[execution.executionStatus]
  const activeStageIndex = STAGE_GROUPS.findIndex((group) => (
    execution.currentStage ? group.stages.some((stage) => stage === execution.currentStage) : false
  ))

  return (
    <section className="processing-execution-progress" aria-labelledby="processing-execution-progress-title">
      <div className="processing-execution-section-heading">
        <div>
          <span>LIVE PROGRESS</span>
          <h2 id="processing-execution-progress-title">整体处理进度</h2>
          <p>{getStageLabel(execution.currentStage)}</p>
        </div>
        <strong>{progress}%</strong>
      </div>

      <div
        className={`processing-execution-progress__track is-${status.tone}`}
        role="progressbar"
        aria-label="音频处理整体进度"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={progress}
      >
        <span style={{ width: `${progress}%` }} />
      </div>

      <ol className="processing-execution-stage-track" aria-label="处理阶段">
        {STAGE_GROUPS.map((group, index) => {
          const completed = execution.executionStatus === 'SUCCESS' || (activeStageIndex >= 0 && index < activeStageIndex)
          const active = execution.executionStatus !== 'SUCCESS' && index === activeStageIndex
          return (
            <li key={group.label} className={completed ? 'is-complete' : active ? 'is-active' : ''}>
              <i aria-hidden="true" />
              <span>{group.label}</span>
            </li>
          )
        })}
      </ol>

      <div className="processing-execution-progress__details">
        <div>
          <span>当前状态</span>
          <strong>{status.label}</strong>
        </div>
        <div>
          <span>当前阶段</span>
          <strong>{getStageLabel(execution.currentStage)}</strong>
        </div>
        <div>
          <span>当前步骤</span>
          <strong>
            {activeStep ? (
              <><LoadingOutlined spin aria-hidden="true" /> {OPERATION_LABELS[activeStep.operationType]}</>
            ) : execution.executionStatus === 'SUCCESS' ? '全部步骤已完成' : '等待进入下一步骤'}
          </strong>
        </div>
      </div>
    </section>
  )
}
