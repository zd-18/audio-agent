import {
  AimOutlined,
  ArrowRightOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  PlayCircleOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { Alert, Button } from 'antd'
import { Link } from 'react-router-dom'
import type {
  ProcessingStepConfirmation,
  UpdateProcessingStepConfirmationPayload,
} from '../../../types/processingConfirmation'
import type { ProcessingStep } from '../../../types/processingPlan'
import { formatDuration, formatTimestamp } from '../../../utils/audioTime'
import {
  getOperationLabel,
  getPriorityLabel,
  getRiskLabel,
  getStepDurationMs,
  hasSegmentRange,
  isWholeAudioOperation,
} from '../../../utils/processingPlanDisplay'
import StepParameters from './StepParameters'
import StepDecisionEditor from './StepDecisionEditor'

interface ProcessingStepDetailProps {
  taskId: string
  step: ProcessingStep
  confirmationStep?: ProcessingStepConfirmation
  confirmationReadOnly?: boolean
  confirmationSaving?: boolean
  listened?: boolean
  onLocate: (step: ProcessingStep) => void
  onPreview: (step: ProcessingStep) => void
  onDirtyChange?: (dirty: boolean) => void
  onSaveConfirmationStep?: (
    payload: UpdateProcessingStepConfirmationPayload,
  ) => Promise<ProcessingStepConfirmation | null>
}

export default function ProcessingStepDetail({
  taskId,
  step,
  confirmationStep,
  confirmationReadOnly = false,
  confirmationSaving = false,
  listened = false,
  onLocate,
  onPreview,
  onDirtyChange,
  onSaveConfirmationStep,
}: ProcessingStepDetailProps) {
  const segment = hasSegmentRange(step)
  const wholeAudio = isWholeAudioOperation(step.operationType)
  const durationMs = getStepDurationMs(step)

  return (
    <article className="processing-step-detail" aria-labelledby={`processing-step-${step.stepId}`}>
      <header className="processing-step-detail__header">
        <div>
          <span className="processing-step-detail__eyebrow">步骤 {String(step.stepOrder).padStart(2, '0')}</span>
          <h2 id={`processing-step-${step.stepId}`}>{getOperationLabel(step.operationType, step.title, step)}</h2>
          <p>{step.description || '当前步骤未提供额外说明。'}</p>
        </div>
        <div className="processing-step-detail__labels">
          <span className={`processing-priority processing-priority--${step.priority.toLowerCase()}`}>
            {getPriorityLabel(step.priority)}
          </span>
          <span className={`processing-risk processing-risk--${step.riskLevel.toLowerCase()}`}>
            <SafetyCertificateOutlined /> {getRiskLabel(step.riskLevel)}
          </span>
        </div>
      </header>

      {segment ? (
        <section className="processing-step-detail__time" aria-label="音频片段时间">
          <ClockCircleOutlined aria-hidden="true" />
          <div><span>开始位置</span><strong>{formatTimestamp(step.startMs)}</strong></div>
          <ArrowRightOutlined aria-hidden="true" />
          <div><span>结束位置</span><strong>{formatTimestamp(step.endMs)}</strong></div>
          <div><span>片段时长</span><strong>{formatDuration(durationMs)}</strong></div>
        </section>
      ) : wholeAudio ? (
        <Alert
          className="processing-step-detail__whole-audio"
          type="info"
          showIcon
          message="该建议作用于整段音频。"
        />
      ) : (
        <Alert
          className="processing-step-detail__whole-audio"
          type="warning"
          showIcon
          message="当前建议没有可定位的片段范围。"
          description="你仍可查看处理原因和建议参数。"
        />
      )}

      <div className="processing-step-detail__content-grid">
        <section>
          <span className="processing-step-detail__section-label">为什么需要处理</span>
          <p>{step.reason || '该建议来自当前音频分析结果，处理前请结合试听结果判断。'}</p>
        </section>
        <section>
          <span className="processing-step-detail__section-label">系统建议参数</span>
          <StepParameters step={step} />
        </section>
      </div>

      {step.requiresConfirmation && (
        <div className="processing-step-detail__confirmation">
          <CheckCircleOutlined aria-hidden="true" />
          <div><strong>需要试听确认</strong><span>请在执行任何修改前对比原始片段，确认建议符合预期。</span></div>
        </div>
      )}

      <footer className="processing-step-detail__actions">
        {segment && (
          <>
            <Button icon={<AimOutlined />} onClick={() => onLocate(step)}>定位到此处</Button>
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => onPreview(step)}>试听此片段</Button>
          </>
        )}
        {wholeAudio && (
          <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => onPreview(step)}>
            试听整段音频
          </Button>
        )}
        {step.sourceIssueId && (
          <Link to={`/analysis/tasks/${encodeURIComponent(taskId)}/report?issueId=${encodeURIComponent(step.sourceIssueId)}`}>
            <Button type="link" icon={<ArrowRightOutlined />}>查看原始问题</Button>
          </Link>
        )}
      </footer>

      {confirmationStep && onSaveConfirmationStep && onDirtyChange && (
        <StepDecisionEditor
          step={confirmationStep}
          readOnly={confirmationReadOnly}
          saving={confirmationSaving}
          listened={listened}
          onDirtyChange={onDirtyChange}
          onSave={onSaveConfirmationStep}
        />
      )}
    </article>
  )
}
